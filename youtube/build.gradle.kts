plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

/**
 * What git says this working tree is - the tag when HEAD sits on one, otherwise the tag plus how
 * far past it we are. Read through `providers.exec`, which the configuration cache understands.
 */
val gitDescribe: String? by lazy {
    val repository = rootDir.absolutePath
    val output = providers.exec {
        commandLine("git", "-C", repository, "describe", "--tags", "--dirty", "--always")
        isIgnoreExitValue = true
    }
    output.takeIf { it.result.get().exitValue == 0 }
        ?.standardOutput?.asText?.get()?.trim()?.ifBlank { null }
}

// JitPack passes its own -Pgroup/-Pversion and those win, so a release here is a git tag and
// nothing else - the same as in the library this extends.
group = (findProperty("group") as? String)?.takeIf { it.contains('.') }
    ?: "com.github.Dev-Husnain.MediaDownloaderYouTube"
version = (findProperty("version") as? String)?.takeIf { it != Project.DEFAULT_VERSION }
    ?: gitDescribe
    ?: "0.0.0-local"

android {
    namespace = "com.markhoor.mediadownloader.youtube"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        // The library is built at 21, and bytecode built at 21 cannot be inlined into a module
        // built lower. Under AGP 9's built-in Kotlin, targetCompatibility *is* the Kotlin jvmTarget.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Compile-only would be tidier, but a consumer needs both anyway and this states the truth:
    // nothing here works without the library it plugs into.
    api(libs.media.downloader)
    implementation(libs.kotlinx.coroutines.core)
    // GPLv3 - see README. Everything in this repository exists to keep this dependency out of the
    // library's own build.
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("youtube")
                description.set("A YouTube reader for MediaDownloaderLibrary, built on NewPipeExtractor.")
                url.set("https://github.com/Dev-Husnain/MediaDownloaderYouTube")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("Dev-Husnain")
                        name.set("Hussnain Mehdi")
                    }
                }
            }
        }
    }
}
