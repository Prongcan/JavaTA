package com.example.aiplugin.service;

import com.example.aiplugin.service.model.TextChunk;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Markdown导出服务
 * 将提取的文本内容导出为Markdown格式
 */
@Service(Service.Level.PROJECT)
public final class MarkdownExportService {

    /**
     * 将文本块列表导出为Markdown文件
     *
     * @param chunks 文本块列表
     * @param outputPath 输出文件路径
     * @throws IOException IO异常
     */
    public void exportChunksToMarkdown(List<TextChunk> chunks, String outputPath) throws IOException {
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(outputFile, false)) {
            // 写入文档头部
            writer.write("# Document Export\n\n");
            writer.write("Generated from: " + (chunks.isEmpty() ? "Unknown" : chunks.get(0).getSourceFile()) + "\n\n");
            writer.write("Total chunks: " + chunks.size() + "\n\n");
            writer.write("---\n\n");

            // 写入每个文本块
            for (TextChunk chunk : chunks) {
                writer.write("## Chunk " + chunk.getChunkIndex() + "\n\n");

                // 写入元数据
                writer.write("**Source:** " + chunk.getSourceCitation() + "\n\n");
                writer.write("**Type:** " + chunk.getChunkType() + "\n\n");
                if (chunk.getPageNumber() > 0) {
                    writer.write("**Page:** " + chunk.getPageNumber() + "\n\n");
                }

                // 写入内容
                writer.write("**Content:**\n\n");
                writer.write(escapeMarkdown(chunk.getContent()));
                writer.write("\n\n");
                writer.write("---\n\n");
            }
        }
    }

    /**
     * 将页面内容列表导出为Markdown文件（PDF）
     *
     * @param pages 页面内容列表
     * @param outputPath 输出文件路径
     * @throws IOException IO异常
     */
    public void exportPdfPagesToMarkdown(List<DocumentParserService.PageContent> pages, String outputPath)
            throws IOException {
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(outputFile, false)) {
            // 写入文档头部
            String sourceFile = pages.isEmpty() ? "Unknown" : pages.get(0).getSourceFile();
            String fileName = new File(sourceFile).getName();
            writer.write("# " + fileName + "\n\n");
            writer.write("**Source:** " + sourceFile + "\n\n");
            writer.write("**Total Pages:** " + pages.size() + "\n\n");
            writer.write("---\n\n");

            // 写入每个页面
            for (DocumentParserService.PageContent page : pages) {
                writer.write("## Page " + page.getPageNumber() + "\n\n");
                writer.write(escapeMarkdown(page.getContent()));
                writer.write("\n\n");
                writer.write("---\n\n");
            }
        }
    }

    /**
     * 将幻灯片内容列表导出为Markdown文件（PPT）
     *
     * @param slides 幻灯片内容列表
     * @param outputPath 输出文件路径
     * @throws IOException IO异常
     */
    public void exportPptSlidesToMarkdown(List<DocumentParserService.SlideContent> slides, String outputPath)
            throws IOException {
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(outputFile, false)) {
            // 写入文档头部
            String sourceFile = slides.isEmpty() ? "Unknown" : slides.get(0).getSourceFile();
            String fileName = new File(sourceFile).getName();
            writer.write("# " + fileName + "\n\n");
            writer.write("**Source:** " + sourceFile + "\n\n");
            writer.write("**Total Slides:** " + slides.size() + "\n\n");
            writer.write("---\n\n");

            // 写入每个幻灯片
            for (DocumentParserService.SlideContent slide : slides) {
                writer.write("## Slide " + slide.getSlideNumber() + "\n\n");
                writer.write(escapeMarkdown(slide.getContent()));
                writer.write("\n\n");
                writer.write("---\n\n");
            }
        }
    }

    /**
     * 将纯文本导出为Markdown文件
     *
     * @param text 文本内容
     * @param outputPath 输出文件路径
     * @param title 文档标题
     * @throws IOException IO异常
     */
    public void exportTextToMarkdown(String text, String outputPath, String title) throws IOException {
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(outputFile, false)) {
            writer.write("# " + (title != null ? title : "Document") + "\n\n");
            writer.write(escapeMarkdown(text));
        }
    }

    /**
     * 转义Markdown特殊字符
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }

        // 转义Markdown特殊字符
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("#", "\\#")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("`", "\\`")
                .replace(">", "\\>");
    }

    /**
     * 获取服务实例
     *
     * @param project 当前项目
     * @return MarkdownExportService实例
     */
    public static MarkdownExportService getInstance(Project project) {
        return project.getService(MarkdownExportService.class);
    }
}