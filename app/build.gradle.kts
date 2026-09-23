plugins {
    id("com.android.application")
}

android {
    namespace = "com.elxvro.warstate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elxvro.warstate"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
