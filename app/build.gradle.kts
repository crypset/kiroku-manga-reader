plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.crypset.kiroku.mangareader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.crypset.kiroku.mangareader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // DocumentFile for folder access
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
//    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // PhotoView for zoom functionality
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
