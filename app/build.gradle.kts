plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services) apply false
}

// O arquivo pessoal do Firebase fica fora do GitHub.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.proflucas.aula7"
    compileSdk { version = release(36) }

    defaultConfig {
        applicationId = "com.proflucas.aula7"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.firebase.database)
}
