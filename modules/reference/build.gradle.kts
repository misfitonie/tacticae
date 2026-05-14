plugins {
    id("io.spring.dependency-management") version "1.1.6"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.0")
    }
}

dependencies {
    implementation(project(":modules:shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
