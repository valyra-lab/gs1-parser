import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
    signing
}

val generateGs1Catalog by tasks.registering(GenerateGs1CatalogTask::class) {
    group = "code generation"
    description = "Generate GS1 catalog from official JSON-LD"
    datasetUrl.set("https://ref.gs1.org/ai/GS1_Application_Identifiers.jsonld")
    chunkSize.set(40)
    outputDir.set(layout.buildDirectory.dir("generated/gs1/src/commonMain/kotlin"))
}

kotlin {
    jvm()

    explicitApi()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateGs1Catalog.flatMap { it.outputDir })
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateGs1Catalog)
}

dokka {
    moduleName.set(project.findProperty("POM_NAME") as String)

    dokkaPublications.named("html") {
        outputDirectory.set(layout.buildDirectory.dir("docs/html"))
        includes.from("../README.md")

        pluginsConfiguration.named<DokkaHtmlPluginParameters>("html") {
            footerMessage.set("© 2026 Cyril Ponce")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(project.findProperty("POM_NAME") as String)
        description.set(project.findProperty("POM_DESCRIPTION") as String)
        url.set(project.findProperty("POM_URL") as String)

        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("cyril-ponce")
                name.set("Cyril Ponce")
                url.set("https://github.com/cyril-ponce/")
            }
        }

        issueManagement {
            system.set("Github")
            url.set("https://github.com/valyra-lab/gs1-parser/issues")
        }

        scm {
            url.set("https://github.com/valyra-lab/gs1-parser/")
            connection.set("scm:git:git://github.com/valyra-lab/gs1-parser.git")
            developerConnection.set("scm:git:ssh://git@github.com/valyra-lab/gs1-parser.git")
        }
    }
}

tasks.named("publish").configure {
    dependsOn(generateGs1Catalog)
}

tasks.named<Test>("jvmTest") {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

signing {
    if (project.hasProperty("signingInMemoryKey")) {
        useInMemoryPgpKeys(
            project.findProperty("signingInMemoryKeyId") as String?,
            project.findProperty("signingInMemoryKey") as String?,
            project.findProperty("signingInMemoryKeyPassword") as String?
        )
        sign(publishing.publications)
    } else {
        useGpgCmd()
    }
}
