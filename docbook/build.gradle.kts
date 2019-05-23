import java.io.FileOutputStream

buildscript {
    repositories {
        maven("https://repo.spring.io/plugins-release")
    }

    dependencies {
        classpath("io.spring.gradle:docbook-reference-plugin:0.3.1")
    }
}

subprojects {
    val xslt = configurations.create("xslt")

    dependencies {
        xslt("net.sf.saxon:saxon:8.7")
    }

    apply(plugin = "docbook-reference")

    val docbookDir = "${project.buildDir}/docbook"
    val inputDir = "${project.parent?.projectDir}/src/main/reference"

    val docbookStatic by tasks.creating(Copy::class) {
        from(inputDir)
        exclude("**/*.xml")
        into(docbookDir)
    }

    val docbookSingle by tasks.creating {
        dependsOn(docbookStatic)
        inputs.dir("${project.parent?.projectDir}/src/main/reference")
        outputs.file("${docbookDir}/index.single.xml")
        doLast {
            ant.withGroovyBuilder {
                "xslt"(
                        "in" to "${project.parent?.projectDir}/src/main/reference/index.xml",
                        "out" to "${docbookDir}/index.single.xml",
                        "style" to "${project.parent?.projectDir}/src/main/reference/xsl/copy.xsl",
                        "force" to true,
                        "classpath" to xslt.asPath
                ) {
                    "param"("name" to "lang", "expression" to project.name)
                }
            }
        }
    }

    val docbookSinglePo by tasks.creating {
        dependsOn(docbookStatic)
        inputs.dir("${project.parent?.projectDir}/src/main/reference")
        outputs.file("${docbookDir}/index.l10n.xml")
        doLast {
            ant.withGroovyBuilder {
                "xslt"(
                        "in" to "${project.parent?.projectDir}/src/main/reference/index.xml",
                        "out" to "${docbookDir}/index.l10n.xml",
                        "style" to "${project.parent?.projectDir}/src/main/reference/xsl/copy.xsl",
                        "force" to true,
                        "classpath" to xslt.asPath
                ) {
                    "param"("name" to "lang", "expression" to project.name)
                    "param"("name" to "skip", "expression" to project.name)
                }
            }
        }
    }

    val fopConfig by tasks.creating(Copy::class) {
        from("${project.parent?.projectDir}/src/main/fonts") {
            include("fop-userconfig.xml")
            expand(
                    "fontBase" to "${project.parent?.projectDir}/src/main/fonts"
            )
        }
        into("${project.buildDir}/fonts")
    }

    val updatePo by tasks.creating(Exec::class) {
        dependsOn(docbookSinglePo)
        val input = "${docbookDir}/index.l10n.xml"

        inputs.file(input)
        workingDir = file(input).parentFile
        executable = "xml2po"
        args("-u", getPo(project), file(input).name)
    }

    val translate by tasks.creating(Exec::class) {
        dependsOn(docbookSingle)
        val output = "${docbookDir}/index.xml"
        val input = "${docbookDir}/index.single.xml"
        file(output).parentFile.mkdirs()

        inputs.file(input)
        inputs.file(getPo(project))
        outputs.file(output)

        executable = "xml2po"
        args("-p", getPo(project), input)
        doFirst {
            standardOutput = FileOutputStream(output)
        }
    }

    withGroovyBuilder {
        "reference" {
            setProperty("sourceDir", project.file(docbookDir))
            setProperty("pdfFilename", "${project.rootProject.name}.${project.name}.pdf")
            setProperty("epubFilename", "${project.rootProject.name}.${project.name}.epub")
            setProperty("fopUserConfig", file("${project.buildDir}/fonts/fop-userconfig.xml"))

            // Configure which files have ${} expanded
            setProperty("expandPlaceholders", "**/*.xml")
        }
    }

    val assembleDocbook by tasks.creating(Copy::class) {
        dependsOn("reference")
        destinationDir = file("${rootProject.buildDir}/doc")

        from("${project.buildDir}/reference/html") {
            into("html/${project.name}")
        }

        from("${project.buildDir}/reference/htmlsingle") {
            into("htmlsingle/${project.name}")
        }

        from("${project.buildDir}/reference/pdf") {
            into("pdf")
        }
        from("${project.buildDir}/reference/epub") {
            into("epub")
        }
    }

    rootProject.tasks.getByName("createDocs") {
        dependsOn(assembleDocbook)
    }

    afterEvaluate {
        tasks.filter { it.name.startsWith("reference") }.forEach {
            it.dependsOn(translate, fopConfig, docbookSinglePo)
        }
    }
}

fun getPo(project: Project): String {
    return "${project.parent?.projectDir}/src/main/po/${project.name}.po"
}
