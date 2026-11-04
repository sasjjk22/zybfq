plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.tjjiangh.bofangqi"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tjjiangh.bofangqi"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // 引入官方 Media3 播放器核心库
    implementation("androidx.media3:media3-exoplayer:1.3.1")
// 引入播放器界面组件（包含暂停/进度条UI）
    implementation("androidx.media3:media3-ui:1.3.1")
// 引入 HLS (m3u8) 视频格式支持
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}