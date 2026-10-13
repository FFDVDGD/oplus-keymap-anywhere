buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

layout.buildDirectory.set(layout.projectDirectory.dir(".amp/in/gradle-build"))

android {
    namespace = "io.github.ffdvdgd.opluskeymap"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ffdvdgd.opluskeymap"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("local") {
            storeFile = file(".amp/in/development.jks")
            storePassword = "android"
            keyAlias = "gamehub"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("local")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("io.github.libxposed:interface:102.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
}
