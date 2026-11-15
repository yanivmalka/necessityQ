plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin: no Android, no Firebase. All business rules live here and are unit-tested on the JVM.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
