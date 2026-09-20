import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is driven by the local, uncommitted keystore.properties.
// Without that file the project still builds; release output is simply unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.playbox.games"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.playbox.games"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Only the ABIs that phones (and an Apple-silicon emulator) actually use. The speech
            // library ships x86 and legacy MIPS builds too, which would only inflate the APK.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }

    androidResources {
        // The bundled speech model is already compressed, so packing it again only wastes time.
        noCompress += "zip"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

// The offline Chinese speech model (~43 MB) is a binary blob that stays out of version control,
// while the APK still bundles it so dictation works immediately after installing. This task
// fetches it into assets when a checkout does not have it yet; drop the file in manually to
// build without network access.
val voskModelAsset = layout.projectDirectory.file("src/main/assets/vosk-model-cn.zip").asFile

val prepareVoskModel by tasks.registering {
    description = "Downloads the bundled Vosk Chinese speech model when it is missing."
    outputs.file(voskModelAsset)
    onlyIf { !voskModelAsset.exists() }
    doLast {
        logger.lifecycle("Downloading the Vosk Chinese model to ${voskModelAsset.path} ...")
        voskModelAsset.parentFile.mkdirs()
        val partial = File(voskModelAsset.parentFile, "${voskModelAsset.name}.part")
        URI("https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip")
            .toURL()
            .openStream()
            .use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
        check(partial.renameTo(voskModelAsset)) { "Could not move the downloaded model into assets" }
    }
}

tasks.named("preBuild") { dependsOn(prepareVoskModel) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Offline on-device speech recognition. Many Chinese ROMs ship no system speech service, so
    // the recogniser is bundled instead of relying on android.speech.SpeechRecognizer.
    implementation("com.alphacephei:vosk-android:0.3.47")

    testImplementation("junit:junit:4.13.2")

    // On-device test that feeds synthesised Chinese number audio straight into the bundled
    // recogniser, so the model, the number grammar and the answer parser are verified together.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
