plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glassbox.rotaportal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glassbox.rotaportal"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        val opsApiBase = providers.gradleProperty("OPS_API_BASE").orElse("https://api.opsgenie.com")
        val tokenDocsUrl = providers.gradleProperty("TOKEN_DOCS_URL").orElse("")
        val scheduleName = providers.gradleProperty("OPSGENIE_SCHEDULE_NAME").orElse("GB-INFRA-Schedule")
        val rotationName = providers.gradleProperty("OPSGENIE_ROTATION_NAME").orElse("normal")
        buildConfigField("String", "OPS_API_BASE", opsApiBase.get().trimEnd('/').toBuildConfigString())
        buildConfigField("String", "TOKEN_DOCS_URL", tokenDocsUrl.get().toBuildConfigString())
        buildConfigField("String", "OPSGENIE_SCHEDULE_NAME", scheduleName.get().toBuildConfigString())
        buildConfigField("String", "OPSGENIE_ROTATION_NAME", rotationName.get().toBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

fun String.toBuildConfigString(): String {
    val escaped = replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.compose.activity)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.ktor.client.okhttp)

    debugImplementation(libs.compose.ui.tooling)
}
