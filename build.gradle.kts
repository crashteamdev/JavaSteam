import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.dokka)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kotlinter)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.protobuf.gradle)
    id("jacoco")
    projectversiongen
    steamlanguagegen
    rpcinterfacegen
}

allprojects {
    group = "dev.crashteam"
    version = "1.5.1"
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    withSourcesJar()
}

/* Protobufs */
protobuf.protoc {
    artifact = libs.protobuf.protoc.get().toString()
}

/* Testing */
tasks.test {
    useJUnitPlatform()
    testLogging {
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
        )
    }
}

/* Test Reporting */
jacoco.toolVersion = libs.versions.jacoco.get()
tasks.jacocoTestReport {
    reports {
        xml.required = false
        html.required = false
    }
}

/* Java-Kotlin Docs */
tasks.dokkaJavadoc {
    dokkaSourceSets {
        configureEach {
            suppressGeneratedFiles.set(false) // Allow generated files to be documented.
            perPackageOption {
                // Deny most of the generated files.
                matchingRegex.set("in.dragonbra.javasteam.(protobufs|enums|generated).*")
                suppress.set(true)
            }
        }
    }
}

// Make sure Maven Publishing gets javadoc
// https://stackoverflow.com/a/71172854
lateinit var javadocArtifact: PublishArtifact
tasks {
    val dokkaHtml by getting(org.jetbrains.dokka.gradle.DokkaTask::class)

    val javadocJar by creating(Jar::class) {
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaHtml.outputDirectory)
    }

    artifacts {
        javadocArtifact = archives(javadocJar)
    }
}

/* Configuration */
configurations {
    configureEach {
        // Only allow junit 5
        exclude("junit", "junit")
        exclude("org.junit.vintage", "junit-vintage-engine")
    }
}

/* Source Sets */
sourceSets.main {
    java.srcDirs(
        // builtBy() fixes gradle warning "Execution optimizations have been disabled for task"
        files("build/generated/source/steamd/main/java").builtBy("generateSteamLanguage"),
        files("build/generated/source/javasteam/main/java").builtBy("generateProjectVersion", "generateRpcMethods")
    )
}

/* Dependencies */
tasks["lintKotlinMain"].dependsOn("formatKotlin")
tasks["check"].dependsOn("jacocoTestReport")
tasks["compileJava"].dependsOn("generateSteamLanguage", "generateProjectVersion", "generateRpcMethods")
// tasks["build"].finalizedBy(dokkaJavadocJar)

dependencies {
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.validator)
    implementation(libs.gson)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.stdib)
    implementation(libs.okHttp)
    implementation(libs.protobuf.java)
    implementation(libs.webSocket)

    testImplementation(libs.bundles.testing)
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
            artifact(javadocArtifact)
            pom {
                name.set("JavaSteam")
                description.set("Java library to interact with Valve's Steam network.")
                url.set("https://github.com/crashteamdev/JavaSteam")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://www.opensource.org/licenses/mit-license.php")
                    }
                }
                developers {
                    developer {
                        id.set("Longi")
                        name.set("Long Tran")
                        email.set("lngtrn94@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/crashteamdev/JavaSteam.git")
                    developerConnection.set("scm:git:ssh://github.com:crashteamdev/JavaSteam.git")
                    url.set("https://github.com/crashteamdev/JavaSteam/tree/master")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/crashteamdev/JavaSteam")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "Longi94"
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
