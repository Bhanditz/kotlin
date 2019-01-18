package org.jetbrains.kotlinx.serialization.compiler.backend.ir

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlinx.serialization.compiler.backend.common.SerializableCodegen
import org.jetbrains.kotlinx.serialization.compiler.resolve.*
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerialEntityNames.MISSING_FIELD_EXC

class SerializableIrGenerator(
    val irClass: IrClass,
    override val compilerContext: BackendContext,
    bindingContext: BindingContext
) : SerializableCodegen(irClass.descriptor, bindingContext), IrBuilderExtension {
    override val translator: TypeTranslator = compilerContext.createTypeTranslator(serializableDescriptor.module)
    private val _table = SymbolTable()
    override val BackendContext.localSymbolTable: SymbolTable
        get() = _table

    override fun generateInternalConstructor(constructorDescriptor: ClassConstructorDescriptor) =
        irClass.contributeConstructor(constructorDescriptor) { ctor ->
            val transformFieldInitializer = buildInitializersRemapping(irClass)

            // Missing field exception parts
            val exceptionCtor =
                serializableDescriptor.getClassFromSerializationPackage(MISSING_FIELD_EXC)
                    .unsubstitutedPrimaryConstructor!!
            val exceptionCtorRef = compilerContext.externalSymbols.referenceConstructor(exceptionCtor)
            val exceptionType = exceptionCtorRef.owner.returnType

            val thiz = irClass.thisReceiver!!
            if (KotlinBuiltIns.isAny(irClass.descriptor.getSuperClassOrAny()))
                generateAnySuperConstructorCall(toBuilder = this@contributeConstructor)
            else
                TODO("Serializable classes with inheritance")
            val seenVarsOffset = properties.serializableProperties.bitMaskSlotCount()
            val seenVars = (0 until seenVarsOffset).map { ctor.valueParameters[it] }

            for ((index, prop) in properties.serializableProperties.withIndex()) {
                val paramRef = ctor.valueParameters[index + seenVarsOffset]
                // assign this.a = a in else branch
                val assignParamExpr = irSetField(irGet(thiz), prop.irField, irGet(paramRef))

                val ifNotSeenExpr: IrExpression = if (prop.optional) {
                    val initializerBody =
                        requireNotNull(transformFieldInitializer(prop.irField)) { "Optional value without an initializer" } // todo: filter abstract here
                    irSetField(irGet(thiz), prop.irField, initializerBody)
                } else {
                    irThrow(irInvoke(null, exceptionCtorRef, irString(prop.name), typeHint = exceptionType))
                }

                val propNotSeenTest =
                    irEquals(
                        irInt(0),
                        irBinOp(
                            OperatorNameConventions.AND,
                            irGet(seenVars[bitMaskSlotAt(index)]),
                            irInt(1 shl (index % 32))
                        )
                    )

                +irIfThenElse(compilerContext.irBuiltIns.unitType, propNotSeenTest, ifNotSeenExpr, assignParamExpr)
            }

            // remaining initalizers of variables
            val serialDescs = properties.serializableProperties.map { it.descriptor }.toSet()
            irClass.declarations.asSequence()
                .filterIsInstance<IrProperty>()
                .filter { it.descriptor !in serialDescs }
                .filter { it.backingField != null }
                .mapNotNull { prop -> transformFieldInitializer(prop.backingField!!)?.let { prop to it } }
                .forEach { (prop, expr) -> +irSetField(irGet(thiz), prop.backingField!!, expr) }

            // init blocks
            irClass.declarations.asSequence()
                .filterIsInstance<IrAnonymousInitializer>()
                .forEach { initializer ->
                    initializer.body.statements.forEach { +it }
                }
        }

    override fun generateWriteSelfMethod(methodDescriptor: FunctionDescriptor) {
        // no-op
    }

    companion object {
        fun generate(
            irClass: IrClass,
            context: BackendContext,
            bindingContext: BindingContext
        ) {
            val serializableClass = irClass.descriptor

            if (serializableClass.isInternalSerializable)
                SerializableIrGenerator(irClass, context, bindingContext).generate()
            else if (serializableClass.hasSerializableAnnotationWithoutArgs && !serializableClass.hasCompanionObjectAsSerializer) {
                throw CompilationException(
                    "@Serializable annotation on $serializableClass would be ignored because it is impossible to serialize it automatically. " +
                            "Provide serializer manually via e.g. companion object", null, serializableClass.findPsi()
                )
            }
        }
    }
}