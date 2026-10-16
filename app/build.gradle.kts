/* 本文件配置 DailyInterest Android 应用模块及其原生 Compose 依赖。 */
import java.util.Properties

/** 读取本机签名信息，避免将密钥密码提交到版本控制。 */
val signingProperties = Properties().apply {
    rootProject.file("local.properties").inputStream().use(::load)
}

plugins {
    // Android 应用模块插件。
    id("com.android.application")
    // Jetpack Compose 编译支持。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dailyinterest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dailyinterest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        // 启用原生 Compose 界面。
        compose = true
    }

    compileOptions {
        // 统一使用 Java 17 字节码。
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // 使用项目根目录已有的 DailyInterest_key 生成可安装的 Release APK。
        create("release") {
            storeFile = rootProject.file(
                signingProperties.getProperty("release.storeFile", "DailyInterest_key"),
            )
            storePassword = signingProperties.getProperty("release.storePassword")
                ?: error("缺少 release.storePassword")
            keyAlias = signingProperties.getProperty("release.keyAlias")
                ?: error("缺少 release.keyAlias")
            keyPassword = signingProperties.getProperty("release.keyPassword")
                ?: error("缺少 release.keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            // Release 构建使用本机配置的正式签名。
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    // Compose 组件版本基线。
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    // Activity 与 Compose 的原生连接层。
    implementation("androidx.activity:activity-compose:1.10.0")
    // Compose 基础界面能力。
    implementation("androidx.compose.ui:ui")
    // Compose 基础交互与滚动能力。
    implementation("androidx.compose.foundation:foundation")
    // Compose 行列和权重布局能力。
    implementation("androidx.compose.foundation:foundation-layout")
    // Compose 预览支持。
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 原生组件。
    implementation("androidx.compose.material3:material3")
    // Material 扩展图标组件，提供日历与月份切换图标。
    implementation("androidx.compose.material:material-icons-extended")
    // 调试阶段的 Compose 工具支持。
    debugImplementation("androidx.compose.ui:ui-tooling")
}
