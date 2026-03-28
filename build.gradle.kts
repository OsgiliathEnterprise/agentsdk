import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.ideaExt)
    id("idea")
    alias(libs.plugins.kotlinSerialization)
    `java-library`
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.dependencycheck)
    alias(libs.plugins.kotlinJvm)
    wrapper
    id("maven-publish")
    jacoco
}

fun Project.secret(name: String): String? =
    (findProperty(name) as String?) ?: System.getenv(name)

group = "net.osgiliath.ai"
description = "Bridge module between ACP and LangGraph/LangChain"
version = (findProperty("releaseVersion") as String?) ?: "1.0-SNAPSHOT"
tasks.wrapper {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.BIN
}

configure<JacocoPluginExtension> {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    // Attach JaCoCo agent to every test task
    configure<JacocoTaskExtension> {
        isEnabled = true
    }
    finalizedBy(tasks.named("jacocoTestReport"))

    useJUnitPlatform()

    // Add detailed test logging for debugging
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    // Enable debug output
    systemProperty("java.util.logging.config.file", "")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
// Override Spring Boot's JUnit version to match Cucumber requirements
ext {
    set("junit-jupiter.version", libs.versions.junitJupiter.get())
    set("commonmark.version", libs.versions.commonmark.get())
}

// Explicitly configure Java toolchain for this module to ensure consistency
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

// Configure Kotlin to use the same Java toolchain
kotlin {
    jvmToolchain(21)
}

// Configure Kotlin compiler options
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Ensure compatibility with Java-only dependencies
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

configurations.all {
    resolutionStrategy {
        force(libs.kotlinStdlib)
        force(libs.kotlinStdlibCommon)
        force(libs.kotlinxCoroutinesCore)
        force(libs.kotlinxCoroutinesCoreJvm)
        // Force JUnit Platform to match Cucumber requirements
        force(libs.junitPlatformSuite)
        force(libs.junitPlatformLauncher)
        force(libs.junitJupiter)
    }
}

dependencies {
    implementation(platform(libs.cucumberBom))
    implementation(platform(libs.langgraph4jBom))
    implementation(platform(libs.langchain4jBom))

    val bridgeVersion = (findProperty("bridgeVersion") as String?) ?: System.getenv("BRIDGE_VERSION") ?: "1.0-SNAPSHOT"
    implementation("net.osgiliath.ai:acp-langraph-langchain-bridge:$bridgeVersion")

    implementation(libs.acp)

    // Kotlin stdlib (required by ACP SDK)
    implementation(libs.kotlinStdlib)
    implementation(libs.kotlinStdlibCommon)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCoroutinesCoreJvm)

    // LangChain4j Backend (Agent Orchestrator)
    implementation(libs.langchain4j)
    implementation(libs.langchain4jSpringBootStarter)
    implementation(libs.langchain4jOpenAiSpringBootStarter)
    implementation("dev.langchain4j:langchain4j-anthropic")
    implementation("dev.langchain4j:langchain4j-ollama")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini")
    implementation("dev.langchain4j:langchain4j-mistral-ai")
    implementation(libs.langchain4jHttpClientJdk)
    implementation(libs.langchain4jMcp)
    implementation(libs.langchain4jDocumentParserMarkdown)
    implementation(libs.langchain4jMicrometerMetrics)
    implementation(libs.langchain4jObservation)

    // LangGraph4j (Agent State Management)
    implementation(libs.langgraph4jCore)
    implementation(libs.langgraph4jLangchain4j)

    implementation(libs.commonmark)
    implementation(libs.commonmarkExtTaskListItems)
    implementation(libs.commonmarkExtYamlFrontMatter)
    implementation(libs.commonmarkExtAutolink)
    implementation(libs.commonmarkExtGfmTables)
    implementation(libs.commonmarkExtIns)

    implementation(libs.springBootStarterActuator)
    implementation(libs.springBootStarterJson)
    testImplementation(libs.springBootStarterTest) {
        exclude(group = "org.junit.platform")
    }

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testImplementation(libs.junitJupiterApi)
    testImplementation(libs.awaitility)

    // Cucumber/Gherkin BDD Testing
    testImplementation(libs.cucumberCore)
    testImplementation(libs.cucumberJava)
    testImplementation(libs.cucumberSpring)
    testImplementation(libs.cucumberJunitPlatformEngine)
    testImplementation(libs.junitPlatformSuite)
    testImplementation(libs.langchain4jOpenAiOfficial)
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    standardInput = System.`in`
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    // This module is published as a library; avoid resolving runtimeClasspath via bootJar in CI.
    enabled = false
    classpath = files()
}

tasks.named<Jar>("jar") {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("agent-sdk")
                description.set("Agent creation helper library for ACP-LangGraph-LangChain integration")
                url.set("https://github.com/OsgiliathEnterprise/agent-sdk")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("charliemordant")
                        name.set("Charlie Mordant")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/OsgiliathEnterprise/agent-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/OsgiliathEnterprise/agent-sdk.git")
                    url.set("https://github.com/OsgiliathEnterprise/agent-sdk")
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

jreleaser {
    configFile.set(file("jreleaser.yml"))
}

sonar {
    properties {
        secret("SONAR_HOST_URL")?.let { property("sonar.host.url", it) }
        secret("SONAR_TOKEN")?.let { property("sonar.token", it) }
        secret("SONAR_ORGANIZATION")?.let { property("sonar.organization", it) }
        secret("SONAR_PROJECT_KEY")?.let { property("sonar.projectKey", it) }
        secret("SONAR_PROJECT_NAME")?.let { property("sonar.projectName", it) }
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        )
        property(
            "sonar.java.binaries",
            "${layout.buildDirectory.get()}/classes/java/main,${layout.buildDirectory.get()}/classes/kotlin/main"
        )
        property(
            "sonar.java.test.binaries",
            "${layout.buildDirectory.get()}/classes/java/test"
        )
        property(
            "sonar.exclusions",
            "src/test/resources/dataset/**"
        )
    }
}
