plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.2"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "jp.bsb.lsp.BsbLanguageServerMain"
}

dependencies {
    implementation(project(":"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        googleJavaFormat("1.36.1")
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}
