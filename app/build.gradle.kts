import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dagger.hilt)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.schotech.videoapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.schotech.videoapp"
        minSdk = 24
        targetSdk = 30
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

    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    kapt {
        correctErrorTypes = true
    }

}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    // Dimensions Dependency
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.androidx.sdpDataKtx)
    implementation(libs.androidx.sspDataKtx)
    implementation(libs.hilt.android)
    implementation(libs.firebase.dataconnect)
    kapt(libs.hilt.compiler)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.itextpdf:itextpdf:5.5.13.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.0.1")
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3")
// ExoPlayer
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // implementation(libs.arthenica.mobile.ffmpeg.full.gpl)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}