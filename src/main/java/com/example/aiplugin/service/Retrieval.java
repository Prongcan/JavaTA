package com.example.aiplugin.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Retrieval { 

    private final Config config = Config.load();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 保存到文件的数据结构
     */
    static class EmbeddingItem {
        String text;
        List<Float> vector;
        String sourcePath; // 来源文件绝对路径
        int pageStart;     // 起始页（1-based）
        int pageEnd;       // 结束页（1-based）

        EmbeddingItem() {}

        EmbeddingItem(String text, List<Float> vector) {
            this.text = text;
            this.vector = vector;
        }

        EmbeddingItem(String text, List<Float> vector, String sourcePath, int pageStart, int pageEnd) {
            this.text = text;
            this.vector = vector;
            this.sourcePath = sourcePath;
            this.pageStart = pageStart;
            this.pageEnd = pageEnd;
        }
    }

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private List<EmbeddingItem> vectorStore = new ArrayList<>();

    // Track processed PDFs: path -> lastModified
    private java.util.Map<String, Long> processedPdfs = new java.util.HashMap<>();

    /**
     * 构造方法，自动加载本地 embeddings
     */
    public Retrieval(String apiKey) {
        this.apiKey = apiKey;

        // 设置本地 127.0.0.1:7897 代理
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (config.isProxyEnabled()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort())));
        }
        this.httpClient = builder.build();

        loadLocalEmbeddings();
        loadProcessedPdfs();
    }

    /**
     * 从本地 JSON 文件加载 embeddings
     */
    private void loadLocalEmbeddings() {
        try {
            File file = new File(config.getVectorFile());
            if (!file.exists()) {
                return;
            }
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            EmbeddingItem[] items = gson.fromJson(json, EmbeddingItem[].class);
            if (items != null) {
                for (EmbeddingItem it : items) {
                    vectorStore.add(it);
                }
            }

            System.out.println("已加载本地向量: " + vectorStore.size() + " 条");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将向量保存到本地 JSON 文件
     */
    private void saveLocalEmbeddings() {
        try (FileWriter writer = new FileWriter(config.getVectorFile(), StandardCharsets.UTF_8)) {
            gson.toJson(vectorStore, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- 处理已解析 PDF 的状态 ----
    private void loadProcessedPdfs() {
        try {
            File file = new File(config.getProcessedPdfsFile());
            if (!file.exists()) return;
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            java.lang.reflect.Type type = new TypeToken<Map<String, Long>>(){}.getType();
            Map<String, Long> map = gson.fromJson(json, type);
            if (map != null) processedPdfs.putAll(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveProcessedPdfs() {
        try (FileWriter writer = new FileWriter(config.getProcessedPdfsFile(), StandardCharsets.UTF_8)) {
            gson.toJson(processedPdfs, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- PDF 索引：遍历、逐页解析、分块（带出处）、embedding 并保存 ----
    public void indexPdfDirectory(String rootDir) throws IOException {
        System.out.println("[Index] Entering indexPdfDirectory with rootDir: " + rootDir);
        File root = new File(rootDir);
        if (!root.exists() || !root.isDirectory()) {
            System.err.println("[Index] Root directory does not exist or is not a directory: " + rootDir);
            return;
        }

        List<File> pdfs = collectPdfs(root);
        System.out.println("[Index] Found " + pdfs.size() + " PDF files to process.");

        for (File pdf : pdfs) {
            long lm = pdf.lastModified();
            String key = pdf.getAbsolutePath();
            Long prev = processedPdfs.get(key);

            System.out.println("[Index] Processing file: " + key);
            if (prev != null && prev == lm) {
                System.out.println("[Index] SKIPPING: File has not been modified since last processing.");
                continue;
            }
            
            System.out.println("[Index] STARTING: Indexing new or modified file.");
            int savedCount = 0; // 本文件成功写入的分块数
            // 逐页解析并分块
            try (PDDocument doc = PDDocument.load(pdf)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int pageCount = doc.getNumberOfPages();
                for (int page = 1; page <= pageCount; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(doc);
                    if (pageText == null || pageText.trim().isEmpty()) continue;
                    for (String chunk : chunkText(pageText, 400, 200)) {
                        try {
                            List<Float> vec = getEmbedding(chunk);
                            saveEmbedding(chunk, vec, pdf.getAbsolutePath(), page, page);
                            savedCount++;
                        } catch (Exception e) {
                            System.err.println("Embedding chunk error: " + e.getMessage());
                        }
                    }
                }
            }
            // 只有当至少有一个分块成功保存时才标记为已处理
            if (savedCount > 0) {
                processedPdfs.put(key, lm);
                saveProcessedPdfs();
                System.out.println("[Index] FINISHED: Successfully indexed " + savedCount + " chunks for file: " + key);
            } else {
                System.err.println("[Index] FAILED: No chunks were saved for file: " + key + ". Will not mark as processed.");
            }
        }
    }

    public void pruneDeletedSources(String rootDir) {
        try {
            File root = new File(rootDir);
            String rootPath = root.getAbsolutePath();

            // 1) Prune processedPdfs entries
            java.util.Iterator<Map.Entry<String, Long>> it = processedPdfs.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                String path = e.getKey();
                if (path.startsWith(rootPath)) {
                    if (!new File(path).exists()) {
                        it.remove();
                    }
                }
            }
            saveProcessedPdfs();

            // 2) Prune vectorStore embeddings whose source under root no longer exists
            List<EmbeddingItem> kept = new ArrayList<>();
            int removed = 0;
            for (EmbeddingItem item : vectorStore) {
                if (item.sourcePath != null && item.sourcePath.startsWith(rootPath)) {
                    if (!new File(item.sourcePath).exists()) {
                        removed++;
                        continue;
                    }
                }
                kept.add(item);
            }
            if (removed > 0) {
                vectorStore = kept;
                saveLocalEmbeddings();
                System.out.println("[Prune] Removed " + removed + " stale embedding chunks under " + rootPath);
            }
        } catch (Exception e) {
            System.err.println("[Prune] Error: " + e.getMessage());
        }
    }

    private List<File> collectPdfs(File dir) {
        List<File> out = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null) return out;
        for (File f : list) {
            if (f.isDirectory()) out.addAll(collectPdfs(f));
            else if (f.getName().toLowerCase().endsWith(".pdf")) out.add(f);
        }
        return out;
    }

    private String extractTextFromPdf(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null) return chunks;
        int n = text.length();
        int start = 0;
        while (start < n) {
            int end = Math.min(n, start + chunkSize);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= n) break;
            start = end - overlap;
            if (start < 0) start = 0;
        }
        return chunks;
    }

    /**
     * 调用 OpenAI Embedding API
     */
    public List<Float> getEmbedding(String text) {
        try {
            // Log request
            String preview = text == null ? "" : text.replaceAll("\n", " ");
            if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
            System.out.println("[Embedding] Request: len=" + (text == null ? 0 : text.length()) + ", preview=\"" + preview + "\"");

            // 构造 JSON 请求
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", config.getEmbeddingModel());

            JsonArray inputArray = new JsonArray();
            inputArray.add(text);
            bodyJson.add("input", inputArray);

            RequestBody body = RequestBody.create(JSON, bodyJson.toString());

            Request request = new Request.Builder()
                    .url(config.getEmbeddingApiUrl())
                    .post(body)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new RuntimeException("请求失败: " + response.code() + " / " + response.body().string());
            }

            String respBody = response.body().string();
            JsonObject respJson = gson.fromJson(respBody, JsonObject.class);

            JsonArray dataArray = respJson.getAsJsonArray("data");
            JsonObject embeddingObj = dataArray.get(0).getAsJsonObject();
            JsonArray vectorJson = embeddingObj.getAsJsonArray("embedding");

            List<Float> vector = new ArrayList<>();
            for (int i = 0; i < vectorJson.size(); i++) {
                vector.add(vectorJson.get(i).getAsFloat());
            }

            // Log response
            String head = "";
            for (int i = 0; i < Math.min(8, vector.size()); i++) {
                head += (i == 0 ? "" : ", ") + String.format("%.5f", vector.get(i));
            }
            System.out.println("[Embedding] Response: dims=" + vector.size() + ", head=[" + head + "]");

            return vector;

        } catch (Exception e) {
            System.err.println("[Embedding] Error: " + e.getMessage());
            throw new RuntimeException("调用 embedding 失败", e);
        }
    }

    /**
     * 保存 embedding 到内存 + 本地
     */
    public void saveEmbedding(String text, List<Float> embedding) {
        vectorStore.add(new EmbeddingItem(text, embedding));
        saveLocalEmbeddings();
    }

    public void saveEmbedding(String text, List<Float> embedding, String sourcePath, int pageStart, int pageEnd) {
        vectorStore.add(new EmbeddingItem(text, embedding, sourcePath, pageStart, pageEnd));
        saveLocalEmbeddings();
    }

    /**
     * 余弦相似度
     */
    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        int len = Math.min(v1.size(), v2.size());

        for (int i = 0; i < len; i++) {
            dot += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 查找最相似的文本（旧接口）
     */
    public String searchMostSimilar(String queryText) {
        return searchMostSimilar(queryText, -1.0);
    }

    public String searchMostSimilar(String queryText, double threshold) {
        List<Match> top1 = searchTopK(queryText, 1, threshold);
        if (top1.isEmpty()) return "";
        return top1.get(0).item.text;
    }

    /**
     * 带元数据的匹配结果
     */
    public static class Match {
        public EmbeddingItem item;
        public double score;
        public Match(EmbeddingItem item, double score) { this.item = item; this.score = score; }
    }

    /**
     * Top-K 语义检索，返回包含来源页码等信息
     */
    public List<Match> searchTopK(String queryText, int k, double threshold) {
        List<Float> queryEmbedding = getEmbedding(queryText);
        List<Match> matches = new ArrayList<>();
        for (EmbeddingItem item : vectorStore) {
            double score = cosineSimilarity(queryEmbedding, item.vector);
            if (threshold > 0 && score < threshold) continue;
            matches.add(new Match(item, score));
        }
        matches.sort((a, b) -> Double.compare(b.score, a.score));
        if (k > 0 && matches.size() > k) {
            return new ArrayList<>(matches.subList(0, k));
        }
        return matches;
    }

    /**
     * 主函数测试
     */
    public static void main(String[] args) {

        Config cfg = Config.load();
        Retrieval retrieval = new Retrieval(cfg.getOpenAIKey());

        retrieval.saveEmbedding("Java 是一种面向对象的编程语言。",
                retrieval.getEmbedding("Java 是一种面向对象的编程语言。"));

        retrieval.saveEmbedding("猫是一种常见的宠物，喜欢睡觉。",
                retrieval.getEmbedding("猫是一种常见的宠物，喜欢睡觉。"));

        retrieval.saveEmbedding("苹果是一种水果，富含维生素。",
                retrieval.getEmbedding("苹果是一种水果，富含维生素。"));

        String query = "Java 编程";
        String result = retrieval.searchMostSimilar(query);

        System.out.println("查询: " + query);
        System.out.println("最相似文本: " + result);
    }
}
