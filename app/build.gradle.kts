plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.carlos.karoopantallas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carlos.karoopantallas"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Clave fija incluida en el proyecto: así cada compilación se puede instalar encima de la anterior.
    // Es solo para uso personal; no la uses para publicar apps.
    signingConfigs {
        create("karoo") {
            storeFile = file("karoo.keystore")
            storePassword = "karoopantallas"
            keyAlias = "karoo"
            keyPassword = "karoopantallas"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("karoo")
            isMinifyEnabled = false
        }
    }
    buildTypes.getByName("debug") {
        signingConfig = signingConfigs.getByName("karoo")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":karoo-ext"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
}
