plugins {
    java
    id("com.diffplug.spotless") version "8.4.0"
}

group = "com.github.isksss"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
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

    jar {
        archiveBaseName.set("FQDNGuard")
    }
}
