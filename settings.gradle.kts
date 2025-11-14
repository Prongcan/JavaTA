pluginManagement {
    repositories {
        // ✅ 确保添加标准的 Gradle 插件门户
        gradlePluginPortal()
        // ✅ 确保添加 Maven Central
        mavenCentral()

        // 你的自定义仓库
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}

rootProject.name = "AIPlugin"