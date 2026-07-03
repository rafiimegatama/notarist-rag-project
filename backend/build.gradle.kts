plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
}

allprojects {
    group = "com.notarist"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    repositories {
        mavenCentral()
        if (System.getenv("NEXUS_URL") != null) {
            maven {
                name = "NotaristNexus"
                url = uri(System.getenv("NEXUS_URL")!!)
                credentials {
                    username = System.getenv("NEXUS_USER")
                    password = System.getenv("NEXUS_PASSWORD")
                }
                isAllowInsecureProtocol = false
            }
        }
    }

    // Use Spring Boot BOM via platform() — avoids io.spring.dependency-management
    // Kotlin DSL accessor issue when plugin is applied via apply(plugin=...) in subprojects block
    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("--enable-preview", "-parameters"))
        options.release.set(17)
    }

    tasks.withType<Test> {
        jvmArgs("--enable-preview")
        useJUnitPlatform()
    }
}
