plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "unimelb.comp90018.equaltrip"

    compileSdk = 35

    defaultConfig {
        applicationId = "unimelb.comp90018.equaltrip"
        minSdk = 29
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
        // java 8
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // ===== Firebase（使用 BoM 统一版本）=====
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    //Splash
    implementation("androidx.core:core-splashscreen:1.0.1")

    //Splash
    implementation("androidx.core:core-splashscreen:1.0.1")

    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage") // Storage：已合入

    // ===== 地图 =====
    implementation("com.google.android.gms:play-services-maps:18.2.0")


    implementation ("androidx.navigation:navigation-fragment:2.7.7") // Navigation
    implementation ("androidx.navigation:navigation-ui:2.7.7") // Navigation

    // ===== 网络/序列化（ Retrofit + Gson + OkHttp）=====
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ===== Android 12+ Splash 启动页（向后兼容库）=====
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.google.android.gms:play-services-maps:18.2.0") // Google map
    //Geocoding API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Nominatim API
    implementation("com.google.firebase:firebase-storage") // Firebase storage依赖


    implementation("com.google.android.gms:play-services-maps:18.2.0") // Google map
    //Geocoding API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Nominatim API
    implementation("com.google.firebase:firebase-storage") // Firebase storage依赖

}
