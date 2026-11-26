import java.util.Properties

// AGP 9 já inclui suporte a Kotlin (não precisa do plugin kotlin("android"))
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Esquema do banco versionado no git (necessário para migrações futuras sem perder dados)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    // Álbum: câmera, dados da foto (EXIF) e miniaturas leves
    implementation("androidx.camera:camera-core:1.7.0")
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    implementation("androidx.camera:camera-view:1.7.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")

    // Relatórios: banco de dados local (compras, vendas, perdas)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Relatórios: gráficos
    implementation("com.patrykandpatrick.vico:compose:3.3.1")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
