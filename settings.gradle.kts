pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Polaroid"

include(":calculadora")
include(":buscador")

// O módulo Android precisa do Android SDK. O test.ps1 (Docker, sem SDK)
// passa -PsemAndroid para testar só os módulos Kotlin puros.
if (!providers.gradleProperty("semAndroid").isPresent) {
    include(":app")
}
