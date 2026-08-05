
plugins {
    id("java-library")}

val qupathVersion = providers.gradleProperty("qupathVersion").orElse("0.7.0").get()

// JavaFX version that ships with QuPath 0.7.0
val jfxVersion = "25.0.2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

repositories {
    mavenCentral()
    // QuPath's own artifacts live here too
    maven { url = uri("https://maven.scijava.org/content/repositories/releases")}
}

dependencies {
    // Point directly at QuPath's own JARs (not robust but you only build once) -- this covers QuPath, JavaFX, and everything else
    compileOnly(fileTree("/home/user/Downloads/QuPath-v0.7.0-Linux/QuPath/lib/app") {
        include("*.jar")
    })

    // Groovy compiler and logging dependencies pulled from Maven
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("org.apache.groovy:groovy:4.0.15")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25) // set an earlier version of java to be compatible with other QuPath frameworks (run on Java 25)
}