plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.baselineprofile)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrlValue.get()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabasePublishableKeyValue.get()}\"")
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt.yml"))
        baseline = file("$rootDir/config/detekt-baseline.xml")
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
    composeCompiler {
        enableStrongSkippingMode = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.android)
    implementation(libs.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.leakcanary.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(bom)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":benchmark"))
}
