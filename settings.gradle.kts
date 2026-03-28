pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.spring.io/release")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/release")
    }
}

rootProject.name = "agent-sdk"

val bridgeDir = file("../acp-langraph-langchain-bridge")
if (gradle.parent == null && bridgeDir.exists()) {
    includeBuild(bridgeDir) {
        dependencySubstitution {
            substitute(module("net.osgiliath.ai:acp-langraph-langchain-bridge")).using(project(":"))
        }
    }
}

