plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.serious_game_usil"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.serious_game_usil"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Variables de entorno para desarrollo
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "RP_ORIGIN", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "RP_ID", "\"localhost\"")
        buildConfigField("String", "RP_NAME", "\"SERIOUS_GAME\"")
    }

    buildTypes {
        debug {
            // Configuración para desarrollo local
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "RP_ORIGIN", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "RP_ID", "\"localhost\"")
            buildConfigField("String", "RP_NAME", "\"SERIOUS_GAME\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Configuración para producción
            buildConfigField("String", "API_BASE_URL", "\"https://b2hbqaai8d5tyfpljuoi-mysql.services.clever-cloud.com\"")
            buildConfigField("String", "RP_ORIGIN", "\"https://b2hbqaai8d5tyfpljuoi-mysql.services.clever-cloud.com\"")
            buildConfigField("String", "RP_ID", "\"b2hbqaai8d5tyfpljuoi-mysql.services.clever-cloud.com\"")
            buildConfigField("String", "RP_NAME", "\"SERIOUS_GAME\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true  // Importante: habilitar BuildConfig
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:fontawesome-typeface:5.9.0.2-kotlin@aar")

    // Dependencias adicionales recomendadas para API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation(libs.firebase.appdistribution.gradle)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}