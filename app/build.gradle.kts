plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.lifebench.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lifebench.app"
        minSdk = 26                                   // 最低兼容 Android 8.0
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false                   // 上线前可改为 true 并配 proguard
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 签名配置见《APK签名打包教程》：在本机 gradle.properties 配置 KEYSTORE_PWD / KEY_PWD
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true                                // 启用 Jetpack Compose
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"      // 与 Kotlin 1.9.22 + Compose BOM 2024.02.02(1.6.2) 匹配
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ===== 核心 AndroidX =====
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ===== Jetpack Compose（BOM 统一版本）=====
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // 全套图标
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ===== 导航 =====（navigation-compose 不在 Compose BOM 内，需显式版本，与 Compose 1.6.2 匹配）
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ===== 本地存储 =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ===== 协程 =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ===== JSON 序列化（数据备份导出/导入）=====
    implementation("com.google.code.gson:gson:2.10.1")

    // ===== 网络：真实天气 API（Open-Meteo，无需 Key）=====
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
