plugins {
    alias(libs.plugins.android.application)
//如果是想不使用firebase那么运行时报错先注释下面这一行
//如果要联动firebase建议使用firebase官方使用的id格式加json文件
//报错的原因是因为没用放json文件进去
    alias(libs.plugins.google.services) // Jinglin 8.18.2025
}

android {
    namespace = "unimelb.comp90018.equaltrip"
    compileSdk = 34

    defaultConfig {
        applicationId = "unimelb.comp90018.equaltrip"
        minSdk = 33
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    implementation("com.google.android.material:material:1.12.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

android { buildFeatures { viewBinding = true } }
