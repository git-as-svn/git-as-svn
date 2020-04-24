import org.ajoberstar.grgit.Grgit
import org.asciidoctor.gradle.jvm.AbstractAsciidoctorTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

tasks.wrapper {
    gradleVersion = "6.3"
    distributionType = Wrapper.DistributionType.ALL
}

plugins {
    id("com.github.ben-manes.versions") version "0.28.0"
    id("com.github.hierynomus.license") version "0.15.0"
    id("org.ajoberstar.grgit") version "4.0.1"
    id("org.asciidoctor.jvm.convert") version "2.3.0"
    id("org.asciidoctor.jvm.pdf") version "2.3.0"
    id("org.asciidoctor.jvm.epub") version "2.3.0"
    idea
    application
}

version = "1.25.2"

val javaVersion = JavaVersion.VERSION_1_8

idea {
    project.jdkName = javaVersion.name

    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

repositories {
    jcenter()
}

license {
    header = file("src/license/license_header.txt")
    exclude("**/*.json")
    exclude("**/*.yml")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useTestNG {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

application {
    mainClassName = "svnserver.server.Main"
}

tasks.getByName<JavaExec>("run") {
    args = listOf("-c", "$projectDir/src/test/resources/config-local.yml")
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003110725-r")
    implementation("org.tmatesoft.svnkit:svnkit:1.10.1")
    implementation("org.yaml:snakeyaml:1.26")
    implementation("com.beust:jcommander:1.78")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("org.mapdb:mapdb:3.0.8")
    implementation("com.unboundid:unboundid-ldapsdk:5.0.1")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.26.v20200117")
    implementation("org.gitlab:java-gitlab-api:4.1.1")
    implementation("org.bitbucket.b_c:jose4j:0.7.0")
    implementation("com.github.zeripath:java-gitea-api:1.7.4")

    val gitLfsJava = "0.16.0"
    implementation("ru.bozaro.gitlfs:gitlfs-pointer:$gitLfsJava")
    implementation("ru.bozaro.gitlfs:gitlfs-client:$gitLfsJava")
    implementation("ru.bozaro.gitlfs:gitlfs-server:$gitLfsJava")

    implementation("com.google.oauth-client:google-oauth-client:1.30.6")
    implementation("com.google.http-client:google-http-client-jackson2:1.34.2")
    implementation("org.jetbrains:annotations:19.0.0")
    implementation("org.slf4j:slf4j-api:1.7.30")

    val classindex = "org.atteo.classindex:classindex:3.9"
    implementation(classindex)
    annotationProcessor(classindex)

    runtimeOnly("org.apache.logging.log4j:log4j-slf4j18-impl:2.13.1")

    testImplementation("org.testcontainers:testcontainers:1.13.0")
    testImplementation("org.testng:testng:7.2.0")

    constraints {
        implementation("org.apache.httpcomponents:httpclient") {
            version {
                require("4.5.12")
                because("https://issues.apache.org/jira/browse/HTTPCLIENT-2047")
            }
        }
    }
}

tasks.jar {
    archiveFileName.set("${project.name}.jar")
    manifest {
        attributes(
                "Main-Class" to "svnserver.server.Main",
                "Class-Path" to createLauncherClassPath()
        )
    }
}

val compileDocs by tasks.registering(Copy::class) {
    group = "documentation"
    dependsOn(tasks.asciidoctor, tasks.asciidoctorEpub, tasks.asciidoctorPdf)

    from("$buildDir/docs/asciidoc") {
        into("htmlsingle")
    }
    from("$buildDir/docs/asciidocEpub")
    from("$buildDir/docs/asciidocPdf")
    from("$projectDir") {
        include("*.adoc", "LICENSE")
    }
    into(file("$buildDir/doc"))
}

tasks.asciidoctor {
    configure()

    resources {
        from("src/docs/asciidoc") {
            include("examples/**")
            include("images/**")
        }
    }
}

tasks.asciidoctorEpub {
    configure()
    ebookFormats("epub3")
}

tasks.asciidoctorPdf {
    configure()
}

fun AbstractAsciidoctorTask.configure() {
    baseDirFollowsSourceDir()

    val commitDateTime = getCommitDateTime()
    attributes(mapOf(
            "source-highlighter" to "coderay",
            "docdate" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "doctime" to commitDateTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
    ))
}

distributions {
    main {
        contents {
            into("doc") {
                from(compileDocs)
            }
            into("tools") {
                from("$projectDir/tools")
            }
        }
    }
}

tasks.processResources {
    from(sourceSets.main.get().resources.srcDirs) {
        include("**/VersionInfo.properties")

        expand(mapOf(
                "revision" to Grgit.open(mapOf("dir" to projectDir)).head().id,
                "tag" to (System.getenv("TRAVIS_TAG") ?: "")
        ))
    }
}

val debianControl by tasks.registering(Copy::class) {
    from("$projectDir/src/main/deb") {
        exclude("**/changelog")
    }
    from("$projectDir/src/main/deb") {
        include("**/changelog")

        expand(mapOf(
                "version" to project.version,
                "date" to DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(getCommitDateTime())
        ))
    }
    into(file("$buildDir/debPackage/package"))
}

val compileDeb by tasks.registering(Exec::class) {
    dependsOn(tasks.installDist, debianControl)

    workingDir = file("$buildDir/debPackage/package")
    executable = "dpkg-buildpackage"
    args("-uc", "-us")
}

val distDeb by tasks.registering(Copy::class) {
    group = "distribution"
    dependsOn(compileDeb)

    from("$buildDir/debPackage") {
        include("*.deb")
    }
    into("$buildDir/distributions")
}

tasks.assembleDist {
    dependsOn(distDeb)
}

tasks.distZip {
    archiveFileName.set("${project.name}_${project.version}.zip")
}

tasks.distTar {
    archiveFileName.set("${project.name}_${project.version}.tbz2")
    compression = Compression.BZIP2
}

fun createLauncherClassPath(): String {
    val projectArtifacts = configurations.archives.get().artifacts.map { it.file }
    val fullArtifacts = configurations.archives.get().artifacts.map { it.file } + configurations.runtimeClasspath.get().files
    val vendorJars = fullArtifacts.minus(projectArtifacts).map { it.name }
    return vendorJars.joinToString(" ")
}

fun getCommitDateTime(): ZonedDateTime {
    return Grgit.open(mapOf("dir" to projectDir)).head().dateTime
}
