package com.example.aiplugin.service;

import com.example.aiplugin.service.model.DocumentMetadata;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * 文档解析服务
 * 使用Apache Tika提取PDF和PPT文件中的文本内容
 */
@Service(Service.Level.PROJECT)
public final class DocumentParserService {

    // 仅用于PPT等非PDF文件
    private static final Tika tika = new Tika();
    private static final Parser parser = new AutoDetectParser();
    
    /**
     * 从文件中提取文本内容
     * 
     * @param filePath 文件路径（支持PDF、PPT等格式）
     * @return 提取的文本内容
     * @throws IOException IO异常
     * @throws TikaException Tika解析异常
     */
    public String extractText(String filePath) throws IOException, TikaException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }
        
        // 对于PDF文件，直接使用PDFBox，避免Tika的XPathFactory问题
        if (isPdf(filePath)) {
            return extractTextFromPdfWithPdfBox(file);
        }
        
        return extractText(file);
    }
    
    /**
     * 从文件中提取文本内容
     * 
     * @param file 文件对象
     * @return 提取的文本内容
     * @throws IOException IO异常
     * @throws TikaException Tika解析异常
     */
    public String extractText(File file) throws IOException, TikaException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return extractText(inputStream, file.getName());
        }
    }
    
    /**
     * 从输入流中提取文本内容
     * 
     * @param inputStream 输入流
     * @param fileName 文件名（用于识别文件类型）
     * @return 提取的文本内容
     * @throws IOException IO异常
     * @throws TikaException Tika解析异常
     */
    public String extractText(InputStream inputStream, String fileName) 
            throws IOException, TikaException {
        try {
            // 使用BodyContentHandler来提取文本内容
            // 设置最大字符数限制（-1表示无限制）
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            
            // 解析文档
            parser.parse(inputStream, handler, metadata, parseContext);
            
            return handler.toString();
        } catch (SAXException e) {
            throw new TikaException("解析文档时发生SAX异常", e);
        }
    }
    
    /**
     * 检测文件类型（使用扩展名，避免Tika的XPathFactory问题）
     * 
     * @param filePath 文件路径
     * @return MIME类型
     * @throws IOException IO异常
     */
    public String detectFileType(String filePath) throws IOException {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerPath.endsWith(".ppt") || lowerPath.endsWith(".pptx")) {
            return "application/vnd.ms-powerpoint";
        } else if (lowerPath.endsWith(".doc") || lowerPath.endsWith(".docx")) {
            return "application/msword";
        }
        return "application/octet-stream";
    }
    
    /**
     * 检查文件是否为PDF格式（直接判断扩展名，避免Tika）
     * 
     * @param filePath 文件路径
     * @return 是否为PDF
     */
    public boolean isPdf(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".pdf");
    }
    
    /**
     * 检查文件是否为PPT格式（直接判断扩展名，避免Tika）
     * 
     * @param filePath 文件路径
     * @return 是否为PPT
     */
    public boolean isPpt(String filePath) {
        if (filePath == null) return false;
        String lower = filePath.toLowerCase();
        return lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }
    
    /**
     * 检查文件是否为支持的文档格式（PDF或PPT）
     * 
     * @param filePath 文件路径
     * @return 是否支持
     */
    public boolean isSupportedFormat(String filePath) {
        return isPdf(filePath) || isPpt(filePath);
    }
    
    /**
     * 提取文档并返回元数据（直接判断扩展名，避免Tika）
     * 
     * @param filePath 文件路径
     * @return 文档元数据对象
     * @throws IOException IO异常
     * @throws TikaException Tika解析异常
     */
    public DocumentMetadata extractDocumentMetadata(String filePath) 
            throws IOException, TikaException {
        DocumentMetadata metadata = new DocumentMetadata(filePath);
        // 直接判断扩展名，完全避免Tika
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) {
            metadata.setFileType("application/pdf");
        } else if (lowerPath.endsWith(".ppt") || lowerPath.endsWith(".pptx")) {
            metadata.setFileType("application/vnd.ms-powerpoint");
        } else {
            metadata.setFileType("application/octet-stream");
        }
        return metadata;
    }
    
    /**
     * 使用PDFBox提取PDF文本（避免XPathFactory问题）
     * 
     * @param file PDF文件对象
     * @return 提取的文本内容
     * @throws IOException IO异常
     */
    private String extractTextFromPdfWithPdfBox(File file) throws IOException {
        StringBuilder fullText = new StringBuilder();

        org.apache.pdfbox.io.MemoryUsageSetting memSettings =
                org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly();

        try (PDDocument document = PDDocument.load(file, memSettings)) {
            PDFTextStripper textStripper = new PDFTextStripper() {
                @Override
                protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator,
                                               java.util.List<org.apache.pdfbox.cos.COSBase> operands)
                        throws IOException {
                    try {
                        super.processOperator(operator, operands);
                    } catch (Exception e) {
                        System.err.println("⚠ 跳过图像处理: " + e.getMessage());
                    }
                }
            };
            fullText.append(textStripper.getText(document));
        }

        return fullText.toString();
    }
    
    /**
     * PDF页面级别的文本提取
     * 按页提取PDF文本内容，每页作为一个独立的文本块
     * 
     * @param filePath PDF文件路径
     * @return 页面文本列表，索引对应页码（从0开始）
     * @throws IOException IO异常
     */
    public List<PageContent> extractPdfByPages(String filePath) throws IOException {
        if (!isPdf(filePath)) {
            throw new IllegalArgumentException("文件不是PDF格式: " + filePath);
        }

        List<PageContent> pages = new ArrayList<>();
        File file = new File(filePath);

        // ✅ 关键：配置 PDDocument 加载器，忽略图像错误
        org.apache.pdfbox.io.MemoryUsageSetting memSettings =
                org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly();

        try (PDDocument document = PDDocument.load(file, memSettings)) {
            // ✅ 禁用渲染模式（避免触发图像过滤器）
            PDFTextStripper textStripper = new PDFTextStripper() {
                @Override
                protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator,
                                               java.util.List<org.apache.pdfbox.cos.COSBase> operands)
                        throws IOException {
                    try {
                        super.processOperator(operator, operands);
                    } catch (Exception e) {
                        // 忽略图像处理错误，继续提取文本
                        System.err.println("⚠ 跳过图像处理错误: " + e.getMessage());
                    }
                }
            };

            textStripper.setSortByPosition(true);
            int totalPages = document.getNumberOfPages();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                textStripper.setStartPage(pageNum);
                textStripper.setEndPage(pageNum);

                try {
                    String pageText = textStripper.getText(document);
                    PageContent pageContent = new PageContent(pageNum, pageText, filePath);
                    pages.add(pageContent);
                } catch (Exception e) {
                    System.err.println("⚠ 第 " + pageNum + " 页提取失败，尝试简单模式: " + e.getMessage());

                    // 备用方案：只提取纯文本，忽略所有格式
                    try {
                        PDFTextStripper simpleStripper = new PDFTextStripper();
                        simpleStripper.setStartPage(pageNum);
                        simpleStripper.setEndPage(pageNum);
                        String simpleText = simpleStripper.getText(document);
                        pages.add(new PageContent(pageNum, simpleText, filePath));
                    } catch (Exception e2) {
                        pages.add(new PageContent(pageNum, "[页面提取失败]", filePath));
                    }
                }
            }
        }

        return pages;
    }

    /**
     * PPT幻灯片级别的文本提取 - 使用 ZIP 直接读取（绕过 XPath）
     *
     * @param filePath PPTX 文件路径
     * @return 幻灯片内容列表
     * @throws IOException IO异常
     */
    public List<SlideContent> extractPptBySlides(String filePath) throws IOException {
        if (!isPpt(filePath)) {
            throw new IllegalArgumentException("文件不是PPT格式: " + filePath);
        }

        List<SlideContent> slides = new ArrayList<>();

        try {
            // ✅ 使用 ZIP 直接读取 PPTX（绕过所有 XPath 依赖）
            slides = extractPptBySlidesDirectly(filePath);
            System.out.println("✓ 使用直接 ZIP 解析成功提取 PPTX");
        } catch (Exception e) {
            System.err.println("✗ PPTX 解析失败: " + e.getMessage());
            e.printStackTrace();
            // 返回错误信息
            slides.add(new SlideContent(1, "[PPTX 解析失败: " + e.getMessage() + "]", filePath));
        }

        return slides;
    }

    /**
     * 直接从 PPTX ZIP 结构中提取文本（完全绕过 POI 和 Tika）
     *
     * @param filePath PPTX 文件路径
     * @return 幻灯片内容列表
     * @throws IOException IO异常
     */
    private List<SlideContent> extractPptBySlidesDirectly(String filePath) throws IOException {
        List<SlideContent> slides = new ArrayList<>();
        File file = new File(filePath);

        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(file)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();

            // 收集所有幻灯片文件
            java.util.Map<Integer, String> slideMap = new java.util.TreeMap<>();

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // 查找幻灯片 XML 文件（格式: ppt/slides/slide1.xml, slide2.xml, ...）
                if (name.matches("ppt/slides/slide\\d+\\.xml")) {
                    try {
                        // 提取幻灯片编号
                        String numStr = name.replaceAll("\\D+", "");
                        int slideNum = Integer.parseInt(numStr);

                        // 读取 XML 内容
                        try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                            String xmlContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

                            // 提取文本（简单的 XML 解析，不使用 XPath）
                            String text = extractTextFromSlideXml(xmlContent);
                            slideMap.put(slideNum, text);
                        }
                    } catch (Exception e) {
                        System.err.println("⚠ 跳过幻灯片 " + name + ": " + e.getMessage());
                    }
                }
            }

            // 按顺序创建 SlideContent 对象
            for (java.util.Map.Entry<Integer, String> entry : slideMap.entrySet()) {
                slides.add(new SlideContent(entry.getKey(), entry.getValue(), filePath));
            }

            if (slides.isEmpty()) {
                throw new IOException("未找到任何幻灯片内容");
            }

        } catch (Exception e) {
            throw new IOException("读取 PPTX 文件失败: " + e.getMessage(), e);
        }

        return slides;
    }

    /**
     * 从幻灯片 XML 中提取纯文本（不使用 XPath）
     *
     * @param xmlContent 幻灯片的 XML 内容
     * @return 提取的文本
     */
    private String extractTextFromSlideXml(String xmlContent) {
        StringBuilder text = new StringBuilder();

        // 查找所有 <a:t> 标签（文本标签）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<a:t>(.*?)</a:t>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(xmlContent);

        while (matcher.find()) {
            String textContent = matcher.group(1);
            // 解码 XML 实体
            textContent = textContent.replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'");
            text.append(textContent).append("\n");
        }

        return text.toString().trim();
    }

    /**
     * 页面内容数据类（PDF）
     */
    public static class PageContent {
        private final int pageNumber;
        private final String content;
        private final String sourceFile;
        
        public PageContent(int pageNumber, String content, String sourceFile) {
            this.pageNumber = pageNumber;
            this.content = content;
            this.sourceFile = sourceFile;
        }
        
        public int getPageNumber() {
            return pageNumber;
        }
        
        public String getContent() {
            return content;
        }
        
        public String getSourceFile() {
            return sourceFile;
        }
        
        public String getCitation() {
            String fileName = new File(sourceFile).getName();
            return String.format("%s, Page %d", fileName, pageNumber);
        }
    }
    
    /**
     * 幻灯片内容数据类（PPT）
     */
    public static class SlideContent {
        private final int slideNumber;
        private final String content;
        private final String sourceFile;
        
        public SlideContent(int slideNumber, String content, String sourceFile) {
            this.slideNumber = slideNumber;
            this.content = content;
            this.sourceFile = sourceFile;
        }
        
        public int getSlideNumber() {
            return slideNumber;
        }
        
        public String getContent() {
            return content;
        }
        
        public String getSourceFile() {
            return sourceFile;
        }
        
        public String getCitation() {
            String fileName = new File(sourceFile).getName();
            return String.format("%s, Slide %d", fileName, slideNumber);
        }
    }
    
    /**
     * 获取服务实例
     * 
     * @param project 当前项目
     * @return DocumentParserService实例
     */
    public static DocumentParserService getInstance(Project project) {
        return project.getService(DocumentParserService.class);
    }
}

