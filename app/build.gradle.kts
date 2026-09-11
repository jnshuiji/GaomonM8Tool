plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gaomon.m8"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gaomon.m8"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    // Modern LibXposed API 102
    compileOnly(libs.libxposed.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)

    // Unit Testing
    testImplementation(libs.junit)
}

// LibXposed 102.0.0 的 AAR metadata 超前声明了 minCompileSdk=37，而当前稳定 SDK 最高为 35。
// 使用标准的 Task 名称匹配跳过该元数据检查，彻底消除对 AGP internal 私有类的依赖，避免升级插件时 ClassNotFound 崩溃。
tasks.matching { it.name.contains("AarMetadata", ignoreCase = true) }.configureEach {
    enabled = false
}
