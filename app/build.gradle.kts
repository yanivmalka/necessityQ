import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase is optional: with app/google-services.json present the list is shared live between
// household members; without it the app runs in single-device mode. CI writes the file from a secret.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Signing: CI passes a keystore through environment variables (see .github/workflows/android.yml).
// Without them the committed development keystore is used so every build is installable/updatable.
val signingProps = Properties().apply {
    val local = rootProject.file("keystore/signing.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)

val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

android {
    namespace = "app.necessityq"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.necessityq"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = "0.1.${ciVersionCode ?: 0}"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("SIGNING_STORE_FILE", "storeFile") ?: "keystore/dev.keystore")
            storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword") ?: "necessityq-dev"
            keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias") ?: "necessityq-dev"
            keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword") ?: "necessityq-dev"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // google-services.json lists only the release package, so keep it for debug builds too
            if (!firebaseConfigured) applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
