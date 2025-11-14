# PDF/PPTX 文档解析与 Markdown 导出插件

一个 IntelliJ IDEA 插件，用于快速解析 PDF 和 PPTX 文件，并将内容导出为 Markdown 格式。支持文本提取、内容分块、索引和搜索功能。

## ✨ 主要功能

### 📄 文档解析
- ✅ **PDF 文本提取** - 按页面级别提取 PDF 文本内容
- ✅ **PPTX 幻灯片提取** - 按幻灯片级别提取演示文稿内容
- ✅ **自动文件验证** - 验证文件完整性和格式有效性

### 📝 内容处理
- ✅ **文本分块** - 智能分割文本为可管理的块
- ✅ **内容索引** - 建立索引以支持快速搜索
- ✅ **关键词搜索** - 搜索相关的文本块

### 💾 导出功能
- ✅ **Markdown 导出** - 将提取的内容导出为标准 Markdown 格式
- ✅ **结构化输出** - 保持文档结构（标题、分段等）

### 使用方法

#### 方式 1：通过菜单
1. 打开 IntelliJ IDEA
2. 点击菜单 **Tools → Test PDF/PPT Parsing**
3. 在弹出的对话框中输入文件完整路径
4. 点击 OK 开始解析

#### 方式 2：输入文件路径
支持的文件格式：
- `.pdf` - PDF 文档
- `.pptx` / `.ppt` - PowerPoint 演示文稿

**示例路径**：
C:\Users\YourName\Documents\report.pdf
F:\Files\presentation.pptx
/home/user/documents/slides.pptx

#### 输出位置
解析完成后，Markdown 文件会保存到：
[项目路径]/output/[原文件名].md

## 🏗️ 项目结构

PdfConvert/

├── src/

│ └── main/

│ ├── java/com/example/aiplugin/

│ │ ├── action/

│ │ │ ├── TestPdfParsingAction.java # 测试入口

│ │ │ └── AskAboutCodeAction.java # 代码问答

│ │ ├── service/

│ │ │ ├── DocumentParserService.java # 文档解析核心

│ │ │ ├── ChunkIndexService.java # 分块索引服务

│ │ │ ├── MarkdownExportService.java # Markdown 导出

│ │ │ └── model/ # 数据模型

│ │ └── ui/

│ │ └── ChatToolWindowFactory.java # UI 组件

│ └── resources/

│ └── META-INF/plugin.xml # 插件配置

├── build.gradle.kts # Gradle 构建配置

├── settings.gradle.kts # Gradle 设置

└── README.md # 本文件

## 📚 核心服务说明

### DocumentParserService
负责从 PDF 和 PPTX 文件中提取文本。

**关键方法**：
- `extractPdfByPages(String filePath)` - 按页面提取 PDF
- `extractPptBySlides(String filePath)` - 按幻灯片提取 PPTX

**特点**：
- 绕过 XPath 依赖问题
- 支持异常恢复，损坏页面会跳过继续处理

### ChunkIndexService
对提取的文本进行分块和索引。

**关键方法**：
- `processDocument(String filePath, DocumentParserService parserService)` - 处理文档
- `searchChunks(String query, int maxResults)` - 搜索文本块

### MarkdownExportService
将提取的内容导出为 Markdown 格式。

**关键方法**：
- `exportPdfPagesToMarkdown(List<PageContent> pages, String outputPath)` - 导出 PDF
- `exportPptSlidesToMarkdown(List<SlideContent> slides, String outputPath)` - 导出 PPTX

## 🔧 技术栈（注意版本）

- **Java 17** - 编程语言
- **Kotlin 2.1.0** - JVM 语言
- **IntelliJ Platform SDK** - 插件框架
- **Apache PDFBox 2.0.31** - PDF 处理
- **Apache POI** - Office 文档处理
- **Apache Tika** - 文档格式检测
- **Gradle 8.8** - 构建工具

- ## ⚠️ 已知限制

1. **PDF 图像处理** - 由于 XPath 依赖问题，无法提取嵌入的 JPEG 图像
   - 但文本提取完全正常工作

2. **PPTX 格式** - 使用直接 ZIP 解析，可能无法保留所有格式
   - 但文本内容提取完整

3. **文件路径** - 不支持相对路径，必须使用完整绝对路径

## 🐛 故障排除

### 问题：文件无法读取
**解决**：
- 确认文件路径正确（使用完整绝对路径）
- 确认文件不被其他程序占用
- 检查文件是否损坏（用 Adobe Reader 或 Office 打开验证）

### 问题：XPath 相关错误
**解决**：
- 这是已知问题，已通过异常捕获处理
- 如果仍有问题，确保已添加 `xalan:xalan:2.7.3` 依赖

### 问题：输出文件找不到
**解决**：
- 检查 `output/` 目录是否已创建
- 查看控制台输出的完整输出路径
