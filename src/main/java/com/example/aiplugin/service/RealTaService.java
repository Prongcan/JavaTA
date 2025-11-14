package com.example.aiplugin.service;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class RealTaService implements TaService {

    @Override
    public String askQuestion(String question){
        try {
            // 将 chat 方法的调用放入 try 块中
            String response = chat(question, "deepseek", "sk-8cc37591bfce4d00b473f6f7f844a22a");
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Error communicating with DeepSeek API.", e);
        }
    }

    @Override
    public String askAboutCode(String code, String question) {
        // Simulate a delay
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return askQuestion(code + question);
    }

    public String chat(String prompt, String model_name, String api_key) throws IOException {
        String baseUrl = "";
        if(model_name == "deepseek")
        {
            baseUrl = "https://api.deepseek.com/v1";
        }
        else return("Sorry we can't answer because model name is incorrect.");
        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + api_key);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // 构造请求体
        JSONObject body = new JSONObject();
        body.put("model", "deepseek-chat");
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You are a helpful assistant."));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);
        body.put("stream", false);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        Scanner scanner;
        if (code == HttpURLConnection.HTTP_OK) {
            scanner = new Scanner(conn.getInputStream(), "utf-8");
        } else {
            scanner = new Scanner(conn.getErrorStream(), "utf-8");
        }
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
        }
        scanner.close();

        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Chat API returned non‐OK code: " + code + ", body: " + sb.toString());
        }

        JSONObject respJson = new JSONObject(sb.toString());
        // 根据接口规范：choices[0].message.content
        String reply = respJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        return reply;
    }
}

