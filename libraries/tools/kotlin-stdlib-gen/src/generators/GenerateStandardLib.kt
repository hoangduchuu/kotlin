package generators

import java.io.*
import templates.*
import templates.Family.*

private val COMMON_AUTOGENERATED_WARNING: String = """//
// NOTE THIS FILE IS AUTO-GENERATED by the GenerateStandardLib.kt
// See: https://github.com/JetBrains/kotlin/tree/master/libraries/stdlib
//"""

/**
 * Generates methods in the standard library which are mostly identical
 * but just using a different input kind.
 *
 * Kinda like mimicking source macros here, but this avoids the inefficiency of type conversions
 * at runtime.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "Expecting Kotlin project home path as an argument" }
    val baseDir = File(args.first())

    val outDir = baseDir.resolve("libraries/stdlib/src/generated")
    require(outDir.exists()) { "$outDir doesn't exist!" }

    val jsCoreDir = baseDir.resolve("js/js.libraries/src/core/generated")
    require(jsCoreDir.exists()) { "$jsCoreDir doesn't exist!" }

    generateCollectionsAPI(outDir)
    generateCollectionsJsAPI(jsCoreDir)

}

fun generateCollectionsAPI(outDir: File) {
    val templates = sequenceOf(
            ::elements,
            ::filtering,
            ::ordering,
            ::arrays,
            ::snapshots,
            ::mapping,
            ::sets,
            ::aggregates,
            ::guards,
            ::generators,
            ::strings,
            ::sequences,
            ::specialJVM,
            ::ranges,
            ::numeric,
            ::comparables
    ).flatMap { it().sortedBy { it.signature }.asSequence() }

    templates.groupByFileAndWrite(outDir)
}

fun generateCollectionsJsAPI(outDir: File) {
    specialJS().asSequence().groupByFileAndWrite(outDir, { "_${it.name.capitalize()}Js.kt"})
}


private fun Sequence<GenericFunction>.groupByFileAndWrite(
        outDir: File,
        fileNameBuilder: (SourceFile) -> String = { "_${it.name.capitalize()}.kt" }
) {
    val groupedConcreteFunctions = flatMap { it.instantiate().asSequence() }.groupBy { it.sourceFile }

    for ((sourceFile, functions) in groupedConcreteFunctions) {
        val file = outDir.resolve(fileNameBuilder(sourceFile))
        functions.writeTo(file, sourceFile)
    }
}

private fun List<ConcreteFunction>.writeTo(file: File, sourceFile: SourceFile) {
    println("Generating file: $file")

    FileWriter(file).use { writer ->
        if (sourceFile.multifile) {
            writer.append("@file:kotlin.jvm.JvmMultifileClass\n")
        }
        writer.append("@file:kotlin.jvm.JvmName(\"${sourceFile.jvmClassName}\")\n\n")
        writer.append("package ${sourceFile.packageName ?: "kotlin"}\n\n")
        writer.append("$COMMON_AUTOGENERATED_WARNING\n\n")
        writer.append("import kotlin.comparisons.*\n\n")

        for (f in this) {
            f.textBuilder(writer)
        }
    }
}
