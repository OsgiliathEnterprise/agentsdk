import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.9"
    id("idea")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    `java-library`
    id("org.jreleaser") version "1.15.0"
    kotlin("jvm") version "2.1.10"
    wrapper
    id("maven-publish")
}
fun Project.secret(name: String): String? =
    (findProperty(name) as String?) ?: System.getenv(name)

group = "net.osgiliath.ai"
description = "Bridge module between ACP and LangGraph/LangChain"
version = (findProperty("releaseVersion") as String?) ?: "1.0-SNAPSHOT"
tasks.wrapper {
    gradleVersion = "9.4.0"
    distributionType = Wrapper.DistributionType.BIN
}

tasks.withType<Test>().configureEach {
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
// Override Spring Boot's JUnit version to match Cucumber 7.34.2 requirements
ext {
    set("junit-jupiter.version", "5.14.2")
    set("commonmark.version", "0.27.1")
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
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Ensure compatibility with Java-only dependencies
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.10")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
        // Force JUnit Platform 5.14.2 to match Cucumber 7.34.2 requirements
        force("org.junit.platform:junit-platform-engine:1.14.2")
        force("org.junit.platform:junit-platform-commons:1.14.2")
        force("org.junit.platform:junit-platform-suite:1.14.2")
        force("org.junit.platform:junit-platform-suite-api:1.14.2")
        force("org.junit.platform:junit-platform-suite-engine:1.14.2")
        force("org.junit.platform:junit-platform-launcher:1.14.2")
        force("org.junit.jupiter:junit-jupiter:5.14.2")
        force("org.junit.jupiter:junit-jupiter-api:5.14.2")
        force("org.junit.jupiter:junit-jupiter-engine:5.14.2")
    }
}

dependencies {
   "implementation"(platform("io.cucumber:cucumber-bom:7.34.2"))
    "implementation"(platform("org.bsc.langgraph4j:langgraph4j-bom:1.8.3"))
    "implementation"(platform("dev.langchain4j:langchain4j-bom:1.11.0"))

    // ACP LangGraph LangChain Bridge (published to local Maven)
    // Version can be overridden in CI with -PbridgeVersion=1.0.13 or BRIDGE_VERSION env var
    val bridgeVersion = (findProperty("bridgeVersion") as String?) ?: System.getenv("BRIDGE_VERSION") ?: "1.0-SNAPSHOT"
    implementation("net.osgiliath.ai:acp-langraph-langchain-bridge:$bridgeVersion")

    // Official ACP Kotlin SDK from JetBrains
    // Provides built-in protocol handling, STDIO transport, and session management
    implementation("com.agentclientprotocol:acp:0.15.3")

    // Kotlin stdlib (required by ACP SDK)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")

    // LangChain4j Backend (Agent Orchestrator)
    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter")
    implementation("dev.langchain4j:langchain4j-open-ai-spring-boot-starter")
    implementation("dev.langchain4j:langchain4j-http-client-jdk")
    implementation("dev.langchain4j:langchain4j-mcp")
    implementation("dev.langchain4j:langchain4j-document-parser-markdown")

    // LangGraph4j (Agent State Management)
    implementation("org.bsc.langgraph4j:langgraph4j-core")
    implementation("org.bsc.langgraph4j:langgraph4j-langchain4j")

    // CommonMark markdown parsing library with extensions
    implementation("org.commonmark:commonmark:${property("commonmark.version")}")
    implementation("org.commonmark:commonmark-ext-task-list-items:${property("commonmark.version")}")
    implementation("org.commonmark:commonmark-ext-yaml-front-matter:${property("commonmark.version")}")
    implementation("org.commonmark:commonmark-ext-autolink:${property("commonmark.version")}")
    implementation("org.commonmark:commonmark-ext-gfm-tables:${property("commonmark.version")}")
    implementation("org.commonmark:commonmark-ext-ins:${property("commonmark.version")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // Exclude Spring Boot's JUnit Platform version management
        exclude(group = "org.junit.platform")
    }


    // Import JUnit BOM AFTER Spring Boot to override its version management
    testImplementation(platform("org.junit:junit-bom:5.14.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // Testing utilities
    testImplementation("org.awaitility:awaitility:4.2.2")

    // Cucumber/Gherkin BDD Testing
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
    // testImplementation("org.testcontainers:testcontainers-ollama:2.0.2")
    // testImplementation("dev.langchain4j:langchain4j-ollama-spring-boot-starter")
    testImplementation("dev.langchain4j:langchain4j-open-ai-official")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    standardInput = System.`in`
}
// This module is published as a library, not an executable Spring Boot app.
tasks.named("bootJar") {
    enabled = false
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
