plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "com.example"
version = "1.0-SNAPSHOT"

// Java Toolchain 配置
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

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

    testImplementation(kotlin("test"))
}

// ✅ 强制版本解析
configurations.all {
    resolutionStrategy {
        force("org.apache.pdfbox:pdfbox:2.0.31")
        force("org.apache.pdfbox:pdfbox-tools:2.0.31")
        force("org.apache.pdfbox:fontbox:2.0.31")
        force("org.apache.pdfbox:pdfbox-io:2.0.31")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"  // ✅ 只保留一个，移除重复的
            untilBuild = "251.*"  // ✅ 添加 until 限制
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // ✅ Java 编译 UTF-8 编码
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // ✅ Kotlin 编译配置
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // ✅ 处理重复文件
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    runIde {
        jvmArgs("--add-modules", "java.xml")
    }

    withType<Test> {
        jvmArgs("--add-modules", "java.xml")
    }
}
