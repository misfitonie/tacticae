plugins {
    java
}

allprojects {
    group = "gg.tacticae"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
        "testImplementation"("org.assertj:assertj-core:3.26.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}