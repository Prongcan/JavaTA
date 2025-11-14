package com.example.aiplugin.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class Retrieval {

    private static final String VECTOR_FILE = "embeddings.json";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 保存到文件的数据结构
     */
    static class EmbeddingItem {
        String text;
        List<Float> vector;

        EmbeddingItem() {}

        EmbeddingItem(String text, List<Float> vector) {
            this.text = text;
            this.vector = vector;
        }
    }

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private List<EmbeddingItem> vectorStore = new ArrayList<>();

    /**
     * 构造方法，自动加载本地 embeddings
     */
    public Retrieval(String apiKey) {
        this.apiKey = apiKey;

        // 设置本地 127.0.0.1:7897 代理
        this.httpClient = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897)))
                .build();

        loadLocalEmbeddings();
    }

    /**
     * 从本地 JSON 文件加载 embeddings
     */
    private void loadLocalEmbeddings() {
        try {
            File file = new File(VECTOR_FILE);
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
        try (FileWriter writer = new FileWriter(VECTOR_FILE, StandardCharsets.UTF_8)) {
            gson.toJson(vectorStore, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 调用 OpenAI Embedding API
     */
    public List<Float> getEmbedding(String text) {
        try {
            // 构造 JSON 请求
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", "text-embedding-3-small");

            JsonArray inputArray = new JsonArray();
            inputArray.add(text);
            bodyJson.add("input", inputArray);

            RequestBody body = RequestBody.create(JSON, bodyJson.toString());

            Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
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

            return vector;

        } catch (Exception e) {
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
     * 查找最相似的文本
     */
    public String searchMostSimilar(String queryText) {
        List<Float> queryEmbedding = getEmbedding(queryText);

        EmbeddingItem best = null;
        double bestScore = -1;

        for (EmbeddingItem item : vectorStore) {
            double score = cosineSimilarity(queryEmbedding, item.vector);

            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return best != null ? best.text : null;
    }

    /**
     * 主函数测试
     */
    public static void main(String[] args) {

        Retrieval retrieval = new Retrieval("your api key");

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
