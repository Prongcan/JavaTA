pluginManagement {
    repositories {
        gradlePluginPortal()  // Kotlin 插件在这里
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()  // 添加备选仓库
    }
}

rootProject.name = "AIPlugin"