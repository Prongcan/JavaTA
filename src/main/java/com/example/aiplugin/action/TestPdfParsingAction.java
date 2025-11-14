package com.example.aiplugin.action;

import com.example.aiplugin.service.*;
import com.example.aiplugin.service.model.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * 通用文档解析功能测试Action
 * 支持 PDF 和 PPTX 文件解析并导出为 Markdown
 */
public class TestPdfParsingAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        try {
            System.out.println("\n\n==================== ACTION TRIGGERED ====================");

            Project project = e.getProject();
            if (project == null) {
                System.err.println("ERROR: Project is null");
                Messages.showMessageDialog("无法获取项目实例", "错误", Messages.getErrorIcon());
                return;
            }

            System.out.println("Project: " + project.getName());
            System.out.println("Project path: " + project.getBasePath());

            // ✅ 弹出对话框让用户输入文件路径
            String filePath = Messages.showInputDialog(
                    project,
                    "请输入文件完整路径（支持 PDF 和 PPTX）:\n例如: C:/Users/YourName/Documents/file.pdf",
                    "文档解析 - 输入文件路径",
                    Messages.getQuestionIcon()
            );

            if (filePath == null || filePath.trim().isEmpty()) {
                Messages.showMessageDialog("未输入文件路径", "取消", Messages.getInformationIcon());
                return;
            }

            // 去除首尾空格
            filePath = filePath.trim();

            // 去除开头和结尾的引号（单引号或双引号）
            if ((filePath.startsWith("\"") && filePath.endsWith("\"")) ||
                    (filePath.startsWith("'") && filePath.endsWith("'"))) {
                filePath = filePath.substring(1, filePath.length() - 1);
            }

            System.out.println("处理后的文件路径: " + filePath);

            // 运行测试
            runTest(project, filePath);

        } catch (Throwable t) {
            System.err.println("ERROR in actionPerformed: " + t.getMessage());
            t.printStackTrace();
            Messages.showMessageDialog(
                    "测试失败:\n" + t.getMessage(),
                    "错误",
                    Messages.getErrorIcon()
            );
        }
    }

    private void runTest(Project project, String filePath) {
        try {
            StringBuilder result = new StringBuilder();

            result.append("========================================\n");
            result.append("文档解析功能测试\n");
            result.append("========================================\n\n");

            File file = new File(filePath);

            result.append("文件路径: ").append(filePath).append("\n");
            result.append("文件名: ").append(file.getName()).append("\n");

            System.out.println("========================================");
            System.out.println("文档解析功能测试");
            System.out.println("========================================");
            System.out.println("文件路径: " + filePath);
            System.out.println("文件名: " + file.getName());

            // ✅ 检查文件是否存在
            if (!file.exists()) {
                result.append("❌ 文件不存在: ").append(filePath).append("\n");
                System.err.println("❌ 文件不存在: " + filePath);
                showResult("测试失败", result.toString());
                return;
            }

            // ✅ 检查文件大小
            long fileSize = file.length();
            System.out.println("文件大小: " + fileSize + " 字节");
            result.append("文件大小: ").append(fileSize).append(" 字节\n");

            if (fileSize == 0) {
                result.append("❌ 文件为空（0 字节），可能损坏或未下载完成\n");
                System.err.println("❌ 文件为空");
                showResult("测试失败", result.toString());
                return;
            }

            // ✅ 检查文件类型
            String fileName = file.getName().toLowerCase();
            boolean isPdf = fileName.endsWith(".pdf");
            boolean isPpt = fileName.endsWith(".ppt") || fileName.endsWith(".pptx");

            if (!isPdf && !isPpt) {
                result.append("❌ 不支持的文件类型\n");
                result.append("支持的格式: .pdf, .ppt, .pptx\n");
                showResult("测试失败", result.toString());
                return;
            }

            // ✅ 验证 PDF 文件头
            if (isPdf) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    byte[] header = new byte[5];
                    int bytesRead = fis.read(header);

                    if (bytesRead < 5) {
                        result.append("❌ PDF 文件头不完整（文件可能损坏）\n");
                        System.err.println("❌ PDF 文件头不完整");
                        showResult("测试失败", result.toString());
                        return;
                    }

                    String headerStr = new String(header);
                    System.out.println("PDF 文件头: " + headerStr);

                    if (!headerStr.equals("%PDF-")) {
                        result.append("❌ 不是有效的 PDF 文件\n");
                        result.append("  文件头: ").append(headerStr).append("\n");
                        result.append("  应为: %PDF-\n");
                        System.err.println("❌ 无效的 PDF 文件头: " + headerStr);
                        showResult("测试失败", result.toString());
                        return;
                    }
                } catch (Exception ex) {
                    result.append("❌ 无法读取文件: ").append(ex.getMessage()).append("\n");
                    showResult("测试失败", result.toString());
                    return;
                }
            }

            result.append("\n");

            String fileType = isPdf ? "PDF" : "PPTX";
            System.out.println("✓ " + fileType + " 文件验证通过，开始解析...\n");

            // 获取服务实例
            DocumentParserService parserService = DocumentParserService.getInstance(project);
            ChunkIndexService indexService = ChunkIndexService.getInstance(project);
            MarkdownExportService exportService = MarkdownExportService.getInstance(project);

            try {
                // ✅ 根据文件类型选择提取方法
                if (isPdf) {
                    // PDF 处理
                    System.out.println("【步骤1】PDF 页面级别提取");
                    result.append("【步骤1】PDF 页面级别提取\n");
                    result.append("----------------------------------------\n");

                    List<DocumentParserService.PageContent> pages = parserService.extractPdfByPages(filePath);
                    result.append("✓ 提取成功！\n");
                    result.append("  总页数: ").append(pages.size()).append("\n\n");

                    // 显示前3页预览
                    for (int i = 0; i < Math.min(3, pages.size()); i++) {
                        DocumentParserService.PageContent page = pages.get(i);
                        result.append("  第").append(page.getPageNumber()).append("页预览:\n");
                        result.append("    字符数: ").append(page.getContent().length()).append("\n");
                        String preview = page.getContent().substring(0, Math.min(100, page.getContent().length()));
                        result.append("    内容: ").append(preview.replace("\n", " ")).append("...\n\n");
                    }

                    // 导出 Markdown
                    System.out.println("【步骤2】导出为 Markdown");
                    result.append("【步骤2】导出为 Markdown\n");
                    result.append("----------------------------------------\n");

                    String outputDir = project.getBasePath() + File.separator + "output";
                    new File(outputDir).mkdirs();
                    String mdPath = outputDir + File.separator + file.getName().replace(".pdf", ".md");

                    exportService.exportPdfPagesToMarkdown(pages, mdPath);
                    result.append("✓ 已导出为 Markdown\n");
                    result.append("  输出路径: ").append(mdPath).append("\n\n");

                } else {
                    // PPTX 处理
                    System.out.println("【步骤1】PPTX 幻灯片提取");
                    result.append("【步骤1】PPTX 幻灯片提取\n");
                    result.append("----------------------------------------\n");

                    List<DocumentParserService.SlideContent> slides = parserService.extractPptBySlides(filePath);
                    result.append("✓ 提取成功！\n");
                    result.append("  总幻灯片数: ").append(slides.size()).append("\n\n");

                    // 显示前3张幻灯片预览
                    for (int i = 0; i < Math.min(3, slides.size()); i++) {
                        DocumentParserService.SlideContent slide = slides.get(i);
                        result.append("  第").append(slide.getSlideNumber()).append("张幻灯片预览:\n");
                        result.append("    字符数: ").append(slide.getContent().length()).append("\n");
                        String preview = slide.getContent().substring(0, Math.min(100, slide.getContent().length()));
                        result.append("    内容: ").append(preview.replace("\n", " ")).append("...\n\n");
                    }

                    // 导出 Markdown
                    System.out.println("【步骤2】导出为 Markdown");
                    result.append("【步骤2】导出为 Markdown\n");
                    result.append("----------------------------------------\n");

                    String outputDir = project.getBasePath() + File.separator + "output";
                    new File(outputDir).mkdirs();
                    String mdPath = outputDir + File.separator +
                            file.getName().replace(".pptx", ".md").replace(".ppt", ".md");

                    exportService.exportPptSlidesToMarkdown(slides, mdPath);
                    result.append("✓ 已导出为 Markdown\n");
                    result.append("  输出路径: ").append(mdPath).append("\n\n");
                }

                // 文档索引（可选）
                System.out.println("【步骤3】文档索引");
                result.append("【步骤3】文档索引\n");
                result.append("----------------------------------------\n");

                DocumentMetadata metadata = indexService.processDocument(filePath, parserService);
                result.append("✓ 索引完成！\n");
                result.append("  文件名: ").append(metadata.getFileName()).append("\n");
                result.append("  总块数: ").append(metadata.getTotalChunks()).append("\n\n");

                result.append("========================================\n");
                result.append("✅ 解析完成！\n");
                result.append("========================================\n");

                showResult("解析成功", result.toString());

            } catch (Exception ex) {
                result.append("\n❌ 解析失败: ").append(ex.getMessage()).append("\n");
                System.err.println("❌ 解析失败: " + ex.getMessage());
                ex.printStackTrace();
                showResult("解析失败", result.toString());
            }

        } catch (Throwable t) {
            System.err.println("FATAL ERROR in runTest: " + t.getMessage());
            t.printStackTrace();
            Messages.showMessageDialog(
                    "严重错误:\n" + t.getMessage() + "\n\n请查看控制台",
                    "测试失败",
                    Messages.getErrorIcon()
            );
        }
    }

    private void showResult(String title, String message) {
        Messages.showMessageDialog(message, title, Messages.getInformationIcon());
    }
}
