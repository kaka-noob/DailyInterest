/* 本文件声明整个 Android 工程共用的 Gradle 插件版本。 */
plugins {
    // Android 应用插件。
    id("com.android.application") version "9.0.1" apply false
    // Jetpack Compose 编译插件。
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
