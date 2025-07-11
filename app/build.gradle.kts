plugins {
    id("buildlogic.kotlin-application-conventions")
    kotlin("plugin.serialization") version "1.9.23"
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":lib"))
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("ch.qos.logback:logback-classic:1.5.13")
}

application {
    mainClass = "org.example.app.AppKt"
}
