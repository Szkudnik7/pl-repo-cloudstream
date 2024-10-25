plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.10")
        classpath("com.codingfeline.buildkonfig:buildkonfig-gradle-plugin:0.15.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

cloudstream {
    language = "pl"
    authors = listOf("AuraMc")
    tvTypes = listOf(TvType.Movie, TvType.TvSeries)
    iconUrl = "https://www.google.com/s2/favicons?domain=cda-hd.cc&sz=%size%"
}

dependencies {
    implementation("com.lagradost.cloudstream3:cloudstream-core:1.0.0") // Upewnij się, że masz tę wersję
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0") // Jackson
    implementation("org.jsoup:jsoup:1.14.3") // Jsoup
}
