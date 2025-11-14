package com.example.aiplugin.service.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 文本块数据模型
 * 用于存储分块后的文本内容及其元数据
 */
public class TextChunk {
    private String content;           // 文本内容
    private int chunkIndex;           // 块索引
    private int startPosition;        // 在原文本中的起始位置
    private int endPosition;          // 在原文本中的结束位置
    private String sourceFile;        // 源文件路径
    private String chunkType;         // 块类型（如：paragraph, section, page等）
    private int pageNumber;          // 页码（如果是PDF）
    private Map<String, String> metadata;  // 额外的元数据

    /**
     * 构造函数
     */
    public TextChunk(String content, int chunkIndex) {
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.metadata = new HashMap<>();
    }

    /**
     * 完整构造函数
     */
    public TextChunk(String content, int chunkIndex, int startPosition, int endPosition, 
                     String sourceFile, String chunkType, int pageNumber) {
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.sourceFile = sourceFile;
        this.chunkType = chunkType;
        this.pageNumber = pageNumber;
        this.metadata = new HashMap<>();
    }

    // Getters and Setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return this.metadata.get(key);
    }

    /**
     * 获取文本块的长度
     */
    public int getLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 获取源引用信息（用于RAG引用）
     */
    public String getSourceCitation() {
        String fileName = new java.io.File(sourceFile).getName();
        if (pageNumber > 0) {
            return String.format("%s, Page %d", fileName, pageNumber);
        }
        return fileName;
    }

    @Override
    public String toString() {
        return String.format("TextChunk[index=%d, type=%s, length=%d, source=%s, page=%d]", 
                chunkIndex, chunkType, getLength(), sourceFile, pageNumber);
    }
}


