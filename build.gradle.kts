plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.no-main-class")
    id("com.hayden.docker-compose")
    id("com.hayden.observable-app")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"

var utilLib = ""

if (project.parent?.name?.contains("multi_agent_ide_parent") ?: false) {
    utilLib = ":multi_agent_ide_java_parent"
} else {
    utilLib = ""
}

dependencies {
    implementation(project(":persistence"))
    implementation(project("${utilLib}:utilitymodule"))
}

