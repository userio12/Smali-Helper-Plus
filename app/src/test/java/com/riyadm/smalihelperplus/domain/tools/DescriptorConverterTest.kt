package com.riyadm.smalihelperplus.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DescriptorConverterTest {

    private fun desc(java: String) = DescriptorConverter.javaTypeToDescriptor(java).descriptor
    private fun java(desc: String) = DescriptorConverter.parseTypeDescriptor(desc).java

    @Test fun primitivesToDescriptors() {
        assertThat(desc("int")).isEqualTo("I")
        assertThat(desc("boolean")).isEqualTo("Z")
        assertThat(desc("byte")).isEqualTo("B")
        assertThat(desc("short")).isEqualTo("S")
        assertThat(desc("char")).isEqualTo("C")
        assertThat(desc("long")).isEqualTo("J")
        assertThat(desc("float")).isEqualTo("F")
        assertThat(desc("double")).isEqualTo("D")
        assertThat(desc("void")).isEqualTo("V")
    }

    @Test fun classAndArraysToDescriptors() {
        assertThat(desc("java.lang.String")).isEqualTo("Ljava/lang/String;")
        assertThat(desc("int[]")).isEqualTo("[I")
        assertThat(desc("String[][]")).isEqualTo("[[LString;")
        assertThat(desc("java.lang.Object[]")).isEqualTo("[Ljava/lang/Object;")
        assertThat(desc("  long [] [] ")).isEqualTo("[[J")
    }

    @Test fun innerClassDots() {
        assertThat(desc("java.util.Map.Entry")).isEqualTo("Ljava/util/Map\$Entry;")
        assertThat(desc("com.acme.demo.Outer.Inner.Deep")).isEqualTo("Lcom/acme/demo/Outer\$Inner\$Deep;")
    }

    @Test fun innerClassDollar() {
        assertThat(desc("com.acme.demo.Outer\$Inner")).isEqualTo("Lcom/acme/demo/Outer\$Inner;")
    }

    @Test fun genericsAreStrippedWithNote() {
        val notes = mutableListOf<String>()
        val t = DescriptorConverter.javaTypeToDescriptor("java.util.List<java.lang.String>", notes)
        assertThat(t.descriptor).isEqualTo("Ljava/util/List;")
        assertThat(notes.joinToString()).contains("Generic")
        assertThat(desc("java.util.Map<String, List<Integer>>[]")).isEqualTo("[Ljava/util/Map;")
    }

    @Test fun varargsBecomesArray() {
        assertThat(desc("int...")).isEqualTo("[I")
    }

    @Test fun descriptorsToJava() {
        assertThat(java("I")).isEqualTo("int")
        assertThat(java("[I")).isEqualTo("int[]")
        assertThat(java("[[Ljava/lang/String;")).isEqualTo("java.lang.String[][]")
        assertThat(java("Ljava/lang/String;")).isEqualTo("java.lang.String")
        assertThat(java("V")).isEqualTo("void")
        assertThat(java("Lcom/a/Outer\$Inner;")).isEqualTo("com.a.Outer.Inner")
    }

    @Test fun anonymousClassKeepsDollar() {
        assertThat(java("Lcom/a/Outer\$1;")).isEqualTo("com.a.Outer\$1")
    }

    @Test fun descriptorErrorsHavePositions() {
        val e = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("Ljava/lang/String") }
        assertThat(e.message).contains("';'")
        val e2 = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("[") }
        assertThat(e2.message).contains("element type")
        val e3 = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("Q") }
        assertThat(e3.position).isEqualTo(0)
        val e4 = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("II") }
        assertThat(e4.position).isEqualTo(1)
        val e5 = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("Lcom//A;") }
        assertThat(e5.message).contains("stray")
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseTypeDescriptor("[V") }
    }

    @Test fun javaTypeErrors() {
        assertThrows(DescriptorException::class.java) { DescriptorConverter.javaTypeToDescriptor("") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.javaTypeToDescriptor("void[]") }
        val e = assertThrows(DescriptorException::class.java) { DescriptorConverter.javaTypeToDescriptor("java.lang.Str#ing") }
        assertThat(e.position).isEqualTo(13)
        assertThrows(DescriptorException::class.java) { DescriptorConverter.javaTypeToDescriptor("java..String") }
    }

    @Test fun methodSignatureToProto() {
        val (name, proto) = DescriptorConverter.parseJavaMethod("int add(int a, long b)")
        assertThat(name).isEqualTo("add")
        assertThat(proto.descriptor).isEqualTo("(IJ)I")
        assertThat(proto.params.map { it.name }).containsExactly("a", "b").inOrder()
        assertThat(proto.paramWords).isEqualTo(3)
    }

    @Test fun methodSignatureWithModifiersAndGenerics() {
        val (name, proto) = DescriptorConverter.parseJavaMethod(
            "public static java.util.List<String> find(final java.lang.String key, java.util.Map<String, Integer> map, int... rest) throws Exception",
        )
        assertThat(name).isEqualTo("find")
        assertThat(proto.descriptor).isEqualTo("(Ljava/lang/String;Ljava/util/Map;[I)Ljava/util/List;")
    }

    @Test fun constructorSignatureDefaultsToVoid() {
        val (name, proto) = DescriptorConverter.parseJavaMethod("<init>(int, String[])")
        assertThat(name).isEqualTo("<init>")
        assertThat(proto.descriptor).isEqualTo("(I[LString;)V")
    }

    @Test fun missingReturnTypeIsAnError() {
        val e = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseJavaMethod("add(int a)") }
        assertThat(e.message).contains("return type")
    }

    @Test fun protoToJava() {
        val proto = DescriptorConverter.parseProto("(IJLjava/lang/String;[D)Z")
        assertThat(DescriptorConverter.protoToJava(proto)).isEqualTo("boolean method(int, long, java.lang.String, double[])")
        assertThat(proto.paramWords).isEqualTo(5)
    }

    @Test fun protoErrors() {
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseProto("(I") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseProto("(V)V") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseProto("(I)") }
        val e = assertThrows(DescriptorException::class.java) { DescriptorConverter.parseProto("(I)VV") }
        assertThat(e.position).isEqualTo(4)
    }

    @Test fun fieldReference() {
        val f = DescriptorConverter.parseFieldRef("Lcom/a/B;->name:I")
        assertThat(f.owner.java).isEqualTo("com.a.B")
        assertThat(f.name).isEqualTo("name")
        assertThat(f.type.java).isEqualTo("int")
        assertThat(f.java).isEqualTo("int com.a.B.name")
        assertThat(f.smali).isEqualTo("Lcom/a/B;->name:I")
    }

    @Test fun fieldReferenceErrors() {
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseFieldRef("Lcom/a/B;name:I") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseFieldRef("Lcom/a/B;->name") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseFieldRef("Lcom/a/B;->name:V") }
        assertThrows(DescriptorException::class.java) { DescriptorConverter.parseFieldRef("Lcom/a/B;-><init>:I") }
    }

    @Test fun methodReference() {
        val m = DescriptorConverter.parseMethodRef("Lcom/a/B;->m(ILjava/lang/String;)V")
        assertThat(m.name).isEqualTo("m")
        assertThat(m.proto.params).hasSize(2)
        assertThat(m.java).isEqualTo("void com.a.B.m(int, java.lang.String)")
        assertThat(m.smali).isEqualTo("Lcom/a/B;->m(ILjava/lang/String;)V")
    }

    @Test fun constructorReference() {
        val m = DescriptorConverter.parseMethodRef("Ljava/lang/StringBuilder;-><init>()V")
        assertThat(m.name).isEqualTo("<init>")
        assertThat(m.java).isEqualTo("void java.lang.StringBuilder.<init>()")
    }

    @Test fun convertAutoDetectsInput() {
        val r1 = DescriptorConverter.convert("int[]") as DescriptorResult.Success
        assertThat(r1.kind).isEqualTo(DescriptorResult.Kind.JAVA_TYPE)
        assertThat(r1.lines.first().second).isEqualTo("[I")

        val r2 = DescriptorConverter.convert("[[J") as DescriptorResult.Success
        assertThat(r2.kind).isEqualTo(DescriptorResult.Kind.DESCRIPTOR)
        assertThat(r2.lines.first().second).isEqualTo("long[][]")

        val r3 = DescriptorConverter.convert("(IJ)I") as DescriptorResult.Success
        assertThat(r3.kind).isEqualTo(DescriptorResult.Kind.PROTO)
        assertThat(r3.lines.first().second).isEqualTo("int method(int, long)")

        val r4 = DescriptorConverter.convert("int add(int a, long b)") as DescriptorResult.Success
        assertThat(r4.kind).isEqualTo(DescriptorResult.Kind.JAVA_METHOD)
        assertThat(r4.lines.first().second).isEqualTo("(IJ)I")

        val r5 = DescriptorConverter.convert("Lcom/a/B;->name:I") as DescriptorResult.Success
        assertThat(r5.kind).isEqualTo(DescriptorResult.Kind.FIELD_REF)

        val r6 = DescriptorConverter.convert("Lcom/a/B;->m(I)V") as DescriptorResult.Success
        assertThat(r6.kind).isEqualTo(DescriptorResult.Kind.METHOD_REF)
    }

    @Test fun convertReportsFailureWithPosition() {
        val r = DescriptorConverter.convert("Lcom/a/B;->name:X") as DescriptorResult.Failure
        assertThat(r.position).isEqualTo(16)
        assertThat(DescriptorConverter.convert("   ")).isInstanceOf(DescriptorResult.Failure::class.java)
    }

    @Test fun parseAnySignatureVariants() {
        assertThat(DescriptorConverter.parseAnySignature("(IJ)V").descriptor).isEqualTo("(IJ)V")
        assertThat(DescriptorConverter.parseAnySignature("int a, long b").descriptor).isEqualTo("(IJ)V")
        assertThat(DescriptorConverter.parseAnySignature("(int, long)").descriptor).isEqualTo("(IJ)V")
        assertThat(DescriptorConverter.parseAnySignature("IJ").descriptor).isEqualTo("(IJ)V")
        assertThat(DescriptorConverter.parseAnySignature("Lcom/a/B;->m(DD)I").descriptor).isEqualTo("(DD)I")
        assertThat(DescriptorConverter.parseAnySignature("void run(String s)").descriptor).isEqualTo("(LString;)V")
        assertThat(DescriptorConverter.parseAnySignature("").descriptor).isEqualTo("()V")
        assertThat(DescriptorConverter.parseAnySignature("java.lang.String[] names, int... counts").descriptor)
            .isEqualTo("([Ljava/lang/String;[I)V")
    }

    @Test fun wideTypes() {
        assertThat(DescriptorConverter.parseTypeDescriptor("J").words).isEqualTo(2)
        assertThat(DescriptorConverter.parseTypeDescriptor("D").isWide).isTrue()
        assertThat(DescriptorConverter.parseTypeDescriptor("[J").words).isEqualTo(1)
        assertThat(DescriptorConverter.parseTypeDescriptor("V").words).isEqualTo(0)
    }
}
