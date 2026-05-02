plugins {
    java
    id("com.diffplug.spotless") version "8.4.0"
}

group = "net.isksss.mc.fqdnguard"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

spotless {
    java {
        googleJavaFormat()
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
    }

    jar {
        archiveBaseName.set("FQDNGuard")
    }
}
