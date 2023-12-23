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
    implementation(project(":eider-java-writer"))
}
