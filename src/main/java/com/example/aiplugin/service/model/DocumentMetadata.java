package com.example.aiplugin.service.model;

import java.io.File;
import java.util.Date;

/**
 * 文档元数据
 * 存储文档的基本信息和提取状态
 */
public class DocumentMetadata {
    private String filePath;
    private String fileName;
    private String fileType;  // PDF, PPT, etc.
    private long fileSize;
    private Date lastModified;
    private Date processedDate;
    private int totalChunks;
    private boolean isProcessed;

    public DocumentMetadata(String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        this.fileName = file.getName();
        this.fileSize = file.length();
        this.lastModified = new Date(file.lastModified());
        this.isProcessed = false;
    }

    // Getters and Setters
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Date getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(Date processedDate) {
        this.processedDate = processedDate;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }
}


