plugins {
//    alias(libs.plugins.android.application)
    id("com.android.application")
    id("com.google.gms.google-services")
    // alias(libs.plugins.google.services) // Jinglin 8.18.2025

}
//
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    implementation("com.google.android.material:material:1.12.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))

    //Splash
    implementation("androidx.core:core-splashscreen:1.0.1")

    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")        // For authentication
    implementation("com.google.firebase:firebase-firestore")   // For database


    implementation ("androidx.navigation:navigation-fragment:2.7.7") // Navigation
    implementation ("androidx.navigation:navigation-ui:2.7.7") // Navigation



    implementation("com.google.android.gms:play-services-maps:18.2.0") // Google map
    //Geocoding API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

}


android { buildFeatures { viewBinding = true } }
