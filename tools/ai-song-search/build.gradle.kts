plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

application {
    mainClass.set("com.playlists.tools.AiSongSearchKt")
    applicationName = "ai-song-search"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val appJava = rootProject.layout.projectDirectory.dir("app/src/main/java")
val sharedSrc = layout.buildDirectory.dir("generated/shared-app-src")
val sharedRelPaths = listOf(
    "com/playlists/app/ai/AiPrompts.kt",
    "com/playlists/app/ai/AiJsonHelper.kt",
    "com/playlists/app/ai/ChartIntent.kt",
    "com/playlists/app/ai/ChartDraft.kt",
    "com/playlists/app/ai/ChartAssistantService.kt",
    "com/playlists/app/ai/ChartTextParser.kt",
    "com/playlists/app/find/WebSearchService.kt",
    "com/playlists/app/find/SearchResult.kt",
    "com/playlists/app/render/ChartPdfLayout.kt",
    "com/playlists/app/render/PdfPageSpec.kt",
)

val syncSharedAppSources = tasks.register<Sync>("syncSharedAppSources") {
    into(sharedSrc)
    from(appJava) {
        sharedRelPaths.forEach { include(it) }
    }
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir(syncSharedAppSources)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
}
