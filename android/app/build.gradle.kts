plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val resolvedVersionName = (project.findProperty("appVersionName") as String?) ?: "0.1.7"
val resolvedVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 107

val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
val ksPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val keyAliasEnv = System.getenv("RELEASE_KEY_ALIAS")
val keyPasswordEnv = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseKeystore = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
    !ksPassword.isNullOrBlank() && !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()

android {
    namespace = "com.shaonianpai.tablet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shaonianpai.tablet"
        minSdk = 21
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = ksPassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
