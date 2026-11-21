plugins {
    `java-library`
    `maven-publish`
}

group = "dev.mantic"
version = "1.0.0"

repositories { mavenCentral() }

dependencies {
    // Herald itself has no dependencies. These are for its tests only.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { withSourcesJar() }

tasks.compileJava { options.release = 8 } // the library runs on Java 8+, like the plugins that use it
tasks.compileTestJava { options.release = 17 }
tasks.withType<JavaCompile> { options.compilerArgs.add("-Xlint:-options") }
tasks.test { useJUnitPlatform() }

publishing {
    publications.create<MavenPublication>("maven") { from(components["java"]) }
}
