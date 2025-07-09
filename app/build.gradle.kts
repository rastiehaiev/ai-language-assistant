plugins {
    id("buildlogic.kotlin-application-conventions")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":lib"))
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")

    implementation("ch.qos.logback:logback-classic:1.5.13")
}

application {
    mainClass = "org.example.app.AppKt"
}
