"""Beginner track, part 1 (orders 1-5)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "beginner"

LESSONS = [
    lesson(
        id="what-is-smali",
        level=L,
        order=1,
        title="What is Smali?",
        summary="""
            Smali is the human-readable assembly language for Dalvik bytecode, the instruction set stored in the
            classes.dex files of every Android app. Learn where it comes from, how it relates to Java and Kotlin,
            and how to read your first method.
        """,
        minutes=8,
        sections=[
            text("""
                When you build an Android app, the Java or Kotlin compiler first produces ordinary JVM .class files.
                The Android build tools (today the D8 dexer, with R8 for release builds) then translate those
                classes into Dalvik bytecode and pack them into one or more classes.dex files inside the APK.

                Dalvik bytecode is a binary format, so it is hard to read directly. Smali is a text syntax for it:
                every Dalvik instruction, class, field and method has a one-to-one textual form. The name comes from
                the Icelandic word for "assembler"; its companion tool baksmali ("disassembler") turns a .dex file
                back into .smali text files, one per class.
            """, title="From source code to Smali"),
            text("""
                Because Smali maps directly to the bytecode, it shows exactly what the device will execute:
                - which registers hold which values,
                - which methods are called and with which arguments,
                - how branches, loops and exception handlers are laid out.

                That precision is why Smali is the language of choice when you need to understand how a compiled
                app behaves, debug a build or optimisation problem, or study what the compiler generated for your
                own code.
            """, title="Why learn it?"),
            code("""
                .class public Lcom/acme/demo/Greeter;
                .super Ljava/lang/Object;

                .method public static greet(Ljava/lang/String;)Ljava/lang/String;
                    .registers 3

                    new-instance v0, Ljava/lang/StringBuilder;
                    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

                    const-string v1, "Hello, "
                    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

                    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                    move-result-object v0
                    return-object v0
                .end method
            """, title="A complete Smali class"),
            java("""
                package com.acme.demo;

                public class Greeter {
                    public static String greet(String name) {
                        return new StringBuilder().append("Hello, ").append(name).toString();
                    }
                }
            """, title="Equivalent Java"),
            text("""
                Read the listing from top to bottom. Lines starting with a dot are directives: .class names the
                class using a type descriptor (Lcom/acme/demo/Greeter;), .super names its superclass, and .method
                ... .end method wraps one method. The method header greet(Ljava/lang/String;)Ljava/lang/String;
                says it takes one String and returns a String.

                Inside the method, .registers 3 reserves three registers. The single argument arrives in the last
                one, which Smali also lets you call p0 because the method is static. Every other line is one
                instruction: create a StringBuilder, call its constructor, load a constant string, call append
                twice, call toString, copy the result into v0 with move-result-object and return it.
            """, title="Reading the listing"),
            tip("""
                Smali is not a decompiler output. A decompiler guesses Java source; Smali shows the real
                instructions. When a decompiled view looks odd, the Smali is the ground truth.
            """),
            warning("""
                Only analyse apps you wrote yourself or are explicitly authorised to examine. Respect licences,
                terms of service and the law in your country.
            """, title="Use it responsibly"),
        ],
        exercises=[
            exercise(
                "what-is-smali-ex1",
                """
                    Write a static method answer()I in a class Lcom/acme/demo/Quiz; that returns the number 42.
                    Use as few registers as possible.
                """,
                starter="""
                    .class public Lcom/acme/demo/Quiz;
                    .super Ljava/lang/Object;

                    .method public static answer()I
                        .registers 1
                        # load 42 into v0 and return it
                    .end method
                """,
                solution="""
                    .class public Lcom/acme/demo/Quiz;
                    .super Ljava/lang/Object;

                    .method public static answer()I
                        .registers 1
                        const/16 v0, 0x2a
                        return v0
                    .end method
                """,
                hint="42 does not fit in the 4-bit literal of const/4 (range -8..7), but it fits in const/16.",
            ),
            exercise(
                "what-is-smali-ex2",
                """
                    In the Greeter example, which instruction stores the value returned by toString() into a
                    register, and why is it needed?
                """,
                solution="""
                    move-result-object v0 copies the object returned by the immediately preceding invoke-virtual
                    into v0. Invoke instructions never write a register themselves; the result must be fetched by
                    a move-result* instruction placed directly after the call.
                """,
                hint="Look at the line right after the last invoke-virtual.",
            ),
        ],
        quiz=[
            question(
                "what-is-smali-q1",
                "What does Smali represent?",
                [
                    "A text syntax for Dalvik bytecode stored in .dex files",
                    "A decompiled approximation of the original Java source",
                    "The machine code ART generates for ARM CPUs",
                    "A scripting language that runs inside Android apps",
                ],
                0,
                """
                    Smali is an assembly-style, one-to-one textual form of Dalvik bytecode. Decompilers produce
                    Java-like guesses; ART's AOT output is native machine code, which is a different thing.
                """,
            ),
            question(
                "what-is-smali-q2",
                "Which tool turns a classes.dex file into .smali text files?",
                ["smali", "baksmali", "dex2oat", "javac"],
                1,
                "baksmali disassembles .dex into .smali; smali assembles .smali back into .dex.",
            ),
            question(
                "what-is-smali-q3",
                "What does this line declare?",
                ["The superclass of the class", "An interface the class implements", "The source file name", "A field"],
                0,
                ".super names the direct superclass. Every class except java.lang.Object has exactly one.",
                code=".super Ljava/lang/Object;",
            ),
            question(
                "what-is-smali-q4",
                "Why does the Greeter example need move-result-object after calling toString()?",
                [
                    "Invoke instructions do not write their result to a register",
                    "To convert the String into a primitive",
                    "Because toString() is a static method",
                    "It releases the StringBuilder for garbage collection",
                ],
                0,
                """
                    An invoke stores its return value in a hidden result slot. move-result, move-result-wide or
                    move-result-object must immediately follow the call to copy it into a register.
                """,
            ),
        ],
        related=["const-string", "new-instance", "invoke-direct", "invoke-virtual", "move-result-object", "return-object"],
    ),
]

FLASHCARDS = [
    card("fc-b1-01", "Directives", "What does the .super directive declare?",
         "The direct superclass of the class, written as a type descriptor, e.g. .super Ljava/lang/Object;"),
    card("fc-b1-02", "Opcodes", "What does move-result-object do?",
         "Copies the object reference returned by the immediately preceding invoke-* (or filled-new-array) into a register.",
         code="""
            invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;
            move-result-object v1
         """),
]

LESSONS += [
    lesson(
        id="android-runtime",
        level=L,
        order=2,
        title="The Android Runtime",
        summary="""
            Dalvik bytecode is executed by the Android Runtime (ART), which replaced the original Dalvik virtual
            machine. See how interpretation, JIT and ahead-of-time compilation fit together, and why the bytecode
            you read in Smali remains the contract between an app and the device.
        """,
        minutes=9,
        sections=[
            text("""
                Early Android versions ran apps on the Dalvik virtual machine. Dalvik interpreted bytecode and,
                from Android 2.2, added a just-in-time (JIT) compiler that translated frequently executed code
                into native instructions while the app was running.

                Android 4.4 shipped the Android Runtime (ART) as a developer option, and Android 5.0 made it the
                only runtime. ART and Dalvik execute the same DEX bytecode, so an app built for one runs on the
                other; what changed is how and when that bytecode turns into machine code.
            """, title="From Dalvik to ART"),
            text("""
                ART can run a method in three ways:
                - Interpreted: the bytecode is executed instruction by instruction.
                - JIT compiled: hot methods are compiled to native code at run time and kept in a code cache.
                - AOT compiled: the on-device dex2oat tool compiles DEX files to native code ahead of time and
                  stores the result in OAT files next to the app.

                Android 5.0 and 6.0 compiled almost the whole app with dex2oat at install time, which made installs
                and system updates slow. Android 7.0 introduced a hybrid model: apps start out interpreted and
                JIT-compiled, the JIT records which methods are hot in a profile, and later, when the device is
                idle and charging, dex2oat compiles just those methods (the speed-profile filter). Play Store
                cloud profiles and Baseline Profiles shipped inside apps let this happen even before first use.
            """, title="Interpreter, JIT and AOT"),
            code("""
                .method public static sum([I)I
                    .registers 4

                    const/4 v0, 0x0          # total
                    const/4 v1, 0x0          # index

                    :loop
                    array-length v2, p0
                    if-ge v1, v2, :done
                    aget v2, p0, v1
                    add-int/2addr v0, v2
                    add-int/lit8 v1, v1, 0x1
                    goto :loop

                    :done
                    return v0
                .end method
            """, title="A hot loop: interpreted first, compiled later"),
            java("""
                public static int sum(int[] values) {
                    int total = 0;
                    for (int i = 0; i < values.length; i++) {
                        total += values[i];
                    }
                    return total;
                }
            """, title="Equivalent Java"),
            text("""
                Whichever execution mode ART picks, it must produce the same observable behaviour as the bytecode.
                Native code from the JIT or dex2oat is a cache that can be thrown away and regenerated; the DEX
                bytecode is the real program. That is why reading Smali tells you what an app does, regardless of
                the device, Android version or compilation state.

                Before any of this happens, ART verifies each class: it checks that registers are used with
                consistent types, that branches land on instruction boundaries and that method and field
                references make sense. Bytecode that fails verification is rejected, typically with a
                java.lang.VerifyError when the class is used.
            """, title="What this means for Smali readers"),
            tip("""
                On a test device with your own debuggable app you can trigger profile-guided compilation with
                adb shell cmd package compile -m speed-profile -f <package>, or drop the compiled code by using
                -m verify instead (the app then runs interpreted and JIT-compiled again). This is handy for reproducing performance issues.
            """),
            warning("""
                Tricks that relied on Dalvik quirks, such as editing bytecode in memory or depending on
                tolerated-but-invalid DEX files, often break on ART because its verifier and compiler are stricter.
            """, title="Dalvik-era assumptions"),
        ],
        exercises=[
            exercise(
                "android-runtime-ex1",
                """
                    Write a static method count([Ljava/lang/Object;)I that returns the length of the array it
                    receives. Use .registers so that the argument ends up in p0.
                """,
                starter="""
                    .method public static count([Ljava/lang/Object;)I
                        .registers 2
                        # return the array length
                    .end method
                """,
                solution="""
                    .method public static count([Ljava/lang/Object;)I
                        .registers 2
                        array-length v0, p0
                        return v0
                    .end method
                """,
                hint="array-length vA, vB writes the length of the array in vB into vA.",
            ),
            exercise(
                "android-runtime-ex2",
                """
                    An app runs slowly right after installation but gets faster after a few days of use, without
                    an update. Using the ART execution modes, explain what most likely happened.
                """,
                solution="""
                    At first the app was interpreted and JIT-compiled on demand. The JIT recorded the hot methods
                    in a profile, and during idle maintenance dex2oat compiled those methods ahead of time with the
                    speed-profile filter. Later launches run that native code directly instead of warming up again.
                """,
                hint="Think about which component writes the profile and which one reads it.",
            ),
        ],
        quiz=[
            question(
                "android-runtime-q1",
                "Which Android release made ART the only runtime, replacing Dalvik?",
                ["Android 2.2", "Android 4.4", "Android 5.0", "Android 7.0"],
                2,
                """
                    Android 4.4 offered ART as a developer option; Android 5.0 removed Dalvik and made ART the
                    default and only runtime.
                """,
            ),
            question(
                "android-runtime-q2",
                "What is dex2oat?",
                [
                    "The on-device tool that compiles DEX bytecode into native code ahead of time",
                    "A desktop tool that converts .class files into .dex files",
                    "The JIT compiler that runs inside the app process",
                    "A command that converts Smali text into DEX",
                ],
                0,
                """
                    dex2oat runs on the device and turns DEX files into OAT files containing compiled code. The
                    desktop .class-to-.dex step is done by D8/R8, and Smali-to-DEX by the smali assembler.
                """,
            ),
            question(
                "android-runtime-q3",
                "Since Android 7.0, what typically drives which methods dex2oat compiles?",
                [
                    "A profile of hot methods recorded while the app runs",
                    "Every method is always compiled at install time",
                    "Only methods annotated with @Hot",
                    "Methods listed in AndroidManifest.xml",
                ],
                0,
                """
                    The hybrid model records hot methods in a profile; background dexopt then compiles them with the
                    speed-profile filter. Cloud and Baseline Profiles can supply that information up front.
                """,
            ),
            question(
                "android-runtime-q4",
                "ART has compiled this method to native code. Which statement is true?",
                [
                    "Its behaviour must still match the bytecode, so the Smali stays an accurate description",
                    "The bytecode is deleted from the APK after compilation",
                    "The native code may behave differently from the bytecode",
                    "The method can no longer be verified",
                ],
                0,
                """
                    Compiled code is an optimisation of the bytecode and must preserve its semantics. The DEX file
                    inside the APK is unchanged, so reading the Smali still tells you what the method does.
                """,
                code="""
                    .method public static twice(I)I
                        .registers 2
                        add-int v0, p0, p0
                        return v0
                    .end method
                """,
            ),
            question(
                "android-runtime-q5",
                "What usually happens when a class contains bytecode that fails ART's verifier?",
                [
                    "A java.lang.VerifyError is thrown when the class is used",
                    "ART silently skips the invalid instructions",
                    "The app is uninstalled",
                    "The instructions are executed by the old Dalvik VM instead",
                ],
                0,
                """
                    Hard verification failures cause the class to be rejected, which surfaces as a VerifyError.
                    There is no fallback to Dalvik on modern devices.
                """,
            ),
        ],
        related=["array-length", "aget", "add-int/2addr", "add-int/lit8", "if-ge", "goto"],
    ),
    lesson(
        id="dalvik-bytecode",
        level=L,
        order=3,
        title="Dalvik Bytecode",
        summary="""
            Dalvik bytecode is register-based and built from 16-bit code units. Compare it with JVM stack bytecode,
            tour the main instruction families and learn what the verifier checks before code is allowed to run.
        """,
        minutes=10,
        sections=[
            text("""
                The Java virtual machine uses a stack machine: instructions push values onto an operand stack and
                pop them off again. Dalvik was designed as a register machine instead. Each method declares a fixed
                number of virtual registers, and instructions name their source and destination registers
                explicitly.

                For c = a + b with int parameters, javac emits four stack instructions:
                - iload_0 (push a)
                - iload_1 (push b)
                - iadd (pop both, push the sum)
                - istore_2 (pop the sum into local c)

                Dalvik needs a single instruction, add-int, that reads two registers and writes a third. Register
                code has fewer instructions to dispatch and maps well onto real CPU registers, at the cost of
                slightly larger individual instructions.
            """, title="Registers instead of a stack"),
            code("""
                .method public static add(II)I
                    .registers 3

                    # v0 = p0 + p1  (p0 is v1, p1 is v2)
                    add-int v0, p0, p1
                    return v0
                .end method
            """, title="c = a + b in Dalvik bytecode"),
            java("""
                public static int add(int a, int b) {
                    int c = a + b;
                    return c;
                }
            """, title="Equivalent Java"),
            text("""
                Dalvik code is a sequence of 16-bit code units. The low byte of an instruction's first unit is its
                opcode; the remaining bits and any following units hold registers, literals and indexes. Short
                instructions such as const/4 v0, 0x1 or return v0 fit in one unit, add-int needs two, a call such
                as invoke-virtual needs three, and const-wide with a full 64-bit literal needs five.

                Each layout has a format id such as 11n, 23x or 35c. The first digit is the size in code units,
                the second is the number of registers, and the letter describes any extra data (n for a small
                literal, x for none, c for a constant-pool index). Branch offsets are also measured in code units,
                not bytes.
            """, title="Code units and formats"),
            text("""
                A little over 200 opcode values are assigned, and they fall into a few families:
                - Moves and constants: move, move-object, move-result*, const*, const-string, const-class.
                - Arithmetic and conversion: add-int, mul-long, div-double, int-to-long, neg-float and friends.
                - Objects and arrays: new-instance, new-array, check-cast, instance-of, aget/aput, array-length.
                - Fields: iget/iput for instance fields, sget/sput for static fields.
                - Calls: invoke-virtual, invoke-super, invoke-direct, invoke-static, invoke-interface.
                - Control flow: goto, if-* comparisons, packed-switch, sparse-switch, return*, throw.
                Most families have typed variants (-wide for long/double, -object for references, -boolean and
                others for small primitives), because the verifier must know what kind of value each register holds.
            """, title="Instruction families"),
            tip("""
                Before ART runs a method, the verifier walks every path through it. It checks that each register
                holds a value of a consistent type, that a register is written before it is read, that branch
                targets are real instruction boundaries and that move-result follows an invoke. If you hand-edit
                Smali and get a VerifyError, one of these rules is usually broken.
            """, title="What the verifier checks"),
        ],
        exercises=[
            exercise(
                "dalvik-bytecode-ex1",
                """
                    Write a static method mul(II)I that multiplies its two int arguments and returns the product,
                    using a single arithmetic instruction.
                """,
                starter="""
                    .method public static mul(II)I
                        .registers 3
                        # multiply p0 by p1 and return the result
                    .end method
                """,
                solution="""
                    .method public static mul(II)I
                        .registers 3
                        mul-int v0, p0, p1
                        return v0
                    .end method
                """,
                hint="The three-register form is mul-int vAA, vBB, vCC.",
            ),
            exercise(
                "dalvik-bytecode-ex2",
                """
                    Rewrite the add method so it uses only two registers in total, storing the result back into
                    the first parameter register, and still returns a + b.
                """,
                starter="""
                    .method public static add(II)I
                        .registers 2
                        # p0 is v0, p1 is v1
                    .end method
                """,
                solution="""
                    .method public static add(II)I
                        .registers 2
                        add-int/2addr p0, p1
                        return p0
                    .end method
                """,
                hint="The /2addr variants use the first register as both a source and the destination.",
            ),
        ],
        quiz=[
            question(
                "dalvik-bytecode-q1",
                "How does Dalvik bytecode pass values between instructions?",
                [
                    "Through numbered virtual registers named in each instruction",
                    "Through an operand stack, like JVM bytecode",
                    "Through global variables",
                    "Through CPU registers chosen by the app developer",
                ],
                0,
                """
                    Dalvik is a register machine: each method has a fixed register frame, and instructions name the
                    registers they read and write.
                """,
            ),
            question(
                "dalvik-bytecode-q2",
                "How big is a Dalvik code unit?",
                ["8 bits", "16 bits", "32 bits", "64 bits"],
                1,
                """
                    Instructions are made of one or more 16-bit code units, and branch offsets count code units.
                """,
            ),
            question(
                "dalvik-bytecode-q3",
                "In the format id 35c used by invoke-virtual, what does the 3 mean?",
                [
                    "The instruction is three code units long",
                    "It takes three registers",
                    "It belongs to opcode group 3",
                    "It references three constant-pool entries",
                ],
                0,
                """
                    The first digit is the length in code units and the second is the maximum register count, so
                    35c is three units long, takes up to five registers and carries a constant-pool index.
                """,
            ),
            question(
                "dalvik-bytecode-q4",
                "Which family does this instruction belong to?",
                ["Instance field access", "Static method calls", "Array access", "Type checks"],
                0,
                """
                    iget-object reads an object-typed instance field (the i stands for instance). Static fields use
                    sget*, arrays use aget*.
                """,
                code="iget-object v0, p0, Lcom/acme/demo/User;->name:Ljava/lang/String;",
            ),
            question(
                "dalvik-bytecode-q5",
                "Why do many instructions come in variants such as -wide and -object?",
                [
                    "The verifier needs to know the kind of value each register holds",
                    "To make the bytecode shorter",
                    "They are only kept for Dalvik compatibility and behave identically",
                    "They select which CPU core runs the instruction",
                ],
                0,
                """
                    Typed variants tell the verifier and the runtime whether a register holds a 32-bit primitive, a
                    64-bit pair or a reference, which matters for type safety and garbage collection.
                """,
            ),
        ],
        related=["add-int", "add-int/2addr", "mul-int", "const/4", "invoke-virtual", "iget-object", "return"],
    ),
    lesson(
        id="dex-files",
        level=L,
        order=4,
        title="DEX Files",
        summary="""
            A .dex file packs all of an app's classes into shared tables of strings, types, prototypes, fields and
            methods. Learn what lives inside, why large apps are split into classes2.dex and beyond, and how
            baksmali turns a DEX file into a tree of .smali files.
        """,
        minutes=9,
        sections=[
            text("""
                A JVM .class file holds exactly one class. A DEX file holds many classes at once and shares their
                constant data: every string, type name, method prototype, field reference and method reference is
                stored once in a table and referred to by index. This makes DEX files compact and fast to map into
                memory.

                At a conceptual level a DEX file contains:
                - a header with the magic value, checksums, file size and the location of every table,
                - id tables: string_ids, type_ids, proto_ids, field_ids, method_ids,
                - class_defs describing each class defined in the file,
                - a data area with the actual bytecode, string contents, annotations and debug info,
                - a map_list summarising every section.
            """, title="One file, many classes"),
            text("""
                Every DEX file starts with 8 magic bytes: the text dex, a newline, a three-digit format version and
                a zero byte, for example dex\\n035\\0. Version 035 is the classic format; 037 arrived with Android
                7.0 (default interface methods), 038 with Android 8.0 (invoke-polymorphic and invoke-custom), 039
                with Android 9 (const-method-handle and const-method-type) and 040 with Android 10. Tools such as
                D8 pick the lowest version that supports the features your code uses and your minSdk.
            """, title="Magic and version"),
            code("""
                .class public Lcom/acme/demo/Counter;
                .super Ljava/lang/Object;
                .source "Counter.java"

                .field private count:I

                .method public increment()V
                    .registers 2

                    iget v0, p0, Lcom/acme/demo/Counter;->count:I
                    add-int/lit8 v0, v0, 0x1
                    iput v0, p0, Lcom/acme/demo/Counter;->count:I
                    return-void
                .end method
            """, title="Every reference here becomes a table entry"),
            text("""
                In the listing above, Lcom/acme/demo/Counter; is stored once in type_ids, the name count once in
                string_ids, and Lcom/acme/demo/Counter;->count:I once in field_ids. Both iget and iput carry the same
                16-bit field index. Smali shows you the resolved names so you never have to read the indexes
                yourself.
            """, title="Indexes behind the names"),
            text("""
                Instructions such as invoke-virtual refer to methods with a 16-bit index, so one DEX file can
                reference at most 65,536 methods. This counts every method referenced, including framework and
                library methods your code calls, not only the methods you define. The same limit applies to field
                and type references.

                Larger apps are split into several files named classes.dex, classes2.dex, classes3.dex and so on.
                ART (Android 5.0 and later) loads all of them natively; older Dalvik devices needed the multidex
                support library.
            """, title="The 64K reference limit and multidex"),
            tip("""
                baksmali writes one .smali file per class and turns the package into folders: Lcom/acme/demo/Counter;
                becomes com/acme/demo/Counter.smali and the inner class Lcom/acme/demo/Counter$Snapshot; becomes
                com/acme/demo/Counter$Snapshot.smali. apktool places classes.dex in smali/ and classesN.dex in
                smali_classesN/.
            """, title="Where the .smali files go"),
        ],
        exercises=[
            exercise(
                "dex-files-ex1",
                """
                    Add a method reset()V to the Counter class that sets count back to zero. Reuse the existing
                    field reference.
                """,
                starter="""
                    .method public reset()V
                        .registers 2
                        # store 0 into this.count
                    .end method
                """,
                solution="""
                    .method public reset()V
                        .registers 2
                        const/4 v0, 0x0
                        iput v0, p0, Lcom/acme/demo/Counter;->count:I
                        return-void
                    .end method
                """,
                hint="Load 0 with const/4, then write it with iput using p0 (this) as the object register.",
            ),
            exercise(
                "dex-files-ex2",
                """
                    baksmali produced the file smali_classes2/org/demo/net/Client$Builder.smali. Write the class
                    descriptor of that class and say which DEX file it came from.
                """,
                solution="""
                    The descriptor is Lorg/demo/net/Client$Builder; (an inner class Builder of
                    org.demo.net.Client). Because it sits in smali_classes2, it was disassembled from classes2.dex.
                """,
                hint="Folders become package segments separated by slashes; the $ marks a nested class.",
            ),
        ],
        quiz=[
            question(
                "dex-files-q1",
                "How does a DEX file differ from a JVM .class file?",
                [
                    "It holds many classes that share string, type and member tables",
                    "It holds exactly one class, like a .class file",
                    "It stores native ARM code instead of bytecode",
                    "It contains Java source code",
                ],
                0,
                """
                    A DEX file bundles many classes and deduplicates their constants into shared id tables, which
                    keeps the file small.
                """,
            ),
            question(
                "dex-files-q2",
                "What do the first bytes of a DEX file look like?",
                [
                    "dex, a newline, a three-digit version and a zero byte",
                    "0xCAFEBABE",
                    "PK followed by a zip header",
                    "The class name of the first class",
                ],
                0,
                """
                    The magic is dex\\n followed by a version such as 035 or 039 and a terminating zero byte.
                    0xCAFEBABE is the JVM class file magic, and PK starts zip archives such as APKs.
                """,
            ),
            question(
                "dex-files-q3",
                "What does the 64K limit of a single DEX file count?",
                [
                    "Method references, including methods from libraries and the framework",
                    "Only methods defined in the app's own classes",
                    "Lines of Smali code",
                    "Bytes of bytecode",
                ],
                0,
                """
                    Method references are addressed with a 16-bit index, so at most 65,536 distinct methods can be
                    referenced, whoever defines them.
                """,
            ),
            question(
                "dex-files-q4",
                "Which path does baksmali use for this class?",
                [
                    "com/acme/demo/ui/MainActivity$1.smali",
                    "com.acme.demo.ui.MainActivity.1.smali",
                    "MainActivity$1.java",
                    "com/acme/demo/ui/MainActivity/1.smali",
                ],
                0,
                """
                    Package segments become folders and the simple name, including the $1 of an anonymous class,
                    becomes the file name with a .smali extension.
                """,
                code="""
                    .class final Lcom/acme/demo/ui/MainActivity$1;
                    .super Ljava/lang/Object;
                """,
            ),
            question(
                "dex-files-q5",
                "Which DEX format version first added invoke-polymorphic and invoke-custom?",
                ["035", "037", "038", "040"],
                2,
                """
                    Version 038 (Android 8.0) introduced invoke-polymorphic and invoke-custom along with method
                    handle data. 037 added default methods, and 039 added const-method-handle and const-method-type.
                """,
            ),
        ],
        related=["iget", "iput", "add-int/lit8", "invoke-virtual", "return-void"],
    ),
    lesson(
        id="classes",
        level=L,
        order=5,
        title="Classes",
        summary="""
            Every .smali file describes one class. Learn the header directives .class, .super, .source and
            .implements, how access flags such as public, final, abstract, interface and enum are written, and
            how nested and anonymous classes are named.
        """,
        minutes=10,
        sections=[
            text("""
                A Smali file starts with a short header:
                - .class with the access flags and the class descriptor, e.g. .class public final Lcom/acme/demo/User;
                - .super with exactly one superclass descriptor (Ljava/lang/Object; when nothing else is extended),
                - optionally .source with the original file name, used in stack traces,
                - zero or more .implements lines, one per interface.

                After the header come fields (.field) and methods (.method). baksmali groups them under comments
                such as # static fields, # instance fields, # direct methods and # virtual methods.
            """, title="The class header"),
            code("""
                .class public final Lcom/acme/demo/User;
                .super Ljava/lang/Object;
                .source "User.java"

                # interfaces
                .implements Ljava/lang/Comparable;

                # static fields
                .field public static final MAX_NAME:I = 0x20

                # instance fields
                .field private final name:Ljava/lang/String;

                # direct methods
                .method public constructor <init>(Ljava/lang/String;)V
                    .registers 2

                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    iput-object p1, p0, Lcom/acme/demo/User;->name:Ljava/lang/String;
                    return-void
                .end method

                # virtual methods
                .method public compareTo(Ljava/lang/Object;)I
                    .registers 4

                    check-cast p1, Lcom/acme/demo/User;
                    iget-object v0, p0, Lcom/acme/demo/User;->name:Ljava/lang/String;
                    iget-object v1, p1, Lcom/acme/demo/User;->name:Ljava/lang/String;
                    invoke-virtual {v0, v1}, Ljava/lang/String;->compareTo(Ljava/lang/String;)I
                    move-result v0
                    return v0
                .end method
            """, title="A small class with every header directive"),
            java("""
                public final class User implements Comparable {
                    public static final int MAX_NAME = 32;
                    private final String name;

                    public User(String name) {
                        this.name = name;
                    }

                    public int compareTo(Object other) {
                        return name.compareTo(((User) other).name);
                    }
                }
            """, title="Equivalent Java"),
            text("""
                Access flags are written as words before the descriptor. The common class flags are public, final,
                abstract, interface, annotation, enum and synthetic. An interface is written as abstract interface,
                because the DEX format stores both flags. An enum class carries the enum flag and extends
                Ljava/lang/Enum;. A synthetic class was generated by the compiler and has no counterpart in the
                source, such as a lambda holder.

                Class-level private, protected and static do not appear on the .class line: in DEX those flags
                exist only for nested classes and are recorded in an InnerClass annotation instead.
            """, title="Access flags"),
            code("""
                .class public abstract interface Lcom/acme/demo/Shape;
                .super Ljava/lang/Object;

                .method public abstract area()D
                .end method
            """, title="An interface"),
            tip("""
                Nested classes get their own file and a name joined with $: an inner class Entry of
                com.acme.demo.Cache is Lcom/acme/demo/Cache$Entry;, and the first anonymous class inside it is
                Lcom/acme/demo/Cache$1;. A non-static inner class also holds a synthetic field, usually named this$0,
                that points to the outer instance.
            """, title="Inner and anonymous classes"),
        ],
        exercises=[
            exercise(
                "classes-ex1",
                """
                    Write the header and constructor for a public class com.acme.demo.Task that extends
                    com.acme.demo.BaseTask and implements java.lang.Runnable. The constructor takes no arguments and
                    only calls the superclass constructor. Include a run()V method that does nothing.
                """,
                solution="""
                    .class public Lcom/acme/demo/Task;
                    .super Lcom/acme/demo/BaseTask;
                    .implements Ljava/lang/Runnable;

                    .method public constructor <init>()V
                        .registers 1
                        invoke-direct {p0}, Lcom/acme/demo/BaseTask;-><init>()V
                        return-void
                    .end method

                    .method public run()V
                        .registers 1
                        return-void
                    .end method
                """,
                hint="The constructor is named <init>, carries the constructor flag and calls the super constructor with invoke-direct.",
            ),
            exercise(
                "classes-ex2",
                """
                    Declare an enum class com.acme.demo.Level with one static final field LOW of its own type. Only
                    the header and the field are needed.
                """,
                solution="""
                    .class public final enum Lcom/acme/demo/Level;
                    .super Ljava/lang/Enum;

                    .field public static final enum LOW:Lcom/acme/demo/Level;
                """,
                hint="Enum classes extend Ljava/lang/Enum; and both the class and its constants carry the enum flag.",
            ),
        ],
        quiz=[
            question(
                "classes-q1",
                "How many .super directives can a class have?",
                ["Exactly one", "Zero or more", "One per interface", "Two: one for the class and one for its outer class"],
                0,
                """
                    Every class has exactly one direct superclass (java.lang.Object itself is the only exception).
                    Interfaces are listed with separate .implements lines.
                """,
            ),
            question(
                "classes-q2",
                "What kind of type does this header declare?",
                ["An interface", "An enum", "An abstract class that cannot have methods", "An annotation"],
                0,
                """
                    The interface flag marks an interface; DEX stores it together with abstract, which is why
                    baksmali prints both words.
                """,
                code="""
                    .class public abstract interface Lcom/acme/demo/Listener;
                    .super Ljava/lang/Object;
                """,
            ),
            question(
                "classes-q3",
                "What is the descriptor of the anonymous class created first inside com.acme.demo.Player?",
                ["Lcom/acme/demo/Player$1;", "Lcom/acme/demo/Player.1;", "Lcom/acme/demo/Player/Anonymous;", "Lcom/acme/demo/1$Player;"],
                0,
                """
                    Anonymous classes are numbered in order and joined to the outer name with $, giving
                    Lcom/acme/demo/Player$1;.
                """,
            ),
            question(
                "classes-q4",
                "What is the .source directive used for?",
                [
                    "Recording the original source file name for stack traces and debugging",
                    "Pointing the assembler at the Java file to compile",
                    "Declaring the package of the class",
                    "Importing another class",
                ],
                0,
                """
                    .source only stores a file name such as "User.java". It is optional and does not affect how
                    the code behaves; obfuscated builds often remove or rename it.
                """,
            ),
            question(
                "classes-q5",
                "A class carries the synthetic flag. What does that tell you?",
                [
                    "The compiler generated it; it has no direct counterpart in the source",
                    "It is loaded from native code",
                    "It cannot be instantiated",
                    "It was added by an obfuscator to hide code",
                ],
                0,
                """
                    synthetic marks compiler-generated items, for example lambda classes or accessors. It is a
                    normal part of compiled code, not a sign of tampering.
                """,
            ),
        ],
        related=["invoke-direct", "iput-object", "iget-object", "check-cast", "return-void"],
    ),
]

FLASHCARDS += [
    card("fc-b1-03", "Runtime", "Which on-device tool compiles DEX bytecode ahead of time in ART?",
         "dex2oat. It reads DEX files and writes OAT files containing native code, guided by a compilation filter such as speed-profile."),
    card("fc-b1-04", "Runtime", "In which Android release did ART fully replace Dalvik?",
         "Android 5.0 (Lollipop). Android 4.4 offered ART only as a developer option."),
    card("fc-b1-05", "Runtime", "What hybrid execution model did Android 7.0 introduce?",
         "Interpretation plus a JIT that records hot methods in a profile, followed by profile-guided AOT compilation by dex2oat when the device is idle and charging."),
    card("fc-b1-06", "DEX", "What are the 8 magic bytes at the start of a DEX file?",
         "The text dex, a newline, a three-digit version and a zero byte, e.g. dex\\n039\\0."),
    card("fc-b1-07", "DEX", "What does the 64K limit of a DEX file refer to?",
         "A single DEX file can reference at most 65,536 methods (16-bit index), counting library and framework methods as well. Fields and types have the same limit."),
    card("fc-b1-08", "DEX", "How are additional DEX files in a multidex APK named?",
         "classes.dex, then classes2.dex, classes3.dex and so on. apktool disassembles classesN.dex into smali_classesN/."),
    card("fc-b1-09", "Formats", "How large is a Dalvik code unit?",
         "16 bits. Instructions are 1 to 5 code units long, and branch offsets are counted in code units."),
    card("fc-b1-10", "Registers", "Is Dalvik bytecode stack-based or register-based?",
         "Register-based. Each method declares a fixed set of virtual registers and instructions name them explicitly, unlike the JVM's operand stack.",
         code="add-int v0, v1, v2"),
    card("fc-b1-11", "Directives", "How does a class declare that it implements an interface?",
         "With one .implements line per interface, placed after .super.",
         code=".implements Ljava/lang/Runnable;"),
    card("fc-b1-12", "Directives", "What does .source record?",
         "The name of the original source file (e.g. \"User.java\"), used in stack traces. It is optional and has no effect on behaviour."),
    card("fc-b1-13", "Descriptors", "What is the descriptor of the nested class Entry inside com.acme.demo.Cache?",
         "Lcom/acme/demo/Cache$Entry; — nested and anonymous classes are joined to the outer name with $."),
    card("fc-b1-14", "Directives", "How does an interface look on the .class line?",
         "The flags abstract and interface appear together, because DEX stores both for interfaces.",
         code="""
            .class public abstract interface Lcom/acme/demo/Shape;
            .super Ljava/lang/Object;
         """),
    card("fc-b1-15", "DEX", "Which DEX version introduced invoke-polymorphic and invoke-custom?",
         "Version 038, supported from Android 8.0. Version 039 (Android 9) added const-method-handle and const-method-type."),
]
