// 根构建脚本：声明 Android / Kotlin / Compose 编译器官方插件版本（无第三方插件）
// AGP 8.5.2 与项目内置的 Gradle 8.7 兼容（AGP 8.7.x 需要 Gradle 8.9+，会额外下载发行包）
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
