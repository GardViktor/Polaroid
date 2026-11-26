import java.util.Properties

// AGP 9 já inclui suporte a Kotlin (não precisa do plugin kotlin("android"))
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Chave de assinatura do release: fica FORA do repositório (~/.polaroid/keystore.properties).
// Sem o arquivo, o release é gerado sem assinatura (não instala, mas o build não quebra).
val arquivoChave = File(System.getProperty("user.home"), ".polaroid/keystore.properties")
val propriedadesChave = Properties().apply {
    if (arquivoChave.exists()) arquivoChave.inputStream().use { load(it) }
}

android {
    namespace = "com.polaroid.app"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.polaroid.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (arquivoChave.exists()) {
            create("release") {
                storeFile = file(propriedadesChave.getProperty("storeFile"))
                storePassword = propriedadesChave.getProperty("storePassword")
                keyAlias = propriedadesChave.getProperty("keyAlias")
                keyPassword = propriedadesChave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug e release instalam lado a lado no celular
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8: remove código não usado e otimiza (app mais rápido e menor)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":calculadora"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")

    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
