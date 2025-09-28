plugins {
    id("com.chaquo.python") version "15.0.1"
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}

android {
    namespace = "bruce.app"
    compileSdk = 34

    defaultConfig {
        minSdk = 27

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.androidx.appcompat)
}

chaquopy {
    version = "3.8"

    defaultConfig {
        pip {
            install("git+https://github.com/xCarlost/pyserial.git@b6adda109d814499a65c671ff60a888d479f3a3d")
            install("bitarray>=2.8.0")
            install("bitstring==3.1.6")
            install("esptool==4.8.1")
        }
    }
    productFlavors { }
    sourceSets { }
}
