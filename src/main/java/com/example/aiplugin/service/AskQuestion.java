package com.example.aiplugin.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.json.JSONArray;
import org.json.JSONObject;
import com.example.aiplugin.service.DocumentParserService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AskQuestion {

    private static final Gson GSON = new Gson();
    private static final String PDF_FOLDER_PROPERTY_KEY = "ai_plugin_pdf_folder_path";

    public String askQuestion(Project project, String question, String chat_history, String code_content) {
        // 0. Get PDF path from saved properties
        String pdfPath = PropertiesComponent.getInstance(project).getValue(PDF_FOLDER_PROPERTY_KEY, "");

        // 1) 建立 Retrieval 对象
        Config cfg = Config.load();
        String openAiKey = cfg.getOpenAIKey();
        Retrieval retrieval = new Retrieval(openAiKey);

        // 2) 如果路径已配置，则进行检索
        boolean doRetrieval = pdfPath != null && !pdfPath.trim().isEmpty();
        java.util.List<Retrieval.Match> matches = new java.util.ArrayList<>();
        if (doRetrieval) {
            String pdfRoot = pdfPath.trim();
            // 先清理已删除源对应的向量
            try {
                retrieval.pruneDeletedSources(pdfRoot);
            } catch (Exception e) {
                System.err.println("Prune error: " + e.getMessage());
            }
            // 再索引现有文档（PDF 和 PPT/PPTX）
            if (!pdfRoot.isEmpty()) {
                try {
                    DocumentParserService parserService = DocumentParserService.getInstance(project);
                    retrieval.indexDocumentDirectory(pdfRoot, parserService);
                } catch (Exception e) {
                    // 不要中断主流程，记录错误即可
                    System.err.println("Index documents error: " + e.getMessage());
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

        // 4) 解析 chat_history JSON
        List<JsonObject> trimmedHistory = trimHistory(chat_history, 12000);

        // 5) 组装 Prompt
        JSONArray messages = new JSONArray();
        StringBuilder systemCtx = new StringBuilder();
        systemCtx.append("You are a helpful assistant.\n");
        if (retrievedConcat.length() > 0) {
            systemCtx.append("Retrieved context (from PDFs):\n").append(retrievedConcat).append("\n");
            systemCtx.append("When answering, prefer the retrieved context and cite facts succinctly in your reasoning, but do not fabricate sources.\n\n");
        } else if (doRetrieval) {
            systemCtx.append("You have access to a knowledge base, but no relevant information was found for this query.\n\n");
        }
        if (code_content != null && !code_content.isEmpty()) {
            systemCtx.append("Relevant code:\n").append(code_content);
        }
        messages.put(new JSONObject().put("role", "system").put("content", systemCtx.toString()));

        for (JsonObject m : trimmedHistory) {
            String role = m.has("role") ? m.get("role").getAsString() : "user";
            String content = m.has("content") ? m.get("content").getAsString() : "";
            messages.put(new JSONObject().put("role", role).put("content", content));
        }

        messages.put(new JSONObject().put("role", "user").put("content", question));

        // 6) 调用 DeepSeek Chat 接口
        String deepseekKey = cfg.getDeepseekKey();
        String answer;
        try {
            answer = chat(messages, deepseekKey, cfg);
        } catch (IOException e) {
            throw new RuntimeException("Error communicating with DeepSeek API.", e);
        }

        // 7) 追加代码智能修改逻辑
        String codeResult = "";
        if (code_content != null && !code_content.trim().isEmpty()) {
            try {
                JSONArray codeMsgs = new JSONArray();
                // 系统提示，严格要求输出
                String sys = String.join("\n",
                        "你是一个资深的代码助手。",
                        "任务：根据用户问题判断给定代码是否需要修改；",
                        "规则：",
                        "1) 如果无需修改，严格只输出：经判断无需修改代码",
                        "2) 如果需要修改，直接输出完整的、可替换原文件的修改后代码，不要附加解释、标题、围栏或任何额外文本。",
                        "3) 输出时不要使用代码块围栏（例如 ```）。" );
                codeMsgs.put(new JSONObject().put("role", "system").put("content", sys));

                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("用户问题：\n").append(question).append("\n\n");
                userPrompt.append("待判断与可能需要修改的代码：\n");
                userPrompt.append(code_content);

                codeMsgs.put(new JSONObject().put("role", "user").put("content", userPrompt.toString()));

                String judgeOrPatched = chat(codeMsgs, deepseekKey, cfg);
                judgeOrPatched = judgeOrPatched == null ? "" : judgeOrPatched.trim();
                judgeOrPatched = stripCodeFences(judgeOrPatched);

                if (judgeOrPatched.contains("经判断无需修改代码")) {
                    // 模型判定无需修改，保持 codeResult 为空
                } else {
                    codeResult = judgeOrPatched;
                }
            } catch (Exception ex) {
                // 出错时不影响主流程，保持 codeResult 为空
                System.err.println("Code modify step error: " + ex.getMessage());
            }
        }

        // 8) 返回包含检索结果、是否引用、引用来源列表以及 code 字段
        JsonObject resp = new JsonObject();
        resp.addProperty("cited", cited);
        resp.addProperty("retrieval_result", retrievedConcat.toString());
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
        resp.addProperty("code", codeResult);
        System.out.println(codeResult);
        return resp.toString();
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
        List<JsonObject> reversed = new ArrayList<>();
        int acc = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            JsonObject m = items.get(i);
            String content = m.has("content") ? m.get("content").getAsString() : "";
            int add = content.length() + 20;
            if (acc + add > maxChars) break;
            reversed.add(0, m);
            acc += add;
        }
        return reversed;
    }

    private static String stripCodeFences(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        // Remove leading and trailing triple backtick fences with optional language
        if (trimmed.startsWith("```")) {
            // remove starting fence line
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                // whole string was just ```lang or ```
                return "";
            }
            // remove ending fence
            int lastFence = trimmed.lastIndexOf("```\n");
            if (lastFence < 0) lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            return trimmed.trim();
        }
        // Also handle inline single backticks expanded over full content
        if (trimmed.length() >= 2 && trimmed.startsWith("`") && trimmed.endsWith("`")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
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

    /*
    // main method is commented out as it can no longer be run directly without a Project context
    public static void main(String[] args) {
        AskQuestion aq = new AskQuestion();
        String chatHistory = "[\n  {\"role\":\"user\",\"content\":\"Hi\"},\n  {\"role\":\"assistant\",\"content\":\"Hello! How can I help?\"}\n]";
        String code = "public class Demo { void run(){} }";
        String pdfRoot = "C:\\Users\\lenovo\\Desktop\\JavaTA\\pdfs"; // 请将此路径改为你的 PDF 根目录
        String question = "What is Fan Hongfei?";
        String resp = aq.askQuestion(question, chatHistory, code, pdfRoot);
        System.out.println(resp);
    }
    */
}

