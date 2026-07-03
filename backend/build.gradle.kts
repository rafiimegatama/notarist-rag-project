plugins {
    java
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

allprojects {
    group = "com.notarist"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

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

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.5")
        }
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
