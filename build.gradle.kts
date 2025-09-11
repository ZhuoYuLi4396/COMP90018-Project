// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.3" apply false

    // alias(libs.plugins.google.services) apply false // To Connect Firebase which is a Google service. Jinglin 8.18.2025
}



