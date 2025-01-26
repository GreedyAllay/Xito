import org.graalvm.buildtools.gradle.dsl.NativeImageOptions

val kordVersion: String by project
val gsonVersion: String by project
val slf4jSimpleVersion: String by project

plugins {
    kotlin("jvm") version "1.9.21"
    // GraalVM Native Image
    id("org.graalvm.buildtools.native") version "0.9.20"
    java
}

group = "io.github.ultreon"
version = "1.1.2"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

repositories {
    // Kord Snapshots Repository (Optional):
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/releases")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    maven("https://s01.oss.sonatype.org/content/repositories/releases")

//    google()
//    mavenCentral()

    flatDir { dir("$projectDir/libs") }
}

configurations {
    create("kotlin") {
        isCanBeResolved = true
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21")
    implementation("dev.kord:kord-core:$kordVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jSimpleVersion")

    // GraalVM JS
    implementation("org.graalvm.polyglot:polyglot:23.1.1")
    implementation("org.graalvm.polyglot:js:23.1.1")
    implementation("org.graalvm.polyglot:python:23.1.1")

    implementation("dev.ultreon.python:core:0.6-SNAPSHOT")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("org.javassist:javassist:3.30.2-GA")
    implementation("org.burningwave:core:12.65.2")
    implementation("net.bytebuddy:byte-buddy:1.14.8")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.+") {
        exclude(group = "dev.kord", module = "kord-core-voice")
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    
}

extensions.getByType<NativeImageOptions>().run {
    mainClass = "io.github.ultreon.bot.MainKt"
    buildArgs("--enable-url-protocols=http,https")
    jvmArgs("--enable-url-protocols=http,https")
}

tasks.jar {
    manifest {
        attributes(mapOf(Pair("Main-Class", "io.github.ultreon.bot.MainKt")))
    }

    from(configurations["kotlin"].map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.create("setup") {
    doLast {
        mkdir(file("build/dist"))

        copy {
            from(fileTree("build/libs"))
            into(file("build/dist"))
        }

        mkdir(file("build/dist/libs"))
        copy {
            from(configurations.runtimeClasspath)
            into(file("build/dist/libs"))
        }

        file("build/dist/start.bat").writeText("""
            @ECHO OFF
            java -Xmx4G -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI %* -cp ./${base.archivesName.get()}-${project.version}.jar;libs/* io.github.ultreon.bot.MainKt
        """.trimIndent())

        file("build/dist/start").writeText("""
            #!/bin/bash
            chmod +x ./${base.archivesName.get()}-${project.version}.jar
            java -Xmx4G -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI $@ -cp ./${base.archivesName.get()}-${project.version}.jar:libs/* io.github.ultreon.bot.MainKt
        """.trimIndent())

        file("build/dist/start.bat").setExecutable(true)
        file("build/dist/start").setExecutable(true)
    }
}

tasks.jar.get().finalizedBy("setup")

mkdir(file("run"))
