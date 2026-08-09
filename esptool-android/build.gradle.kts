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
        // buildPython must match `version` above (3.8), or Chaquopy ships .py instead
        // of .pyc and the device compiles esptool on first import (very slow first
        // flash). Chaquopy 15's pip also can't run on Python 3.13+ (it imports `cgi`).
        // CI runners' default python3 is fine, so only override when a local pyenv 3.8
        // exists. If your system python3 is 3.13+, `pyenv install 3.8.18`.
        // Must be 3.8 to match `version` above, or Chaquopy ships .py instead of .pyc
        // and the phone compiles esptool on the first flash. On CI the workflow makes
        // python3 itself 3.8, which Chaquopy finds on its own — this is just for here.
        val localPy = "/home/user/.pyenv/versions/3.8.18/bin/python"
        if (File(localPy).canExecute()) buildPython(localPy)

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
