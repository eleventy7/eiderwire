plugins {
    `java-library`
    checkstyle
    antlr
}

repositories {
    mavenCentral()
}

configurations[JavaPlugin.API_CONFIGURATION_NAME].let { apiConfiguration ->
    apiConfiguration.setExtendsFrom(apiConfiguration.extendsFrom.filter { it.name != "antlr" })
}

dependencies {
    checkstyle(libs.checkstyle)

    implementation(libs.antlrRuntime)
    implementation(libs.picocli)
    implementation(project(":eider-internals"))
    implementation(project(":eider-java-writer"))

    testImplementation(libs.jupiterApi)
    testRuntimeOnly(libs.jupiterEngine)

    antlr(libs.antlr)
}

tasks {
    generateGrammarSource {
        arguments = arguments + listOf("-no-listener",
        "-visitor",
        "-Werror",
        "-lib", "src/main/antlr")
    }
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
