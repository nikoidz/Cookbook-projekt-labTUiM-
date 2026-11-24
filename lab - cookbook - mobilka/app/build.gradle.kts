import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val cloudApiUrl = providers.gradleProperty("COOKBOOK_API_URL")
    .orElse(providers.environmentVariable("COOKBOOK_API_URL"))
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?.let { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/") &&
            (uri.port == -1 || uri.port in 1..65535)) {
            "COOKBOOK_API_URL must be an HTTPS origin (for example https://cookbook.example.org)"
        }
        value.trimEnd('/')
    }

android {
    namespace = "com.example.cookbook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cookbook"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "backend"
    productFlavors {
        create("local") {
            dimension = "backend"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        create("cloud") {
            dimension = "backend"
            applicationIdSuffix = ".cloud"
            buildConfigField("String", "API_BASE_URL", "\"${cloudApiUrl.orEmpty()}\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    beforeVariants(selector().withFlavor("backend" to "local").withBuildType("release")) {
        it.enable = false
    }
    if (cloudApiUrl == null) {
        beforeVariants(selector().withFlavor("backend" to "cloud")) { it.enable = false }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
