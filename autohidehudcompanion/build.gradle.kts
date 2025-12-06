plugins {
    java
    application
}

group = "com.krookedlilly.autohidehudcompanion"
version = "1.0.0"

repositories {
    mavenCentral()
}


dependencies {
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("com.krookedlilly.autohidehudcompanion.AutoHideHUDCompanion")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Create a fat JAR with all dependencies
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Creates a fat JAR with all dependencies"
    archiveBaseName.set("AutoHideHUDCompanion")  // Base name
    archiveVersion.set("1.0.3")
//    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.krookedlilly.autohidehudcompanion.AutoHideHUDCompanion"
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}