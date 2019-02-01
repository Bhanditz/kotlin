/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.ir.backend.js.compile
import org.jetbrains.kotlin.js.test.JsIrTestRuntime
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

fun buildConfiguration(environment: KotlinCoreEnvironment): CompilerConfiguration {
    val runtimeConfiguration = environment.configuration.copy()
    runtimeConfiguration.put(CommonConfigurationKeys.MODULE_NAME, "JS_IR_RUNTIME")

    runtimeConfiguration.languageVersionSettings = LanguageVersionSettingsImpl(
        LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE,
        specificFeatures = mapOf(
            LanguageFeature.AllowContractsForCustomFunctions to LanguageFeature.State.ENABLED,
            LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED
        ),
        analysisFlags = mapOf(
            AnalysisFlags.useExperimental to listOf("kotlin.contracts.ExperimentalContracts", "kotlin.Experimental"),
            AnalysisFlags.allowResultReturnType to true
        )
    )

    return runtimeConfiguration
}

private val stdKlibFile = File("js/js.translator/testData/out/klibs/stdlib.klib")

private val builtInFiles = listOf(
    "core/builtins/native/kotlin/Annotation.kt",
    "core/builtins/native/kotlin/Any.kt",
    "core/builtins/native/kotlin/Array.kt",
    "core/builtins/native/kotlin/Arrays.kt",
    "core/builtins/native/kotlin/Boolean.kt",
    "core/builtins/native/kotlin/Char.kt",
    "core/builtins/native/kotlin/CharSequence.kt",
//                           "core/builtins/native/kotlin/Collections.kt",
    "core/builtins/native/kotlin/Comparable.kt",
    "core/builtins/native/kotlin/Coroutines.kt",
    "core/builtins/native/kotlin/Enum.kt",
    "core/builtins/native/kotlin/Iterator.kt",
//                           "core/builtins/native/kotlin/Library.kt",
    "core/builtins/native/kotlin/Nothing.kt",
    "core/builtins/native/kotlin/Number.kt",
    "core/builtins/native/kotlin/Primitives.kt",
    "core/builtins/native/kotlin/String.kt",
    "core/builtins/native/kotlin/Throwable.kt",
    "core/builtins/src/kotlin/Annotations.kt"
)

private val stdlibFiles = listOf(
    "libraries/stdlib/js/build/builtin-sources/Unit.kt",
    "libraries/stdlib/js/build/builtin-sources/Iterators.kt",
    "libraries/stdlib/js/build/builtin-sources/Ranges.kt",
    "libraries/stdlib/js/build/builtin-sources/annotation/Annotations.kt",
    "libraries/stdlib/js/build/builtin-sources/Progressions.kt",
    "libraries/stdlib/js/build/builtin-sources/Range.kt",
    "libraries/stdlib/js/build/builtin-sources/Collections.kt",
    "libraries/stdlib/js/build/builtin-sources/internal/progressionUtil.kt",
    "libraries/stdlib/js/src/kotlin/exceptions.kt",
    "libraries/stdlib/js/build/builtin-sources/internal/InternalAnnotations.kt"
//    "libraries/stdlib/js/runtime/hacks.kt"
)

private const val builtInsHeader = """@file:Suppress(
    "NON_ABSTRACT_FUNCTION_WITH_NO_BODY",
    "MUST_BE_INITIALIZED_OR_BE_ABSTRACT",
    "EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE",
    "PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED",
    "WRONG_MODIFIER_TARGET"
)
"""

private val unimplementedNativeBuiltInsDir = KotlinTestUtils.tmpDir("unimplementedBuiltins").also { tmpDir ->
    val allBuiltins = listOfKtFilesFrom("core/builtins/native/kotlin").map { File(it).name }
//    val implementedBuiltIns = listOfKtFilesFrom("libraries/stdlib/js/irRuntime/builtins/").map { File(it).name }
    val unimplementedBuiltIns = builtInFiles
    for (filepath in unimplementedBuiltIns) {
        val originalFile = File(filepath)
        val fileName = originalFile.name
        val newFile = File(tmpDir, fileName)
        val sourceCode = builtInsHeader + originalFile.readText()
        newFile.writeText(sourceCode)
    }
}

private fun listOfKtFilesFrom(vararg paths: String): List<String> {
    val currentDir = File(".")
    return paths.flatMap { path ->
        File(path)
            .walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.relativeToOrSelf(currentDir).path }
            .asIterable()
    }.distinct()
}

private val testFiles = listOf("js/js.translator/testData/box/dynamic/callMethods.kt")

private val tests = listOfKtFilesFrom(unimplementedNativeBuiltInsDir.path) + stdlibFiles + testFiles

fun main() {

    val environment = KotlinCoreEnvironment.createForTests(Disposable { }, CompilerConfiguration(), EnvironmentConfigFiles.JS_CONFIG_FILES)

    fun createPsiFile(fileName: String): KtFile {
        val psiManager = PsiManager.getInstance(environment.project)
        val fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

        val file = fileSystem.findFileByPath(fileName) ?: error("File not found: $fileName")

        return psiManager.findFile(file) as KtFile
    }

    val result = compile(
        environment.project,
        (JsIrTestRuntime.DEFAULT.sources + testFiles).map(::createPsiFile),
//        tests.map(::createPsiFile),
        buildConfiguration(environment),
        null,
        true,
        emptyList(),
        emptyList()
    )

    TODO("Write library into $stdKlibFile")
}