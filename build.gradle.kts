import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.0"
    jacoco
}

group = "jp.bsb"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// JDK 25(クラスファイルバージョン69)のカバレッジ計測にはJava 25公式対応版が必要。
// 0.8.14でJava 25の公式対応が入った(JaCoCoのリリース履歴を参照)。
jacoco {
    toolVersion = "0.8.14"
}

application {
    mainClass = "jp.bsb.cli.BsbMain"
}

dependencies {
    implementation("com.ibm.icu:icu4j:76.1")
    implementation("com.google.re2j:re2j:1.8")
    implementation("org.tomlj:tomlj:1.1.1")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    test {
        // 既存の tests/conformance をテスト資源として読む
        resources.srcDir("tests")
    }
}

spotless {
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        googleJavaFormat("1.36.0")
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Javadoc>().configureEach {
    // 公開APIの説明本文と不正リンクは検査し、機械的な全@param列挙だけを警告対象外にする。
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.test {
    useJUnitPlatform()
    // 巨大境界ケースも、配布基準である512 MiB以内で処理できることを毎回確認する。
    maxHeapSize = "512m"

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }

    // 実装コードへの到達度(補助指標)を毎回のtestで測る。閾値によるビルド失敗はまだ導入しない。
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        // CIや他ツールからの機械可読な参照用。
        xml.required.set(true)
        // 人間がパッケージ別(frontend/analyzer/runtime/cli等)に見るための参照用。
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.processResources {
    from("tests/conformance/language-core/messages.properties") {
        into("jp/bsb/diagnostics")
    }
    from("tests/conformance/control-flow/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "control-flow-messages.properties" }
    }
    from("tests/conformance/bindings/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "bindings-messages.properties" }
    }
    from("tests/conformance/arrays/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "arrays-messages.properties" }
    }
    from("tests/conformance/numerics/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "numerics-messages.properties" }
    }
    from("tests/conformance/text-regex/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "text-regex-messages.properties" }
    }
    from("tests/conformance/host-io/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "host-io-messages.properties" }
    }
    from("tests/conformance/json/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "json-messages.properties" }
    }
    from("tests/conformance/optional-values/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "optional-values-messages.properties" }
    }
    from("tests/conformance/logical-connections/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "logical-connections-messages.properties" }
    }
    from("tests/conformance/byte-sequences/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "byte-sequences-messages.properties" }
    }
    from("tests/conformance/https/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "https-messages.properties" }
    }
    from("tests/conformance/nested-arrays/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "nested-arrays-messages.properties" }
    }
    from("tests/conformance/workspace-tables/messages.properties") {
        into("jp/bsb/diagnostics")
        rename { "workspace-tables-messages.properties" }
    }
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
}
