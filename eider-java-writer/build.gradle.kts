plugins {
    `java-library`
    checkstyle
}

repositories {
    mavenCentral()
}

dependencies {
    checkstyle(libs.checkstyle)
    implementation(project(":eider-internals"))
    implementation(libs.logback)
    implementation(libs.slf4j)
    implementation(libs.agrona)
    implementation(libs.javapoet)
    testImplementation(libs.jupiterApi)
    testRuntimeOnly(libs.jupiterEngine)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter(libs.versions.junitVersion.get())
        }
    }
}
