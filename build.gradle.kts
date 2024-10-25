plugins {
    id("com.android.application") version "8.2.2" apply true
    kotlin("android")
    kotlin("kapt") // Dodaj, jeśli korzystasz z Kapt
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
    implementation("com.lagradost.cloudstream3:cloudstream-core:1.0.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("org.jsoup:jsoup:1.14.3")
}
