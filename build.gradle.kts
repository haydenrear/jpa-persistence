plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.no-main-class")
    id("com.hayden.docker-compose")
    id("com.hayden.observable-app")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":persistence"))
    implementation(project(":utilitymodule"))
}

