package io.github.rastiehaiev.utils

fun getEnv(name: String) = System.getenv(name) ?: error("Environment variable '$name' not set!")
