plugins {
    java
    checkstyle
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.AZUL)
    }
}

checkstyle {
    maxWarnings = 0
}

group = "io.eider"
version = "2.0.0-SNAPSHOT"

defaultTasks("check", "build")
