package com.example.aiplugin.service;

import com.example.aiplugin.service.model.DocumentMetadata;
import com.example.aiplugin.service.model.TextChunk;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 文本分块和索引服务
 * 将提取的文本按照不同的策略进行分块处理，并使用树/图结构进行索引
 */
@Service(Service.Level.PROJECT)
public final class ChunkIndexService {
    
    // 默认分块大小（字符数）
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    
    // 默认重叠大小（字符数）
    private static final int DEFAULT_OVERLAP_SIZE = 200;
    
    // 文档索引：文件路径 -> 文档元数据
    private final Map<String, DocumentMetadata> documentIndex = new HashMap<>();
    
    // 分块索引：文件路径 -> 文本块列表
    private final Map<String, List<TextChunk>> chunkIndex = new HashMap<>();
    
    // 树形结构索引：文件路径 -> 章节树
    private final Map<String, ChunkTreeNode> treeIndex = new HashMap<>();
    
    /**
     * 按段落分块
     * 根据空行或换行符将文本分割成段落块
     * 
     * @param text 原始文本
     * @param sourceFile 源文件路径
     * @param pageNumber 页码（PDF使用，PPT可为0）
     * @return 文本块列表
     */
    public List<TextChunk> chunkByParagraph(String text, String sourceFile, int pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        // 按双换行符或段落标记分割
        String[] paragraphs = text.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
        
        int currentPosition = 0;
        int chunkIndex = 0;
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            
            int startPos = text.indexOf(paragraph, currentPosition);
            int endPos = startPos + paragraph.length();
            
            TextChunk chunk = new TextChunk(
                paragraph,
                chunkIndex++,
                startPos,
                endPos,
                sourceFile,
                "paragraph",
                pageNumber
            );
            
            chunks.add(chunk);
            currentPosition = endPos;
        }
        
        return chunks;
    }
    
    /**
     * 按固定大小分块
     * 将文本按照固定大小分割，可以设置重叠大小以保持上下文连续性
     * 
     * @param text 原始文本
     * @param sourceFile 源文件路径
     * @param chunkSize 每个块的大小（字符数）
     * @param overlapSize 重叠大小（字符数）
     * @param pageNumber 页码
     * @return 文本块列表
     */
    public List<TextChunk> chunkBySize(String text, String sourceFile, 
                                       int chunkSize, int overlapSize, int pageNumber) {
        List<TextChunk> chunks = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        
        if (overlapSize < 0) {
            overlapSize = 0;
        }
        
        int textLength = text.length();
        int chunkIndex = 0;
        int startPos = 0;
        
        while (startPos < textLength) {
            int endPos = Math.min(startPos + chunkSize, textLength);
            
            // 尝试在单词边界处分割，避免截断单词
            if (endPos < textLength) {
                int lastSpace = text.lastIndexOf(' ', endPos);
                int lastPunct = Math.max(
                    text.lastIndexOf('.', endPos),
                    Math.max(
                        text.lastIndexOf('。', endPos),
                        Math.max(
                            text.lastIndexOf('!', endPos),
                            text.lastIndexOf('！', endPos)
                        )
                    )
                );
                
                int breakPoint = Math.max(lastSpace, lastPunct);
                if (breakPoint > startPos + chunkSize * 0.5) {
                    endPos = breakPoint + 1;
                }
            }
            
            String chunkText = text.substring(startPos, endPos).trim();
            
            if (!chunkText.isEmpty()) {
                TextChunk chunk = new TextChunk(
                    chunkText,
                    chunkIndex++,
                    startPos,
                    endPos,
                    sourceFile,
                    "fixed_size",
                    pageNumber
                );
                chunk.addMetadata("chunk_size", String.valueOf(chunkSize));
                chunk.addMetadata("overlap_size", String.valueOf(overlapSize));
                
                chunks.add(chunk);
            }
            
            // 移动到下一个块的起始位置（考虑重叠）
            startPos = endPos - overlapSize;
            if (startPos <= 0) {
                startPos = endPos;
            }
        }
        
        return chunks;
    }
    
    /**
     * 按章节分块并构建树形结构
     * 根据标题模式（如 "# 标题"、"第X章"等）分割文本并构建树
     * 
     * @param text 原始文本
     * @param sourceFile 源文件路径
     * @param pageNumber 页码
     * @return 章节树根节点
     */
    public ChunkTreeNode chunkBySection(String text, String sourceFile, int pageNumber) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        // 匹配章节标题的模式
        Pattern sectionPattern = Pattern.compile(
            "^(第[一二三四五六七八九十\\d]+章|第[\\d]+章|Chapter\\s+\\d+|#+\\s+.+|第[\\d]+节)",
            Pattern.MULTILINE
        );
        
        String[] lines = text.split("\\n");
        List<SectionInfo> sections = new ArrayList<>();
        
        // 查找所有章节标题的位置
        for (int i = 0; i < lines.length; i++) {
            if (sectionPattern.matcher(lines[i]).find()) {
                int position = 0;
                for (int j = 0; j < i; j++) {
                    position += lines[j].length() + 1;
                }
                sections.add(new SectionInfo(lines[i].trim(), position, i));
            }
        }
        
        // 构建树形结构
        ChunkTreeNode root = new ChunkTreeNode("Root", sourceFile, 0);
        ChunkTreeNode currentNode = root;
        
        for (int i = 0; i < sections.size(); i++) {
            SectionInfo section = sections.get(i);
            int startPos = section.position;
            int endPos = (i + 1 < sections.size()) 
                ? sections.get(i + 1).position 
                : text.length();
            
            String sectionText = text.substring(startPos, endPos).trim();
            
            ChunkTreeNode node = new ChunkTreeNode(section.title, sourceFile, pageNumber);
            node.setContent(sectionText);
            node.setStartPosition(startPos);
            node.setEndPosition(endPos);
            
            currentNode.addChild(node);
            currentNode = node;
        }
        
        return root;
    }
    
    /**
     * 处理文档：提取文本、分块并建立索引
     * 
     * @param filePath 文件路径
     * @param parserService 文档解析服务
     * @return 文档元数据
     * @throws Exception 处理异常
     */
    public DocumentMetadata processDocument(String filePath, DocumentParserService parserService) 
            throws Exception {
        // 创建文档元数据
        DocumentMetadata metadata = parserService.extractDocumentMetadata(filePath);
        
        List<TextChunk> chunks;
        ChunkTreeNode tree = null;
        
        // 根据文件类型选择处理策略
        if (parserService.isPdf(filePath)) {
            // PDF：按页面提取
            List<DocumentParserService.PageContent> pages = parserService.extractPdfByPages(filePath);
            chunks = new ArrayList<>();
            
            // 收集所有页面文本用于构建章节树
            StringBuilder fullTextBuilder = new StringBuilder();
            
            for (DocumentParserService.PageContent page : pages) {
                // 对每页内容进行分块
                List<TextChunk> pageChunks = chunkByParagraph(
                    page.getContent(), 
                    filePath, 
                    page.getPageNumber()
                );
                
                // 如果页面没有段落，创建一个整页块
                if (pageChunks.isEmpty() && !page.getContent().trim().isEmpty()) {
                    TextChunk pageChunk = new TextChunk(
                        page.getContent(),
                        chunks.size(),
                        0,
                        page.getContent().length(),
                        filePath,
                        "page",
                        page.getPageNumber()
                    );
                    chunks.add(pageChunk);
                } else {
                    chunks.addAll(pageChunks);
                }
                
                // 收集文本用于章节树
                fullTextBuilder.append(page.getContent()).append("\n");
            }
            
            // 尝试构建章节树（使用已提取的文本，不再调用extractText）
            String fullText = fullTextBuilder.toString();
            tree = chunkBySection(fullText, filePath, 0);
            
        } else if (parserService.isPpt(filePath)) {
            // PPT：按幻灯片提取
            List<DocumentParserService.SlideContent> slides = parserService.extractPptBySlides(filePath);
            chunks = new ArrayList<>();
            
            for (DocumentParserService.SlideContent slide : slides) {
                // 对每个幻灯片内容进行分块
                List<TextChunk> slideChunks = chunkByParagraph(
                    slide.getContent(),
                    filePath,
                    slide.getSlideNumber()  // 使用幻灯片编号作为"页码"
                );
                
                // 如果幻灯片没有段落，创建一个整幻灯片块
                if (slideChunks.isEmpty() && !slide.getContent().trim().isEmpty()) {
                    TextChunk slideChunk = new TextChunk(
                        slide.getContent(),
                        chunks.size(),
                        0,
                        slide.getContent().length(),
                        filePath,
                        "slide",
                        slide.getSlideNumber()
                    );
                    slideChunk.addMetadata("slide_number", String.valueOf(slide.getSlideNumber()));
                    chunks.add(slideChunk);
                } else {
                    chunks.addAll(slideChunks);
                }
            }
            
        } else {
            // 其他格式：使用通用方法（可能触发 XPathFactory 问题）
            try {
                String text = parserService.extractText(filePath);
                tree = chunkBySection(text, filePath, 0);
                
                if (tree != null && tree.getChildren().size() > 1) {
                    chunks = extractChunksFromTree(tree);
                } else {
                    chunks = chunkByParagraph(text, filePath, 0);
                }
            } catch (Exception e) {
                // 如果提取失败，创建错误文本块
                chunks = new ArrayList<>();
                TextChunk errorChunk = new TextChunk(
                    "[文档提取失败: " + e.getMessage() + "]",
                    0, 0, 0, filePath, "error", 0
                );
                chunks.add(errorChunk);
            }
        }
        
        // 建立索引
        documentIndex.put(filePath, metadata);
        chunkIndex.put(filePath, chunks);
        if (tree != null) {
            treeIndex.put(filePath, tree);
        }
        
        metadata.setTotalChunks(chunks.size());
        metadata.setProcessed(true);
        metadata.setProcessedDate(new Date());
        
        return metadata;
    }
    
    /**
     * 从树中提取所有文本块
     */
    private List<TextChunk> extractChunksFromTree(ChunkTreeNode root) {
        List<TextChunk> chunks = new ArrayList<>();
        extractChunksFromNode(root, chunks, 0);
        return chunks;
    }
    
    private void extractChunksFromNode(ChunkTreeNode node, List<TextChunk> chunks, int index) {
        if (node.getContent() != null && !node.getContent().isEmpty()) {
            TextChunk chunk = new TextChunk(
                node.getContent(),
                index,
                node.getStartPosition(),
                node.getEndPosition(),
                node.getSourceFile(),
                "section",
                node.getPageNumber()
            );
            chunk.addMetadata("section_title", node.getTitle());
            chunks.add(chunk);
        }
        
        for (ChunkTreeNode child : node.getChildren()) {
            extractChunksFromNode(child, chunks, chunks.size());
        }
    }
    
    /**
     * 检索相关文本块（用于RAG）
     * 
     * @param query 查询文本
     * @param maxResults 最大返回结果数
     * @return 相关文本块列表
     */
    public List<TextChunk> searchChunks(String query, int maxResults) {
        List<TextChunk> results = new ArrayList<>();
        
        // 简单的关键词匹配搜索
        String lowerQuery = query.toLowerCase();
        
        for (List<TextChunk> chunks : chunkIndex.values()) {
            for (TextChunk chunk : chunks) {
                if (chunk.getContent().toLowerCase().contains(lowerQuery)) {
                    results.add(chunk);
                }
            }
        }
        
        // 按相关性排序（简单实现：匹配次数）
        results.sort((a, b) -> {
            int countA = countMatches(a.getContent().toLowerCase(), lowerQuery);
            int countB = countMatches(b.getContent().toLowerCase(), lowerQuery);
            return Integer.compare(countB, countA);
        });
        
        return results.subList(0, Math.min(maxResults, results.size()));
    }
    
    private int countMatches(String text, String query) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(query, index)) != -1) {
            count++;
            index += query.length();
        }
        return count;
    }
    
    /**
     * 获取文档的所有文本块
     * 
     * @param filePath 文件路径
     * @return 文本块列表
     */
    @Nullable
    public List<TextChunk> getChunksForDocument(String filePath) {
        return chunkIndex.get(filePath);
    }
    
    /**
     * 获取文档的章节树
     * 
     * @param filePath 文件路径
     * @return 章节树根节点
     */
    @Nullable
    public ChunkTreeNode getTreeForDocument(String filePath) {
        return treeIndex.get(filePath);
    }
    
    /**
     * 获取服务实例
     * 
     * @param project 当前项目
     * @return ChunkIndexService实例
     */
    public static ChunkIndexService getInstance(Project project) {
        return project.getService(ChunkIndexService.class);
    }
    
    /**
     * 章节信息内部类
     */
    private static class SectionInfo {
        String title;
        int position;
        int lineNumber;
        
        SectionInfo(String title, int position, int lineNumber) {
            this.title = title;
            this.position = position;
            this.lineNumber = lineNumber;
        }
    }
    
    /**
     * 文本块树节点（用于树形索引结构）
     */
    public static class ChunkTreeNode {
        private String title;
        private String content;
        private String sourceFile;
        private int pageNumber;
        private int startPosition;
        private int endPosition;
        private List<ChunkTreeNode> children;
        
        public ChunkTreeNode(String title, String sourceFile, int pageNumber) {
            this.title = title;
            this.sourceFile = sourceFile;
            this.pageNumber = pageNumber;
            this.children = new ArrayList<>();
        }
        
        public void addChild(ChunkTreeNode child) {
            children.add(child);
        }
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        public List<ChunkTreeNode> getChildren() { return children; }
    }
}

