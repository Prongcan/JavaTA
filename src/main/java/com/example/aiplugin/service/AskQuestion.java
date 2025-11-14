package com.example.aiplugin.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AskQuestion {

    private static final Gson GSON = new Gson();

    /**
     * 扩展接口：
     * question -> String
     * chat_history -> JSON 字符串（[{role: "user"|"assistant"|"system", content: "..."}, ...]）
     * code_content -> String
     * pdf_path -> PDF 根目录（如果不为空则进行解析、分块、向量化并存储，跳过解析过的）
     */
    public String askQuestion(String question, String chat_history, String code_content, String pdf_path) {
        // 1) 建立 Retrieval 对象（参考 Retrieval.main 的用法）
        Config cfg = Config.load();
        String openAiKey = cfg.getOpenAIKey();
        Retrieval retrieval = new Retrieval(openAiKey);

        // 2) 如果用户删除了对应 PDF，则先清理对应 embedding；且当 pdf_path 为 null 时，不进行检索
        boolean doRetrieval = (pdf_path != null);
        java.util.List<Retrieval.Match> matches = new java.util.ArrayList<>();
        if (doRetrieval) {
            String pdfRoot = pdf_path.trim();
            // 先清理已删除源对应的向量
            try {
                retrieval.pruneDeletedSources(pdfRoot);
            } catch (Exception e) {
                System.err.println("Prune error: " + e.getMessage());
            }
            // 再索引现有 PDF
            if (!pdfRoot.isEmpty()) {
                try {
                    retrieval.indexPdfDirectory(pdfRoot);
                } catch (Exception e) {
                    // 不要中断主流程，记录错误即可
                    System.err.println("Index PDF error: " + e.getMessage());
                }
            }
            // 3) 对 question 进行 embedding（不保存），Top-K 语义检索
            int topK = cfg.getTopK();
            double threshold = cfg.getThreshold();
            try {
                matches = retrieval.searchTopK(question, topK, threshold);
            } catch (Exception e) {
                System.err.println("Search error: " + e.getMessage());
            }
        }
        boolean cited = matches != null && !matches.isEmpty();
        StringBuilder retrievedConcat = new StringBuilder();
        if (cited) {
            for (int i = 0; i < Math.min(matches.size(), 5); i++) {
                Retrieval.Match m = matches.get(i);
                retrievedConcat.append("[Source: ")
                        .append(m.item.sourcePath == null ? "" : m.item.sourcePath)
                        .append(" pages ")
                        .append(m.item.pageStart)
                        .append(m.item.pageEnd != m.item.pageStart ? ("-" + m.item.pageEnd) : "")
                        .append("]\n");
                retrievedConcat.append(m.item.text).append("\n\n");
            }
        }

        // 4) 解析 chat_history JSON，动态截断到不超过模型上下文（这里用字符近似限制）
        List<JsonObject> trimmedHistory = trimHistory(chat_history, 12000);

        // 5) 组装 Prompt：把检索结果、代码、历史对话拼到 messages 中
        JSONArray messages = new JSONArray();
        // system with context
        StringBuilder systemCtx = new StringBuilder();
        systemCtx.append("You are a helpful assistant.\n");
        if (retrievedConcat.length() > 0) {
            systemCtx.append("Retrieved context (from PDFs):\n").append(retrievedConcat).append("\n");
            systemCtx.append("When answering, prefer the retrieved context and cite facts succinctly in your reasoning, but do not fabricate sources.\n\n");
        }
        if (code_content != null && !code_content.isEmpty()) {
            systemCtx.append("Relevant code:\n").append(code_content);
        }
        messages.put(new JSONObject().put("role", "system").put("content", systemCtx.toString()));

        // append trimmed history as-is (keep roles user/assistant/system)
        for (JsonObject m : trimmedHistory) {
            String role = m.has("role") ? m.get("role").getAsString() : "user";
            String content = m.has("content") ? m.get("content").getAsString() : "";
            messages.put(new JSONObject().put("role", role).put("content", content));
        }

        // final user turn
        messages.put(new JSONObject().put("role", "user").put("content", question));

        // 6) 调用 DeepSeek Chat 接口
        String deepseekKey = cfg.getDeepseekKey();
        String answer;
        try {
            answer = chat(messages, deepseekKey, cfg); 
        } catch (IOException e) {
            throw new RuntimeException("Error communicating with DeepSeek API.", e);
        }

        // 7) 返回包含检索结果、是否引用、引用来源列表
        JsonObject resp = new JsonObject();
        resp.addProperty("cited", cited);
        resp.addProperty("retrieval_result", retrievedConcat.toString());
        // sources 数组
        JsonArray sources = new JsonArray();
        if (matches != null) {
            for (Retrieval.Match m : matches) {
                JsonObject s = new JsonObject();
                s.addProperty("path", m.item.sourcePath == null ? "" : m.item.sourcePath);
                s.addProperty("page_start", m.item.pageStart);
                s.addProperty("page_end", m.item.pageEnd);
                s.addProperty("score", m.score);
                sources.add(s);
            }
        }
        resp.add("sources", sources);
        resp.addProperty("answer", answer);
        return resp.toString();
    }

    private static String getenvOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static List<JsonObject> trimHistory(String chatHistoryJson, int maxChars) {
        List<JsonObject> items = new ArrayList<>();
        if (chatHistoryJson == null || chatHistoryJson.isEmpty()) return items;
        try {
            JsonElement root = GSON.fromJson(chatHistoryJson, JsonElement.class);
            if (root != null && root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (e.isJsonObject()) items.add(e.getAsJsonObject());
                }
            }
        } catch (Exception ignore) {}
        // 从尾部向前累加，直到达到 maxChars
        List<JsonObject> reversed = new ArrayList<>();
        int acc = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            JsonObject m = items.get(i);
            String content = m.has("content") ? m.get("content").getAsString() : "";
            int add = content.length() + 20; // 角色等开销
            if (acc + add > maxChars) break;
            reversed.add(0, m);
            acc += add;
        }
        return reversed;
    }

    private String chat(JSONArray messages, String api_key, Config cfg) throws IOException {
        String baseUrl = cfg.getChatBaseUrl();
        String model = cfg.getChatModel();
        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + api_key);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        java.util.Scanner scanner;
        if (code == HttpURLConnection.HTTP_OK) {
            scanner = new java.util.Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
        } else {
            scanner = new java.util.Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
        }
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
        }
        scanner.close();

        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Chat API returned non-OK code: " + code + ", body: " + sb);
        }

        JSONObject respJson = new JSONObject(sb.toString());
        return respJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    public static void main(String[] args) {
        AskQuestion aq = new AskQuestion();
        String chatHistory = "[\n  {\"role\":\"user\",\"content\":\"Hi\"},\n  {\"role\":\"assistant\",\"content\":\"Hello! How can I help?\"}\n]";
        String code = "public class Demo { void run(){} }";
        String pdfRoot = "C:\\Users\\lenovo\\Desktop\\JavaTA\\pdfs"; // 请将此路径改为你的 PDF 根目录
        String question = "What is java";
        String resp = aq.askQuestion(question, chatHistory, code, pdfRoot);
        System.out.println(resp);
    }
}

