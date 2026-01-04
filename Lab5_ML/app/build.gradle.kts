plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.matsyshyn.lab5ml"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.matsyshyn.lab5ml"
        minSdk = 26  // Підвищено до 26 для підтримки MethodHandle.invoke (потрібно для TensorFlow Lite залежностей)
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Підтримка 16 KB сторінок для Android 15+
        // Використовуємо тільки arm64-v8a (найпоширеніша архітектура для реальних пристроїв)
        // Це допомагає уникнути проблем з вирівнюванням на x86_64
        ndk {
            abiFilters += listOf("arm64-v8a")
            // armeabi-v7a, x86 та x86_64 виключені для уникнення проблем з вирівнюванням
            // Якщо потрібна підтримка старих пристроїв, можна додати "armeabi-v7a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    aaptOptions {
        noCompress("tflite")
    }
    
    // Підтримка 16 KB сторінок для Android 15+
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // TensorFlow Lite (оновлено для підтримки 16 KB сторінок)
    // Використовуємо версію з кращою підтримкою 16 KB
    // Версія 2.15.0+ має покращену підтримку 16 KB вирівнювання
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    
    // Fragment для вкладок
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}