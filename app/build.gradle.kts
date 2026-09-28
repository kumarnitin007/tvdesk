import java.util.Properties

plugins {
    id("com.android.application")
}

val supabaseProps = Properties()
val supabaseFile = rootProject.file("supabase.properties")
if (supabaseFile.exists()) {
    supabaseFile.inputStream().use { supabaseProps.load(it) }
}

fun supabaseField(name: String): String {
    val raw = supabaseProps.getProperty(name, "") ?: ""
    val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.tvdesk.poc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvdesk.poc"
        minSdk = 21
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
        buildConfigField("String", "SUPABASE_URL", supabaseField("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseField("SUPABASE_ANON_KEY"))
        buildConfigField("String", "OPENWEATHER_API_KEY", supabaseField("OPENWEATHER_API_KEY"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
