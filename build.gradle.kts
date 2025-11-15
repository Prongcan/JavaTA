plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("dev.langchain4j:langchain4j:0.34.0")

    // ✅ 排除 Tika 自带的 PDFBox 依赖
    implementation("org.apache.tika:tika-core:2.9.1") {
        exclude(group = "org.apache.pdfbox")
    }
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.1") {
        exclude(group = "org.apache.pdfbox")
    }

    implementation("org.apache.commons:commons-text:1.12.0")

    // ✅ 显式指定 PDFBox 2.0.31
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    implementation("org.apache.pdfbox:pdfbox-tools:2.0.31")
    implementation("org.apache.pdfbox:fontbox:2.0.31")

    // ✅ 直接添加 xalan（作为 implementation，不是 jarJarArchives）
    implementation("xalan:xalan:2.7.3")
    implementation("xalan:serializer:2.7.3")

    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.1")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("org.json:json:20231013")
    implementation("com.openai:openai-java:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.google.code.gson:gson:2.10.1")
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }

    // Add dependencies for RAG and other features
    implementation("dev.langchain4j:langchain4j:0.34.0")
    implementation("org.apache.tika:tika-core:2.9.1")
    implementation("org.apache.commons:commons-text:1.12.0")

    // Markdown rendering
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}