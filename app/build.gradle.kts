plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Supabase values are read from local Gradle properties or environment variables.
// Do not hard-code project credentials in source files.
val supabaseUrlValue = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .orElse("")
val supabasePublishableKeyValue = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")

val releaseStoreFilePath = providers.gradleProperty("RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("RELEASE_STORE_FILE"))
val releaseStorePasswordValue = providers.gradleProperty("RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
val releaseKeyAliasValue = providers.gradleProperty("RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
val releaseKeyPasswordValue = providers.gradleProperty("RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Ensures release artifacts use the dedicated release key."
    doLast {
        val missing = buildList {
            if (releaseStoreFilePath.orNull.isNullOrBlank()) add("RELEASE_STORE_FILE")
            if (releaseStorePasswordValue.orNull.isNullOrBlank()) add("RELEASE_STORE_PASSWORD")
            if (releaseKeyAliasValue.orNull.isNullOrBlank()) add("RELEASE_KEY_ALIAS")
            if (releaseKeyPasswordValue.orNull.isNullOrBlank()) add("RELEASE_KEY_PASSWORD")
            if (!releaseStoreFilePath.orNull.isNullOrBlank() &&
                !rootProject.file(releaseStoreFilePath.get()).isFile
            ) add("existing keystore at RELEASE_STORE_FILE")
        }
        check(missing.isEmpty()) {
            "Release signing is not configured. Supply: ${missing.joinToString()}"
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyReleaseSigning)
    }
}

android {
    namespace = "com.faheem.habits"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.faheem.habits"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrlValue.get()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabasePublishableKeyValue.get()}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFilePath.orNull?.let { rootProject.file(it) }
            storePassword = releaseStorePasswordValue.orNull
            keyAlias = releaseKeyAliasValue.orNull
            keyPassword = releaseKeyPasswordValue.orNull
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Never sign a distributable build with the publicly known debug key.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Supabase Kotlin client. Keep versions compatible with Kotlin 2.0.20.
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:2.3.13")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
