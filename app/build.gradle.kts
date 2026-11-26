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
    // uploads the R8 mapping so crash reports from release builds are readable
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
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
// The release line (major.minor) lives in version.txt at the repo root; the last number is the build.
// version.txt: line 1 = the release line (major.minor), line 2 = the build number at which that line started,
// so the last number of the version name restarts from 0 whenever the line changes.
val versionFile = rootProject.file("version.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
val versionLine = versionFile[0]
val versionBase = versionFile.getOrNull(1)?.toIntOrNull() ?: 0

android {
    namespace = "app.necessityq"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.necessityq"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = "$versionLine.${if (ciVersionCode != null) maxOf(ciVersionCode - versionBase, 0) else 0}"
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

    testOptions {
        // Robolectric needs the merged resources and manifest to start the app's classes on the JVM
        unitTests.isIncludeAndroidResources = true
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
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.reorderable)
    implementation(libs.play.services.code.scanner)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest) // the empty activity the Compose tests host their content in
}
