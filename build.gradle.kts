// AGP 9 compiles Kotlin itself, and the compiler it uses is whichever kotlin-gradle-plugin lands on
// this buildscript classpath - AGP only suggests one. The library this extends is built at 2.4.20,
// and its classes cannot be read by an older compiler: without this line the build fails with
// "Module was compiled with an incompatible version of Kotlin ... expected version is 2.2.0".
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
}
