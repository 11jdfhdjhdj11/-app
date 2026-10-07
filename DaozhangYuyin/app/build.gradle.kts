plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.daozhang.yuyin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daozhang.yuyin"
        minSdk = 26          // Android 8.0
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // 正式版签名：从环境变量读取（GitHub Actions 里来自仓库 Secrets），未配置时不签名
    val storeFilePath = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (!storeFilePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // 两个版本：lite = 标准版（系统语音，体积小）；full = 音色版（内置 Kokoro 100 个中文离线音色）
    flavorDimensions += "voice"
    productFlavors {
        create("lite") {
            dimension = "voice"
            versionNameSuffix = "-lite"
        }
        create("full") {
            dimension = "voice"
            // 模型较大，只打包手机常见架构
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    androidResources {
        noCompress += listOf("onnx", "bin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 离线语音引擎，仅音色版
    "fullImplementation"("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")

    testImplementation("junit:junit:4.13.2")
}

// 音色版需要先下载模型到 src/full/assets/kokoro（见 scripts/fetch-kokoro.sh），缺失时给出明确提示
val kokoroModel = file("src/full/assets/kokoro/model.int8.onnx")
tasks.matching { it.name.startsWith("preFull") && it.name.endsWith("Build") }.configureEach {
    doFirst {
        if (!kokoroModel.exists()) {
            throw GradleException("缺少 Kokoro 模型。请先在项目根目录运行：bash scripts/fetch-kokoro.sh")
        }
    }
}
