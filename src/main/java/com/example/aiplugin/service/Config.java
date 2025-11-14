package com.example.aiplugin.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Centralized configuration loader for AI Plugin.
 * Order of precedence:
 * 1) External file in working dir: ./aiplugin-config.json
 * 2) Classpath resource: /aiplugin-config.json
 * 3) Built-in defaults
 */
public class Config {

    private static volatile Config INSTANCE;

    private final Gson gson = new Gson();
    private final JsonObject root;

    private Config(JsonObject root) { this.root = root == null ? new JsonObject() : root; }

    public static Config load() {
        if (INSTANCE != null) return INSTANCE;
        synchronized (Config.class) {
            if (INSTANCE != null) return INSTANCE;
            JsonObject json = null;
            try {
                // 1) Working dir file
                File f = new File("aiplugin-config.json");
                if (f.exists()) {
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    json = new Gson().fromJson(s, JsonObject.class);
                } else {
                    // 2) Classpath
                    try (InputStream is = Config.class.getClassLoader().getResourceAsStream("aiplugin-config.json")) {
                        if (is != null) {
                            String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            json = new Gson().fromJson(s, JsonObject.class);
                        }
                    }
                }
            } catch (Exception ignore) {}
            if (json == null) json = new JsonObject();
            INSTANCE = new Config(json);
            return INSTANCE;
        }
    }

    private JsonObject obj(String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    private String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private int i(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private double d(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    private boolean b(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    // Embeddings
    public String getVectorFile() {
        JsonObject e = obj("embeddings");
        return str(e, "file", "embeddings.json");
    }

    public String getEmbeddingApiUrl() {
        JsonObject e = obj("embeddings");
        return str(e, "api_url", "https://api.openai.com/v1/embeddings");
    }

    public String getEmbeddingModel() {
        JsonObject e = obj("embeddings");
        return str(e, "model", "text-embedding-3-small");
    }

    public String getOpenAIKey() {
        JsonObject e = obj("embeddings");
        String key = str(e, "api_key", "");
        if (key != null && !key.isEmpty()) return key;
        String env = str(e, "api_key_env", "OPENAI_API_KEY");
        String v = System.getenv(env);
        return v == null ? "" : v;
    }

    // PDF/Chunking
    public String getProcessedPdfsFile() {
        JsonObject p = obj("pdf");
        return str(p, "processed_file", "processed_pdfs.json");
    }

    public int getChunkSize() {
        JsonObject p = obj("pdf");
        return i(p, "chunk_size", 400);
    }

    public int getOverlap() {
        JsonObject p = obj("pdf");
        return i(p, "overlap", 200);
    }

    public String getPdfDefaultRoot() {
        JsonObject p = obj("pdf");
        return str(p, "default_root", "./pdfs");
    }

    // Proxy
    public boolean isProxyEnabled() {
        JsonObject p = obj("proxy");
        return b(p, "enabled", true);
    }

    public String getProxyHost() {
        JsonObject p = obj("proxy");
        return str(p, "host", "127.0.0.1");
    }

    public int getProxyPort() {
        JsonObject p = obj("proxy");
        return i(p, "port", 7897);
    }

    // Retrieval
    public int getTopK() {
        JsonObject r = obj("retrieval");
        return i(r, "top_k", 5);
    }

    public double getThreshold() {
        JsonObject r = obj("retrieval");
        return d(r, "threshold", 0.7);
    }

    // Chat
    public String getChatBaseUrl() {
        JsonObject c = obj("chat");
        return str(c, "base_url", "https://api.deepseek.com/v1");
    }

    public String getChatModel() {
        JsonObject c = obj("chat");
        return str(c, "model", "deepseek-chat");
    }

    public String getDeepseekKey() {
        JsonObject c = obj("chat");
        String key = str(c, "api_key", "");
        if (key != null && !key.isEmpty()) return key;
        String env = str(c, "api_key_env", "DEEPSEEK_API_KEY");
        String v = System.getenv(env);
        return v == null ? "" : v;
    }
}

