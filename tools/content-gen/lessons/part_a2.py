"""Advanced track, part 2 (orders 25-28)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "advanced"

LESSONS = [
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="register-allocation",
        level=L,
        order=25,
        title="Register Allocation",
        summary="""
            Why compiled Smali uses the register numbers it does: how D8 assigns Java locals to registers, reuses
            them across live ranges, keeps parameters at the top of the frame, and inserts move/from16 shuffles
            when an instruction can only address v0-v15.
        """,
        minutes=14,
        sections=[
            text("""
                Every Dalvik method runs in a frame of registers_size registers. The last ins_size registers hold
                the incoming arguments (with `this` first for instance methods); everything below them is free for
                local values and temporaries. Smali's p-names are simply aliases for those top registers, so in a
                method with .registers 6 and two argument words, p0 is v4 and p1 is v5.

                Java source variables do not map one-to-one onto registers. javac assigns local variable slots for
                the JVM, but D8 (and the older dx tool before it) rebuilds the code in its own intermediate form and
                runs a register allocator over it. D8 uses a linear-scan allocator: it computes how long each value
                is live and packs values whose live ranges do not overlap into the same register.
            """, title="Frames, parameters and the allocator"),
            code("""
                .method public static sumOfSquares(II)I
                    .registers 4

                    mul-int v0, p0, p0
                    mul-int v1, p1, p1
                    add-int/2addr v0, v1
                    return v0
                .end method
            """, title="Two temporaries, then reuse"),
            java("""
                static int sumOfSquares(int a, int b) {
                    int aa = a * a;
                    int bb = b * b;
                    return aa + bb;
                }
            """, title="Equivalent Java"),
            text("""
                Many instruction formats store register numbers in 4 bits, which means they can only name v0-v15:
                - 12x (move vA, vB, the /2addr arithmetic forms, array-length),
                - 22c (iget/iput, instance-of, new-array),
                - 22t (if-eq, if-lt and the other two-register comparisons),
                - 35c (invoke-* and filled-new-array with up to five argument registers).

                When a value the method needs lives in a higher register, the compiler has two options: copy it
                down first with a wider move (move/from16, move-object/from16 and move-wide/from16 use the 22x
                format: an 8-bit destination and a 16-bit source), or, for calls, use a /range invoke (format 3rc)
                whose arguments must be consecutive registers. The allocator therefore tries to give the busiest
                values low numbers, and large methods often begin by shuffling parameters into low registers.
            """, title="Why 4-bit formats shape the output"),
            code("""
                .method public describe()Ljava/lang/String;
                    .registers 18

                    # 17 locals (v0..v16), so `this` is p0 = v17 and cannot appear in a 22c iget.
                    move-object/from16 v0, p0
                    iget-object v1, v0, Lcom/acme/demo/User;->name:Ljava/lang/String;
                    return-object v1
                .end method
            """, title="A parameter shuffle in a large frame"),
            code("""
                .method public static report(II)V
                    .registers 3
                    .param p0, "count"    # I
                    .param p1, "limit"    # I

                    .line 5
                    sub-int v0, p1, p0
                    .local v0, "remaining":I
                    .line 6
                    invoke-static {v0}, Lcom/acme/demo/Console;->print(I)V
                    .end local v0    # "remaining":I

                    .line 8
                    mul-int/lit8 v0, p0, 0x2
                    .local v0, "doubled":I
                    .line 9
                    invoke-static {v0}, Lcom/acme/demo/Console;->print(I)V

                    .line 10
                    return-void
                .end method
            """, title="One register, two variables"),
            tip("""
                Debug info tells you which source variable a register holds at each point. A .local directive
                starts a variable's range in that register and .end local closes it. When you see the same vN
                announced with different names, the allocator reused it because the two live ranges never
                overlapped. Release builds processed by R8 usually drop .local entries, so you have to track the
                values yourself.
            """, title="Reading .local and .end local"),
            warning("""
                When you add a local to hand-edited Smali, raise .registers (or .locals). With .registers, that
                also moves every p-register up by one, so always refer to arguments by their p-names; code that
                hard-codes the old v-number of a parameter silently reads the wrong value.
            """, title="Editing register counts"),
        ],
        exercises=[
            exercise(
                "register-allocation-ex1",
                """
                    This instance method has .registers 20 and one int parameter, so `this` is p0 = v18 and the
                    int is p1 = v19. The iget-object below does not assemble because 22c only encodes v0-v15. Fix
                    it without changing .registers.
                """,
                starter="""
                    .method public label(I)Ljava/lang/String;
                        .registers 20
                        iget-object v0, p0, Lcom/acme/demo/Item;->title:Ljava/lang/String;
                        return-object v0
                    .end method
                """,
                check_starter=False,
                solution="""
                    .method public label(I)Ljava/lang/String;
                        .registers 20
                        move-object/from16 v0, p0
                        iget-object v0, v0, Lcom/acme/demo/Item;->title:Ljava/lang/String;
                        return-object v0
                    .end method
                """,
                hint="""
                    move-object/from16 (format 22x) can read any register up to v65535 and write v0-v255. Copy
                    `this` into a low register first; the same register can then receive the field value.
                """,
            ),
            exercise(
                "register-allocation-ex2",
                """
                    Rewrite this method so it uses the smallest possible frame. It should still return
                    p0 + p1 + p2.
                """,
                starter="""
                    .method public static sum3(III)I
                        .registers 6
                        add-int v0, p0, p1
                        add-int v1, v0, p2
                        return v1
                    .end method
                """,
                solution="""
                    .method public static sum3(III)I
                        .registers 4
                        add-int v0, p0, p1
                        add-int/2addr v0, p2
                        return v0
                    .end method
                """,
                hint="""
                    The intermediate sum is dead as soon as the final sum is computed, so one local register is
                    enough. add-int/2addr accumulates into its first operand.
                """,
            ),
        ],
        quiz=[
            question(
                "register-allocation-q1",
                "In this method, which register is p1?",
                ["v4", "v3", "v5", "v1"],
                0,
                """
                    The arguments need 3 words (an int plus a two-register long), so they occupy v3-v5. p0 is v3
                    and the long p1 is the pair v4/v5, addressed by its first register, v4.
                """,
                code="""
                    .method public static f(IJ)V
                        .registers 6
                        return-void
                    .end method
                """,
            ),
            question(
                "register-allocation-q2",
                "Why does D8 emit move-object/from16 v0, p0 before an iget-object in a method with 20 registers?",
                [
                    "iget-object uses format 22c, whose register fields are only 4 bits wide",
                    "Fields can only be read through v0",
                    "p-registers are read-only",
                    "It is required to null-check the object",
                ],
                0,
                """
                    In format 22c both register operands are nibbles, so only v0-v15 can be named. With 20
                    registers, p0 is v19 and must be copied into a low register first.
                """,
            ),
            question(
                "register-allocation-q3",
                "What does it tell you when debug info shows `.local v2, \"i\":I` and later `.local v2, \"name\":Ljava/lang/String;`?",
                [
                    "The allocator reused v2 because the two variables' live ranges do not overlap",
                    "v2 is a wide register pair",
                    "The method will fail verification",
                    "The string is converted from the int",
                ],
                0,
                """
                    Registers are reused freely across non-overlapping live ranges, even with different types.
                    The verifier tracks the type per instruction, so this is perfectly legal.
                """,
            ),
            question(
                "register-allocation-q4",
                "A call needs six argument registers. Which form must the compiler use?",
                [
                    "A /range invoke (format 3rc) over consecutive registers",
                    "Two consecutive 35c invokes",
                    "invoke-virtual with a 4-bit count of 6",
                    "move-result-wide to pack two arguments",
                ],
                0,
                """
                    Format 35c holds at most five argument registers. Six or more argument words require
                    invoke-*/range, which takes a first register and a count, so the arguments must sit in a
                    contiguous block.
                """,
            ),
            question(
                "register-allocation-q5",
                "Which format does move/from16 use?",
                ["22x", "12x", "32x", "21s"],
                0,
                """
                    22x: an 8-bit destination (vAA) and a 16-bit source (vBBBB). move/16 uses 32x, with 16 bits
                    for both registers.
                """,
            ),
        ],
        related=["move/from16", "move-object/from16", "move-wide/from16", "move/16", "invoke-virtual/range", "iget-object"],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="modern-instructions",
        level=L,
        order=26,
        title="Modern Android Instructions",
        summary="""
            Method handles, call sites and the instructions that use them: invoke-polymorphic, invoke-custom,
            const-method-handle and const-method-type. Plus how D8 desugaring turns lambdas, string concatenation
            and interface methods into Smali that runs on older devices.
        """,
        minutes=16,
        sections=[
            text("""
                The original Dalvik instruction set (DEX version 035) has no notion of java.lang.invoke. Later DEX
                versions added it in two steps:
                - DEX 038 (Android 8.0, API 26) added invoke-polymorphic and invoke-custom, plus the method
                  handle and call site sections of the file.
                - DEX 039 (Android 9, API 28) added const-method-handle and const-method-type.

                (DEX 037, Android 7.0, sits in between and only added support for default interface methods.) A
                runtime refuses DEX files whose version is newer than it understands, which is why D8 picks the
                file version from your minSdk and only emits these instructions when the minimum API allows it.
            """, title="Which DEX version brought what"),
            code("""
                .method public static parse(Ljava/lang/String;)I
                    .registers 3

                    const-method-handle v0, invoke-static@Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
                    invoke-polymorphic {v0, p0}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (Ljava/lang/String;)I
                    move-result v0
                    return v0
                .end method

                .method public static intToString()Ljava/lang/invoke/MethodType;
                    .registers 1

                    const-method-type v0, (I)Ljava/lang/String;
                    return-object v0
                .end method
            """, title="Method handle constants and a polymorphic call"),
            java("""
                static int parse(String s) throws Throwable {
                    MethodHandle h = MethodHandles.lookup().findStatic(
                            Integer.class, "parseInt", MethodType.methodType(int.class, String.class));
                    return (int) h.invokeExact(s);
                }
            """, title="Roughly equivalent Java"),
            text("""
                invoke-polymorphic (format 45cc, or 4rcc for /range) calls a signature-polymorphic method such as
                MethodHandle.invokeExact. It carries two references: the declared method, whose generic
                ([Ljava/lang/Object;)Ljava/lang/Object; signature is fixed, and a separate prototype that
                describes the real argument and return types at this call site. The result, if any, is read with
                the usual move-result* instruction.

                invoke-custom (35c, or 3rc for /range) resolves a call site the first time it executes: the runtime
                calls the bootstrap method named in the call site item, which must return a CallSite, and then
                invokes that CallSite's target with the instruction's arguments. It is the DEX counterpart of the
                JVM's invokedynamic.
            """, title="invoke-polymorphic and invoke-custom"),
            code("""
                .method public static schedule(Ljava/util/concurrent/Executor;Ljava/lang/String;)V
                    .registers 3

                    new-instance v0, Lcom/acme/demo/Jobs$$ExternalSyntheticLambda0;
                    invoke-direct {v0, p1}, Lcom/acme/demo/Jobs$$ExternalSyntheticLambda0;-><init>(Ljava/lang/String;)V
                    invoke-interface {p0, v0}, Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V
                    return-void
                .end method
            """, title="A desugared capturing lambda"),
            text("""
                javac compiles a lambda to an invokedynamic that bootstraps through LambdaMetafactory. D8 does not
                pass that through: it desugars lambdas and method references on every API level. Each lambda
                becomes a synthetic class named like Jobs$$ExternalSyntheticLambda0 that implements the functional
                interface, stores captured values in fields set by its constructor, and forwards the interface
                method to a synthetic method holding the lambda body (typically lambda$schedule$0 in the original
                class). Non-capturing lambdas are usually shared through a static instance instead of being
                allocated at every use.

                String concatenation is similar. javac 9+ emits an invokedynamic to
                StringConcatFactory.makeConcatWithConstants; D8 normally rewrites it into a StringBuilder chain, so
                that is what app DEX files usually contain. A raw call site looks like this:
            """, title="Lambdas and string concatenation"),
            code("""
                .method public static label(Ljava/lang/String;I)Ljava/lang/String;
                    .registers 2

                    invoke-custom {p0, p1}, call_site_0("makeConcatWithConstants", (Ljava/lang/String;I)Ljava/lang/String;, "\\u0001 = \\u0001")@Ljava/lang/invoke/StringConcatFactory;->makeConcatWithConstants(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;
                    move-result-object p0
                    return-object p0
                .end method
            """, title="An invoke-custom call site (not desugared)"),
            tip("""
                Other desugarings you will meet in Smali:
                - Default and static interface methods with minSdk below 24 are moved into a companion class
                  named like Shape$-CC and called with invoke-static.
                - try-with-resources works on all API levels; D8 rewrites the Throwable.addSuppressed call it
                  relies on where the platform lacks it.
                - Core library desugaring (coreLibraryDesugaring in Gradle) rewrites references to newer Java APIs
                  such as java.time into a bundled copy whose package starts with j$, e.g. Lj$/time/LocalDate;.
                - MethodHandle.invoke and invokeExact cannot be desugared, so code that uses them needs
                  minSdk 26.
            """, title="Desugaring in practice"),
        ],
        exercises=[
            exercise(
                "modern-instructions-ex1",
                """
                    Write a static method stringLength()Ljava/lang/invoke/MethodHandle; that returns a method
                    handle for the instance method String.length() using const-method-handle.
                """,
                starter="""
                    .method public static stringLength()Ljava/lang/invoke/MethodHandle;
                        .registers 1
                        # load the handle into v0 and return it
                    .end method
                """,
                solution="""
                    .method public static stringLength()Ljava/lang/invoke/MethodHandle;
                        .registers 1
                        const-method-handle v0, invoke-instance@Ljava/lang/String;->length()I
                        return-object v0
                    .end method
                """,
                hint="""
                    The handle kind for a virtual method is written invoke-instance@ followed by the method
                    reference. const-method-handle needs DEX 039 (API 28).
                """,
            ),
            exercise(
                "modern-instructions-ex2",
                """
                    The invoke-custom version of label(String, int) above cannot run below API 26. Rewrite it with
                    StringBuilder so it works on any API level, producing the same text ("<name> = <value>").
                """,
                solution="""
                    .method public static label(Ljava/lang/String;I)Ljava/lang/String;
                        .registers 4
                        new-instance v0, Ljava/lang/StringBuilder;
                        invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                        invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        const-string v1, " = "
                        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
                        invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v0
                        return-object v0
                    .end method
                """,
                hint="""
                    Each \\u0001 in the recipe is one dynamic argument and the rest is constant text. Use the
                    append(I) overload for the int.
                """,
            ),
            exercise(
                "modern-instructions-ex3",
                """
                    In the schedule() example, where does the body of the lambda actually live, and how does the
                    Executor end up running it?
                """,
                solution="""
                    The body is a synthetic method in Jobs itself (for example a static lambda$schedule$0 that
                    takes the captured String). Jobs$$ExternalSyntheticLambda0 implements Runnable, keeps the
                    captured String in a field set by its constructor, and its run() method calls the synthetic
                    method with that field. The Executor only ever sees a Runnable and calls run().
                """,
                hint="Look for a class implementing Runnable and a synthetic method whose name starts with lambda$.",
            ),
        ],
        quiz=[
            question(
                "modern-instructions-q1",
                "Which DEX version (and Android release) introduced const-method-handle?",
                ["039, Android 9 (API 28)", "038, Android 8.0 (API 26)", "037, Android 7.0 (API 24)", "035, all versions"],
                0,
                """
                    DEX 038 (API 26) added invoke-polymorphic and invoke-custom; DEX 039 (API 28) added
                    const-method-handle and const-method-type.
                """,
            ),
            question(
                "modern-instructions-q2",
                "What is special about invoke-polymorphic compared with invoke-virtual?",
                [
                    "It carries an extra prototype reference describing the call site's actual types",
                    "It can only call static methods",
                    "It never returns a value",
                    "It uses 16-bit register fields for all arguments",
                ],
                0,
                """
                    Format 45cc stores both a method reference (e.g. MethodHandle.invokeExact) and a proto
                    reference with the real argument and return types used at this call site.
                """,
            ),
            question(
                "modern-instructions-q3",
                "You see this in an app built with D8. What produced it?",
                [
                    "A desugared lambda or method reference",
                    "An anonymous inner class written in Java source",
                    "A ProGuard keep rule",
                    "Kotlin coroutines",
                ],
                0,
                """
                    D8 desugars lambdas into synthetic classes named Outer$$ExternalSyntheticLambdaN. Anonymous
                    classes written in source keep names like Outer$1.
                """,
                code="new-instance v0, Lcom/acme/demo/Jobs$$ExternalSyntheticLambda0;",
            ),
            question(
                "modern-instructions-q4",
                "With minSdk 21, where does D8 put the body of a static method declared in an interface Shape?",
                [
                    "In a companion class such as Shape$-CC, called with invoke-static",
                    "It stays in Shape and is called with invoke-interface",
                    "It is inlined everywhere and removed",
                    "It is rejected; static interface methods need minSdk 26",
                ],
                0,
                """
                    Before API 24 the runtime cannot run code in interfaces, so D8 moves static and default
                    method bodies into a $-CC companion class and rewrites the call sites.
                """,
            ),
            question(
                "modern-instructions-q5",
                "How is the result of an invoke-custom call read?",
                [
                    "With a move-result* instruction immediately after it",
                    "From the CallSite object returned by the bootstrap method",
                    "It is always written to v0",
                    "invoke-custom cannot return a value",
                ],
                0,
                """
                    Like every invoke, invoke-custom leaves its result in the hidden result slot; the next
                    instruction must be the matching move-result, move-result-wide or move-result-object.
                """,
            ),
        ],
        related=["invoke-custom", "invoke-custom/range", "invoke-polymorphic", "invoke-polymorphic/range", "const-method-handle", "const-method-type"],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="obfuscated-smali",
        level=L,
        order=27,
        title="Reading Obfuscated Smali",
        summary="""
            Release builds processed by R8 have short renamed identifiers, inlined and merged code, and synthetic
            helper classes. Learn what each transformation looks like in Smali and a systematic way to make sense
            of your own minified builds, using mapping.txt when you have it.
        """,
        minutes=15,
        sections=[
            warning("""
                This lesson is about understanding builds you own or are explicitly authorised to analyse: your
                own release APKs, crash triage for your team's app, or an audit you were contracted to do. Do not
                use these techniques to defeat licensing, authentication, payments or other protections, and
                respect the terms and laws that apply to you.
            """, title="Scope"),
            text("""
                R8 shrinks, optimises and obfuscates. Obfuscation renames classes, fields and methods that are not
                protected by keep rules to short names such as a, b, c or aa, and can move classes into short or
                flattened packages. Some names always survive:
                - entry points the system must find by name (components declared in the manifest, classes kept
                  for reflection or serialization),
                - everything outside your app: framework and library types such as Landroid/app/Activity; or
                  Ljava/lang/String; and the methods you call on them.

                Those surviving names are your anchors.
            """, title="What R8 changes"),
            code("""
                .class public final La/b/c;
                .super Ljava/lang/Object;
                .source "SourceFile"

                .field private final a:Landroid/content/SharedPreferences;

                .method public final a(Ljava/lang/String;)Z
                    .registers 4

                    iget-object v0, p0, La/b/c;->a:Landroid/content/SharedPreferences;
                    const/4 v1, 0x0
                    invoke-interface {v0, p1, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result p1
                    return p1
                .end method
            """, title="A minified class"),
            java("""
                // Your notes after renaming, not decompiler output.
                public final class FeatureFlags {
                    private final SharedPreferences prefs;

                    public final boolean isEnabled(String key) {
                        return prefs.getBoolean(key, false);
                    }
                }
            """, title="The same class, renamed by hand"),
            text("""
                Optimisation changes the shape of the code, not just the names:
                - Inlining: small methods and getters disappear into their callers.
                - Class merging: a class can be merged into its only subclass or with similar classes.
                - Enum unboxing: simple enums may become plain ints.
                - Debug info: .local entries are removed and the source file is often reported as "SourceFile";
                  .line entries remain only so stack traces can be mapped back.
                - Register reuse: as above, a parameter register like p1 can be overwritten once it is dead.

                Synthetic members and classes also appear: access$000-style static accessors that older javac
                versions generated for private members used by nested classes (newer D8 versions produce names
                containing -$$Nest$ when desugaring nest-based access), $$ExternalSyntheticLambdaN classes for
                lambdas, and $$ExternalSyntheticOutlineN classes holding instruction sequences that R8 outlined
                into shared static methods to save space.
            """, title="Optimisations and synthetic code"),
            tip("""
                A workable routine:
                - Start from anchors: const-string literals, framework calls (getBoolean, startActivity, Log),
                  and manifest components.
                - Follow types: field and parameter types tell you what a renamed class holds.
                - Rename as you go and keep notes; meaning builds up across classes.
                - For your own builds, use the mapping file R8 writes (by default
                  app/build/outputs/mapping/release/mapping.txt) and the retrace tool to turn obfuscated stack
                  traces back into real names. Archive the mapping for every release you ship.
            """, title="Strategy"),
        ],
        exercises=[
            exercise(
                "obfuscated-smali-ex1",
                """
                    Describe what this method from your own release build does and propose meaningful names for
                    the class, the field and the method.
                """,
                starter="""
                    .method public final b()Ljava/lang/String;
                        .registers 4
                        iget-object v0, p0, La/b/d;->c:Landroid/content/Context;
                        invoke-virtual {v0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
                        move-result-object v0
                        const-string v1, "build: "
                        invoke-virtual {v1, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v0
                        return-object v0
                    .end method
                """,
                solution="""
                    It reads a Context stored in field c, asks it for the package name and returns
                    "build: " + packageName. Reasonable names: field c -> context, method b() -> buildLabel(), and
                    class La/b/d; -> something like AppInfo or BuildInfoProvider, depending on its other members.
                """,
                hint="The framework calls (Context.getPackageName, String.concat) and the string literal are never renamed.",
            ),
            exercise(
                "obfuscated-smali-ex2",
                """
                    Your crash report shows a frame in a.b.c.a(String). The mapping.txt from that release
                    contains the lines below. What is the real method?
                """,
                starter="""
                    com.acme.demo.flags.FeatureFlags -> a.b.c:
                        android.content.SharedPreferences prefs -> a
                        boolean isEnabled(java.lang.String) -> a
                """,
                solution="""
                    a.b.c is com.acme.demo.flags.FeatureFlags, and its method a taking a String is
                    boolean isEnabled(String). The field a in the same class is prefs. Running retrace with this
                    mapping file over the full stack trace does this translation automatically.
                """,
                hint="Each class line maps original -> obfuscated; the indented lines below it are its members.",
            ),
        ],
        quiz=[
            question(
                "obfuscated-smali-q1",
                "Which names does R8 never rename in your app's DEX?",
                [
                    "Framework and library classes you call, such as Landroid/content/SharedPreferences;",
                    "All public methods",
                    "All fields of type String",
                    "Every class in the default package",
                ],
                0,
                """
                    R8 can only rename code it is processing. References to the Android framework and other
                    platform classes must keep their real names, which makes them reliable anchors.
                """,
            ),
            question(
                "obfuscated-smali-q2",
                "What does a class named Lcom/acme/demo/a$$ExternalSyntheticOutline0; usually contain?",
                [
                    "Shared static methods holding instruction sequences R8 outlined to save space",
                    "The original source code",
                    "Encrypted strings",
                    "The app's keep rules",
                ],
                0,
                """
                    Outlining extracts repeated code sequences into a static helper so each caller can replace
                    them with one invoke-static.
                """,
            ),
            question(
                "obfuscated-smali-q3",
                "Is writing the result into p1 here valid?",
                [
                    "Yes; p1 is an ordinary register and is no longer needed after the call",
                    "No; parameter registers are read-only",
                    "No; move-result can only target v-registers",
                    "Only in static methods",
                ],
                0,
                """
                    Parameter registers are normal frame registers. Once the argument is dead the allocator may
                    reuse its register, which minified code does often.
                """,
                code="""
                    .method public final a(Ljava/lang/String;)Z
                        .registers 4
                        iget-object v0, p0, La/b/c;->a:Landroid/content/SharedPreferences;
                        const/4 v1, 0x0
                        invoke-interface {v0, p1, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                        move-result p1
                        return p1
                    .end method
                """,
            ),
            question(
                "obfuscated-smali-q4",
                "What are access$000-style static methods?",
                [
                    "Synthetic accessors older javac versions generated so nested classes could reach private members",
                    "Methods R8 created by merging classes",
                    "Native methods implemented in C++",
                    "Methods that were obfuscated twice",
                ],
                0,
                """
                    Before Java 11 nestmates, a nested class could not access its outer class's private members
                    directly, so javac generated package-visible static accessors named access$NNN.
                """,
            ),
            question(
                "obfuscated-smali-q5",
                "How do you translate an obfuscated stack trace from your own release build?",
                [
                    "Run retrace with the mapping.txt R8 produced for that exact build",
                    "Rebuild the app and compare method names",
                    "Run baksmali with the --deobfuscate option",
                    "Use any mapping.txt from a recent build",
                ],
                0,
                """
                    Names are assigned per build, so only that build's mapping file is correct. Keep it with
                    every release you ship.
                """,
            ),
        ],
        related=["const-string", "invoke-interface", "invoke-static", "iget-object", "move-result", "check-cast"],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="debugging-validation",
        level=L,
        order=28,
        title="Debugging & Validation",
        summary="""
            Make edited Smali trustworthy: what the debug directives mean, how to round-trip code through
            baksmali and smali, how to read assembler errors and VerifyError messages, and how to use the
            Playground diagnostics in this app before anything reaches a device.
        """,
        minutes=15,
        sections=[
            text("""
                Debug directives come from the DEX debug_info_item and have no effect on what the code computes:
                - .line N maps the following instructions to source line N (used in stack traces and debuggers).
                - .param pN, "name" names a parameter.
                - .local vN, "name":Type starts a local variable's range in vN; .end local vN ends it; and
                  .restart local vN reopens a variable that was ended earlier, for example after a branch.
                - .prologue and .epilogue mark where the method's setup ends and its exit code begins; you
                  rarely see them in modern output.

                Deleting them keeps the code working but loses line numbers and variable names.
            """, title="Debug directives"),
            code("""
                .method public static clamp(III)I
                    .registers 3
                    .param p0, "value"    # I
                    .param p1, "min"    # I
                    .param p2, "max"    # I

                    .line 12
                    if-ge p0, p1, :cond_3
                    .line 13
                    return p1

                    .line 15
                    :cond_3
                    if-le p0, p2, :cond_6
                    .line 16
                    return p2

                    .line 18
                    :cond_6
                    return p0
                .end method
            """, title="A method with line information"),
            text("""
                A round trip is the simplest validation: disassemble with `baksmali d classes.dex -o out`, edit,
                then reassemble with `smali a out -o classes.dex` (apktool does the same for whole APKs with
                `apktool d` and `apktool b`). The result is functionally equivalent, not byte-identical:
                string and type ordering, payload placement and debug info encoding may differ.

                The assembler catches syntax and encoding problems: an unknown mnemonic, a missing .end method,
                an undefined label, a literal that does not fit (const/4 accepts only -8 to 7), a register
                outside the range of a 4-bit format, or a method with no instructions. It does not check types
                or data flow; that is the verifier's job.
            """, title="Round trips and assembler errors"),
            warning("""
                ART verifies every method before running it. Hard failures make the class unusable and throw
                java.lang.VerifyError when it is loaded; the message names the class, the method, the offset in
                code units (like [0x4]) and the reason. Typical causes:
                - register type mismatch, such as passing an int register where a reference is expected or using
                  half of a wide pair;
                - reading a register that was never assigned on some path;
                - using an object from new-instance before its <init> constructor ran;
                - a move-result that does not directly follow an invoke;
                - execution that can fall off the end of the code without a return or throw;
                - monitor-enter/monitor-exit that do not pair up, which can force the method to run without
                  compiled code or be rejected.
            """, title="What the verifier rejects"),
            code("""
                .method public static broken()Ljava/lang/String;
                    .registers 1

                    new-instance v0, Ljava/lang/StringBuilder;
                    # Missing: invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                    move-result-object v0
                    return-object v0
                .end method
            """, title="Assembles, but fails verification (uninitialised reference)"),
            tip("""
                Validate in small steps: write a method in the Playground, read the diagnostics panel (it lists
                errors and warnings with line numbers), fix the first error before the rest because later ones are
                often consequences, and only then round-trip the whole class. On a test device, filter logcat for
                VerifyError to see the exact offset the verifier complained about.
            """, title="Use the Playground diagnostics"),
        ],
        exercises=[
            exercise(
                "debugging-validation-ex1",
                """
                    This method assembles but is rejected by the verifier. Find the problem and fix it.
                """,
                starter="""
                    .method public static make()Ljava/lang/String;
                        .registers 2
                        new-instance v0, Ljava/lang/StringBuilder;
                        const-string v1, "ok"
                        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v0
                        return-object v0
                    .end method
                """,
                solution="""
                    .method public static make()Ljava/lang/String;
                        .registers 2
                        new-instance v0, Ljava/lang/StringBuilder;
                        invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                        const-string v1, "ok"
                        invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v0
                        return-object v0
                    .end method
                """,
                hint="new-instance only allocates. What must run before any other method is called on the object?",
            ),
            exercise(
                "debugging-validation-ex2",
                """
                    The assembler rejects this method with "Invalid literal value: 16. Must be between -8 and 7,
                    inclusive." Fix it so it returns 16.
                """,
                starter="""
                    .method public static sixteen()I
                        .registers 1
                        const/4 v0, 0x10
                        return v0
                    .end method
                """,
                check_starter=False,
                solution="""
                    .method public static sixteen()I
                        .registers 1
                        const/16 v0, 0x10
                        return v0
                    .end method
                """,
                hint="const/4 holds a signed 4-bit literal. Pick the next larger const form.",
            ),
            exercise(
                "debugging-validation-ex3",
                """
                    This method assembles, but the verifier reports that execution can fall off the end of the
                    code. Fix it so it returns the absolute value of p0.
                """,
                starter="""
                    .method public static abs(I)I
                        .registers 1
                        if-gez p0, :done
                        neg-int p0, p0
                        :done
                    .end method
                """,
                check_starter=False,
                solution="""
                    .method public static abs(I)I
                        .registers 1
                        if-gez p0, :done
                        neg-int p0, p0
                        :done
                        return p0
                    .end method
                """,
                hint="Every path through a method must end in a return, a throw or a jump back into the code.",
            ),
        ],
        quiz=[
            question(
                "debugging-validation-q1",
                "What happens at runtime if you delete every .line directive from a method?",
                [
                    "The code behaves the same, but stack traces lose line numbers",
                    "The method fails verification",
                    "The method runs slower",
                    "The assembler refuses the file",
                ],
                0,
                """
                    .line entries only populate debug info. They map instructions to source lines for stack
                    traces and debuggers and never change what the code does.
                """,
            ),
            question(
                "debugging-validation-q2",
                "This code assembles. What happens when the class is loaded?",
                [
                    "The verifier rejects it: the StringBuilder is used before <init> runs",
                    "It runs and returns an empty string",
                    "It throws NullPointerException",
                    "Nothing; uninitialised objects are allowed for StringBuilder",
                ],
                0,
                """
                    new-instance produces an uninitialised reference. The only legal use is calling a
                    constructor on it; anything else is a hard verification failure (VerifyError).
                """,
                code="""
                    new-instance v0, Ljava/lang/StringBuilder;
                    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                """,
            ),
            question(
                "debugging-validation-q3",
                "The assembler says \"Invalid register: v17. Must be between v0 and v15, inclusive.\" on an iget line. What fixes it?",
                [
                    "Copy the value into a low register first, e.g. with move-object/from16",
                    "Increase .registers",
                    "Replace iget with iget-wide",
                    "Add a .local directive for v17",
                ],
                0,
                """
                    iget uses format 22c with 4-bit register fields. Move the value to v0-v15 first; adding
                    registers would only push the parameters higher.
                """,
            ),
            question(
                "debugging-validation-q4",
                "Which statement about a baksmali then smali round trip is true?",
                [
                    "The new DEX is functionally equivalent but not necessarily byte-identical",
                    "It always produces a byte-identical file",
                    "It removes all debug information",
                    "It also verifies the bytecode",
                ],
                0,
                """
                    The assembler may order and encode items differently. It also does no type checking, so a
                    successful round trip does not prove the code will verify.
                """,
            ),
            question(
                "debugging-validation-q5",
                "What does `.end local v3` tell you?",
                [
                    "The named variable in v3 goes out of scope; v3 may be reused afterwards",
                    "v3 must not be written again in this method",
                    "The method returns v3",
                    "v3 is the last register of the frame",
                ],
                0,
                """
                    It closes the debug range of the variable. The register itself is free for the allocator to
                    reuse, often for a different variable.
                """,
            ),
        ],
        related=["new-instance", "invoke-direct", "move-result-object", "return", "monitor-enter", "monitor-exit"],
    ),
]

FLASHCARDS = [
    card("fc-a2-01", "Registers", "A method has .registers N and K parameter words. Which v-register is p0?",
         "v(N - K). Parameters occupy the last K registers, with `this` first for instance methods."),
    card("fc-a2-02", "Formats", "Why do compiled methods contain move-object/from16 shuffles?",
         """
            Formats such as 12x, 22c, 22t and 35c encode registers in 4 bits (v0-v15). Values in higher registers
            must be copied down first.
         """,
         code="""
            move-object/from16 v0, p0
            iget-object v1, v0, Lcom/acme/demo/User;->name:Ljava/lang/String;
         """),
    card("fc-a2-03", "Formats", "What register widths does format 22x (move/from16) use?",
         "An 8-bit destination vAA and a 16-bit source vBBBB."),
    card("fc-a2-04", "DEX", "Which DEX version added invoke-polymorphic and invoke-custom?",
         "Version 038, supported from Android 8.0 (API 26)."),
    card("fc-a2-05", "DEX", "Which DEX version added const-method-handle and const-method-type?",
         "Version 039, supported from Android 9 (API 28)."),
    card("fc-a2-06", "Opcodes", "What two references does invoke-polymorphic carry?",
         """
            A method reference (e.g. MethodHandle.invokeExact) and a proto reference with the call site's actual
            argument and return types. Format 45cc, or 4rcc for /range.
         """),
    card("fc-a2-07", "Opcodes", "Load the method type (I)Ljava/lang/String; into v0.",
         "Use const-method-type (format 21c, API 28+).",
         code="const-method-type v0, (I)Ljava/lang/String;"),
    card("fc-a2-08", "Runtime", "What is a class named Outer$$ExternalSyntheticLambda0?",
         """
            A synthetic class D8 generated when desugaring a lambda or method reference. It implements the
            functional interface and forwards to the lambda body in Outer.
         """),
    card("fc-a2-09", "Runtime", "What is a Shape$-CC class?",
         "A D8 companion class holding static and default interface method bodies when minSdk is below 24."),
    card("fc-a2-10", "Descriptors", "What does the descriptor Lj$/time/LocalDate; indicate?",
         "Core library desugaring: references to java.time were rewritten to the bundled j$ copy for older API levels."),
    card("fc-a2-11", "Directives", "What do .local and .end local mark?",
         "The start and end of a named source variable's range in a register. Debug info only; no runtime effect.",
         code="""
            const/4 v0, 0x0
            .local v0, "count":I
            add-int/lit8 v0, v0, 0x1
            .end local v0    # "count":I
         """),
    card("fc-a2-12", "Directives", "What does .line N do?",
         "Maps the following instructions to source line N for stack traces and debuggers. It does not change execution."),
    card("fc-a2-13", "Runtime", "When is java.lang.VerifyError thrown?",
         """
            When a class whose code fails hard verification is loaded, e.g. register type mismatches, using an
            object before <init>, or code that can fall off the end.
         """),
    card("fc-a2-14", "Runtime", "Where does R8 write the mapping file for a release build, and what reads it?",
         "By default app/build/outputs/mapping/release/mapping.txt; the retrace tool uses it to deobfuscate stack traces."),
]
