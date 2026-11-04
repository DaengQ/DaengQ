import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// TMAP appKey는 git에 올라가지 않는 local.properties의 TMAP_APP_KEY에 둔다
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.example.pawgo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pawgo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMAP_APP_KEY", "\"${localProperties.getProperty("TMAP_APP_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // TMAP 지도 SDK(V3.8): 공식 배포 zip의 aar와, 공식 샘플의 flatbuffers 의존성
    implementation(files("libs/tmap-sdk-3.8.aar", "libs/vsm-tmap-sdk-v2-eaa-2.0.14.aar"))
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    // 강아지 소리 분류: MediaPipe Audio Classifier와 assets의 YAMNet 모델
    implementation("com.google.mediapipe:tasks-audio:1.2.2")
}