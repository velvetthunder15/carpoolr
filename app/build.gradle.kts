import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.carpoolr.app"
    compileSdk {
        version = release(36)
    }
    buildToolsVersion = "36.1.0"

    signingConfigs {
        getByName("debug") {
            val fixedKeystore = file("debug.keystore")
            if (fixedKeystore.exists()) {
                storeFile = fixedKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.carpoolr.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        androidResources.localeFilters += arrayOf("en")

        val gsFile = file("google-services.json")
        if (gsFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val root = JsonSlurper().parse(gsFile) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val projectInfo = root["project_info"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val clients = root["client"] as? List<Map<String, Any?>> ?: emptyList()
            val matchedClient = clients.firstOrNull { c ->
                @Suppress("UNCHECKED_CAST")
                val info = c["client_info"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val androidInfo = info?.get("android_client_info") as? Map<String, Any?>
                androidInfo?.get("package_name") == "com.carpoolr.app"
            } ?: clients.firstOrNull()

            @Suppress("UNCHECKED_CAST")
            val clientInfo = matchedClient?.get("client_info") as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val apiKeys = matchedClient?.get("api_key") as? List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val oauthClients = matchedClient?.get("oauth_client") as? List<Map<String, Any?>>

            val appId = clientInfo?.get("mobilesdk_app_id")?.toString().orEmpty()
            val apiKey = apiKeys?.firstOrNull()?.get("current_key")?.toString().orEmpty()
            val projectId = projectInfo["project_id"]?.toString().orEmpty()
            val senderId = projectInfo["project_number"]?.toString().orEmpty()
            val bucket = projectInfo["storage_bucket"]?.toString().orEmpty()
            val webClientId = oauthClients?.firstOrNull { it["client_type"]?.toString() == "3" }
                ?.get("client_id")?.toString().orEmpty()

            if (appId.isNotEmpty() && apiKey.isNotEmpty()) {
                resValue("string", "google_app_id", appId)
                resValue("string", "google_api_key", apiKey)
                resValue("string", "google_crash_reporting_api_key", apiKey)
                if (projectId.isNotEmpty()) resValue("string", "project_id", projectId)
                if (senderId.isNotEmpty()) resValue("string", "gcm_defaultSenderId", senderId)
                if (bucket.isNotEmpty()) resValue("string", "google_storage_bucket", bucket)
                if (webClientId.isNotEmpty()) resValue("string", "default_web_client_id", webClientId)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the deterministic debug key so APKs update in place and match Firebase SHA-1.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    packaging {
        resources {
            excludes += arrayOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.androidx.webkit)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.mlkit.barcode)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
}
