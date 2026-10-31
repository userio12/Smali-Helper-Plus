"""Intermediate track, part 1 (orders 11-15)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "intermediate"

LESSONS = [
    # ------------------------------------------------------------------------------------------------ 11
    lesson(
        id="method-invocation",
        level=L,
        order=11,
        title="Method Invocation",
        summary="""
            Learn the five invoke instructions, how the runtime chooses the method each one calls, when the
            /range forms are needed, and how return values are collected with move-result, move-result-wide and
            move-result-object.
        """,
        minutes=12,
        sections=[
            text("""
                Every method call in Dalvik bytecode is one of five invoke kinds. They differ in how the target
                method is found at run time:
                - invoke-virtual: a normal instance method that may be overridden. The runtime looks at the
                  actual class of the receiver object and dispatches to the most specific override.
                - invoke-super: the closest superclass implementation of a virtual method, skipping any override
                  in the calling class. This is how super.method() is compiled.
                - invoke-direct: an instance method that cannot be overridden, namely a private method or a
                  constructor (<init>). No dynamic lookup is needed.
                - invoke-static: a static method. There is no receiver object.
                - invoke-interface: a method called through an interface type, such as List or Runnable.

                The first register in the braces is the receiver (this) for every kind except invoke-static;
                the remaining registers are the arguments in declaration order.
            """, title="Five ways to call a method"),
            code("""
                .class public Lcom/acme/demo/Player;
                .super Lcom/acme/demo/Entity;

                .field private listener:Ljava/lang/Runnable;

                .method public constructor <init>()V
                    .registers 1
                    # a superclass constructor is always called with invoke-direct
                    invoke-direct {p0}, Lcom/acme/demo/Entity;-><init>()V
                    return-void
                .end method

                .method private heal(I)V
                    .registers 2
                    return-void
                .end method

                .method public update()V
                    .registers 5

                    # virtual: dispatched on the runtime class of p0
                    invoke-virtual {p0}, Lcom/acme/demo/Player;->getName()Ljava/lang/String;
                    move-result-object v0

                    # super: the parent's implementation, never an override
                    invoke-super {p0}, Lcom/acme/demo/Entity;->update()V

                    # direct: a private method of this class
                    const/16 v1, 0xa
                    invoke-direct {p0, v1}, Lcom/acme/demo/Player;->heal(I)V

                    # static: no receiver register
                    invoke-static {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result v2

                    # interface: the declared type is an interface
                    iget-object v3, p0, Lcom/acme/demo/Player;->listener:Ljava/lang/Runnable;
                    invoke-interface {v3}, Ljava/lang/Runnable;->run()V

                    return-void
                .end method
            """, title="All five invoke kinds"),
            java("""
                public class Player extends Entity {
                    private Runnable listener;

                    public Player() {
                        super();
                    }

                    private void heal(int amount) { }

                    @Override
                    public void update() {
                        String name = getName();
                        super.update();
                        heal(10);
                        boolean empty = TextUtils.isEmpty(name);
                        listener.run();
                    }
                }
            """, title="Equivalent Java"),
            text("""
                An invoke never writes a register. The return value goes into a hidden result slot, and the very
                next instruction must copy it out if you want to keep it:
                - move-result vAA for int, boolean, byte, char, short and float results,
                - move-result-wide vAA for long and double results (it writes the pair vAA and vAA+1),
                - move-result-object vAA for references, including arrays.

                If you do not need the value, simply omit the move-result. Placing any other instruction between
                the invoke and its move-result makes the method invalid, and the verifier rejects the class.
            """, title="Getting the result back"),
            code("""
                .method public static elapsedSince(J)Ljava/lang/Long;
                    .registers 5

                    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
                    move-result-wide v0

                    sub-long/2addr v0, p0

                    # a long argument occupies two registers in the list
                    invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
                    move-result-object v2
                    return-object v2
                .end method
            """, title="Wide results and wide arguments"),
            text("""
                The regular invoke instructions use format 35c: they can list at most five argument registers,
                each a 4-bit register number (v0-v15). The receiver counts as one of the five, and every long or
                double argument counts as two because it lives in a register pair.

                When a call needs more than five argument words, or the arguments live in registers above v15,
                the compiler uses the /range variant (format 3rc). It takes a contiguous block written as
                {vN .. vM}, so the arguments must first be placed in consecutive registers, in order.
            """, title="The five-register limit and /range"),
            code("""
                .method public static sum6(IIIIII)I
                    .registers 6
                    add-int/2addr p0, p1
                    add-int/2addr p0, p2
                    add-int/2addr p0, p3
                    add-int/2addr p0, p4
                    add-int/2addr p0, p5
                    return p0
                .end method

                .method public static demo()I
                    .registers 6
                    const/4 v0, 0x1
                    const/4 v1, 0x2
                    const/4 v2, 0x3
                    const/4 v3, 0x4
                    const/4 v4, 0x5
                    const/4 v5, 0x6
                    invoke-static/range {v0 .. v5}, Lcom/acme/demo/MathUtil;->sum6(IIIIII)I
                    move-result v0
                    return v0
                .end method
            """, title="Six arguments need invoke-static/range"),
            tip("""
                To pick the invoke kind when writing Smali by hand, ask two questions: is there a receiver (if not,
                invoke-static), and can the call be overridden? Private methods and constructors use
                invoke-direct, super calls use invoke-super, calls through an interface type use
                invoke-interface, and everything else is invoke-virtual.
            """),
        ],
        exercises=[
            exercise(
                "method-invocation-ex1",
                """
                    Complete the static method len(Ljava/lang/String;)I so it returns the length of its argument by
                    calling String.length().
                """,
                starter="""
                    .method public static len(Ljava/lang/String;)I
                        .registers 2
                        # call length() on p0 and return the result
                    .end method
                """,
                solution="""
                    .method public static len(Ljava/lang/String;)I
                        .registers 2
                        invoke-virtual {p0}, Ljava/lang/String;->length()I
                        move-result v0
                        return v0
                    .end method
                """,
                hint="String is a class, not an interface, and length() returns an int: invoke-virtual then move-result.",
            ),
            exercise(
                "method-invocation-ex2",
                """
                    This call passes six int arguments and does not assemble. Rewrite it so it works, keeping the
                    arguments in v0-v5.
                """,
                starter="""
                    invoke-static {v0, v1, v2, v3, v4, v5}, Lcom/acme/demo/MathUtil;->sum6(IIIIII)I
                    move-result v0
                """,
                solution="""
                    invoke-static/range {v0 .. v5}, Lcom/acme/demo/MathUtil;->sum6(IIIIII)I
                    move-result v0
                """,
                hint="The 35c format holds at most five registers. The 3rc format takes a contiguous range instead.",
                check_starter=False,
            ),
            exercise(
                "method-invocation-ex3",
                """
                    Name the invoke instruction for each call: (a) list.size() where list is declared as
                    java.util.List, (b) a call to a private helper method, (c) Math.max(a, b), (d) super.onCreate()
                    inside an Activity subclass.
                """,
                solution="""
                    (a) invoke-interface, because List is an interface. (b) invoke-direct, because private methods
                    cannot be overridden. (c) invoke-static, because Math.max is static. (d) invoke-super, which calls
                    the superclass implementation directly.
                """,
                hint="Look at the declared type of the receiver and the method's modifiers, not at the object's runtime class.",
            ),
        ],
        quiz=[
            question(
                "method-invocation-q1",
                "Which instruction is used to call a private instance method of the same class?",
                ["invoke-virtual", "invoke-direct", "invoke-static", "invoke-super"],
                1,
                """
                    Private instance methods and constructors are not overridable, so they are called with
                    invoke-direct. invoke-static is only for methods without a receiver.
                """,
            ),
            question(
                "method-invocation-q2",
                "In this call, which registers hold the long argument?",
                ["v0 and v1", "v1 only", "v1 and v2", "v2 only"],
                2,
                """
                    v0 is the receiver (this). A long occupies a register pair, so it is passed in v1 and v2, and both
                    registers appear in the argument list.
                """,
                code="invoke-virtual {v0, v1, v2}, Lcom/acme/demo/Clock;->sleep(J)V",
            ),
            question(
                "method-invocation-q3",
                "A call needs seven argument words. What must the compiler emit?",
                [
                    "Two separate invoke instructions",
                    "An invoke-*/range instruction with the arguments in consecutive registers",
                    "An invoke with seven registers in braces",
                    "A filled-new-array to pack the arguments",
                ],
                1,
                """
                    Format 35c lists at most five registers. The /range forms (format 3rc) take a contiguous block of
                    registers, {vN .. vM}, so more arguments can be passed.
                """,
            ),
            question(
                "method-invocation-q4",
                "What happens with this code?",
                [
                    "v1 receives the length after the const",
                    "The verifier rejects the method",
                    "v1 receives 0",
                    "The result is kept until the next invoke",
                ],
                1,
                """
                    move-result must be the instruction immediately after the invoke. Any instruction in between makes
                    the method invalid, which is reported as a verification error when the class loads.
                """,
                code="""
                    invoke-virtual {v0}, Ljava/lang/String;->length()I
                    const/4 v2, 0x0
                    move-result v1
                """,
            ),
            question(
                "method-invocation-q5",
                "What does invoke-super do differently from invoke-virtual?",
                [
                    "It calls the closest superclass implementation instead of dispatching to an override",
                    "It calls the method on the superclass object instead of this",
                    "It calls static methods of the superclass",
                    "It is only used for constructors",
                ],
                0,
                """
                    invoke-super still passes this as the receiver, but it skips dynamic dispatch and runs the
                    nearest superclass implementation. Superclass constructors use invoke-direct instead.
                """,
            ),
        ],
        related=[
            "invoke-virtual", "invoke-super", "invoke-direct", "invoke-static", "invoke-interface",
            "invoke-virtual/range", "invoke-static/range", "move-result", "move-result-wide", "move-result-object",
        ],
    ),
    # ------------------------------------------------------------------------------------------------ 12
    lesson(
        id="arrays",
        level=L,
        order=12,
        title="Arrays",
        summary="""
            Create, read and write arrays in Smali: new-array and array-length, the typed aget/aput family,
            filled-new-array for small literal arrays, fill-array-data payloads, and the exceptions array
            instructions can throw.
        """,
        minutes=12,
        sections=[
            text("""
                Arrays are objects, so a register holds only a reference to one. new-array vA, vB, [T creates an
                array of type [T whose length is the int in vB, and stores the reference in vA. All elements start
                as zero, false or null. array-length vA, vB stores the length of the array in vB into vA.

                Elements are read with aget and written with aput. Both take three registers: the value, the array
                and the int index. The suffix must match the element type:
                - aget / aput: int and float elements (any 32-bit primitive that is not one of the types below),
                - aget-wide / aput-wide: long and double, using a register pair for the value,
                - aget-object / aput-object: references, including nested arrays,
                - aget-boolean, aget-byte, aget-char, aget-short and the matching aput variants.
            """, title="Creating and indexing arrays"),
            code("""
                .method public static names()[Ljava/lang/String;
                    .registers 3
                    const/4 v0, 0x2
                    new-array v0, v0, [Ljava/lang/String;

                    const/4 v1, 0x0
                    const-string v2, "alpha"
                    aput-object v2, v0, v1

                    const/4 v1, 0x1
                    const-string v2, "beta"
                    aput-object v2, v0, v1

                    return-object v0
                .end method

                .method public static sum([I)I
                    .registers 5
                    const/4 v0, 0x0
                    const/4 v1, 0x0
                    array-length v2, p0
                    :loop
                    if-ge v1, v2, :done
                    aget v3, p0, v1
                    add-int/2addr v0, v3
                    add-int/lit8 v1, v1, 0x1
                    goto :loop
                    :done
                    return v0
                .end method
            """, title="Writing and reading elements"),
            java("""
                public static String[] names() {
                    String[] a = new String[2];
                    a[0] = "alpha";
                    a[1] = "beta";
                    return a;
                }

                public static int sum(int[] values) {
                    int total = 0;
                    for (int i = 0; i < values.length; i++) {
                        total += values[i];
                    }
                    return total;
                }
            """, title="Equivalent Java"),
            text("""
                Two instructions build arrays from literal values more compactly.

                filled-new-array {vC, ...}, [T creates an array whose length is the number of registers listed and
                fills it with their values. The elements must be single-word values, so it cannot build long[] or
                double[] arrays, but int and reference arrays are fine. Like an invoke, it does not write a register
                itself: a move-result-object must follow immediately. filled-new-array/range accepts a contiguous
                range of registers for longer lists.

                fill-array-data vAA, :label copies constant data into an existing primitive array. The label points
                to an .array-data block (the fill-array-data-payload pseudo-instruction) whose number is the element
                width in bytes: 1 for byte and boolean, 2 for char and short, 4 for int and float, 8 for long and
                double. The payload is data, not code, so it is placed where execution can never fall into it,
                typically after the method's final return.
            """, title="Literal arrays"),
            code("""
                .method public static primes()[I
                    .registers 1
                    const/4 v0, 0x5
                    new-array v0, v0, [I
                    fill-array-data v0, :array_0
                    return-object v0

                    :array_0
                    .array-data 4
                        0x2
                        0x3
                        0x5
                        0x7
                        0xb
                    .end array-data
                .end method

                .method public static triple()[I
                    .registers 4
                    const/4 v0, 0x1
                    const/4 v1, 0x2
                    const/4 v2, 0x3
                    filled-new-array {v0, v1, v2}, [I
                    move-result-object v3
                    return-object v3
                .end method
            """, title="fill-array-data and filled-new-array"),
            warning("""
                Array instructions check their operands at run time. A null array reference throws
                NullPointerException, an index below zero or at least the length throws
                ArrayIndexOutOfBoundsException, new-array with a negative size throws NegativeArraySizeException,
                and aput-object throws ArrayStoreException when the value's type is not assignable to the array's
                element type (for example an Integer stored into a String[] seen through an Object[] reference).
            """, title="Run-time checks"),
            tip("""
                There is no separate aget-float: float arrays use plain aget and aput, just like int arrays. The
                instruction only cares about the element width, while the array type decides how the bits are
                interpreted.
            """),
        ],
        exercises=[
            exercise(
                "arrays-ex1",
                "Write first([Ljava/lang/String;)Ljava/lang/String; that returns element 0 of its argument.",
                starter="""
                    .method public static first([Ljava/lang/String;)Ljava/lang/String;
                        .registers 2
                    .end method
                """,
                solution="""
                    .method public static first([Ljava/lang/String;)Ljava/lang/String;
                        .registers 2
                        const/4 v0, 0x0
                        aget-object v0, p0, v0
                        return-object v0
                    .end method
                """,
                hint="Put the index 0 in a register first, then use the -object variant because the elements are references.",
            ),
            exercise(
                "arrays-ex2",
                "Write make()[J that creates a long[] of length 2, stores 10L at index 1 and returns it.",
                solution="""
                    .method public static make()[J
                        .registers 4
                        const/4 v0, 0x2
                        new-array v0, v0, [J
                        const/4 v1, 0x1
                        const-wide/16 v2, 0xa
                        aput-wide v2, v0, v1
                        return-object v0
                    .end method
                """,
                hint="A long value needs a register pair (here v2 and v3) and the -wide variant of aput.",
            ),
        ],
        quiz=[
            question(
                "arrays-q1",
                "Which instruction reads an element from a char[] array?",
                ["aget", "aget-char", "aget-short", "aget-object"],
                1,
                """
                    char elements are 16-bit unsigned values and use aget-char. aget-short would sign-extend the value,
                    which gives wrong results for characters above 0x7fff.
                """,
            ),
            question(
                "arrays-q2",
                "Which instruction reads an element from a float[] array?",
                ["aget-float", "aget-wide", "aget", "aget-object"],
                2,
                """
                    There is no aget-float. Plain aget handles all 32-bit non-reference elements other than the
                    narrow types, which includes both int and float.
                """,
            ),
            question(
                "arrays-q3",
                "In this payload, what does the 4 after .array-data mean?",
                ["The number of elements", "The element width in bytes", "The array register", "The minimum API level"],
                1,
                """
                    The number is the width of each element: 4 bytes for int or float. The element count is implied by
                    the number of values listed before .end array-data.
                """,
                code="""
                    :array_0
                    .array-data 4
                        0x2
                        0x3
                    .end array-data
                """,
            ),
            question(
                "arrays-q4",
                "Storing an Integer into a String[] through an Object[] reference with aput-object throws what?",
                ["ClassCastException", "ArrayStoreException", "IllegalArgumentException", "Nothing, the store succeeds"],
                1,
                """
                    aput-object checks that the value is assignable to the array's actual element type at run time and
                    throws ArrayStoreException if it is not.
                """,
            ),
            question(
                "arrays-q5",
                "What must follow filled-new-array if the new array is going to be used?",
                ["move-result", "move-result-object", "return-object", "check-cast"],
                1,
                """
                    filled-new-array stores its result like an invoke does, so the array reference has to be copied
                    with move-result-object as the very next instruction.
                """,
            ),
        ],
        related=[
            "new-array", "array-length", "aget", "aput", "aget-object", "aput-object", "aget-wide", "aput-wide",
            "aget-char", "filled-new-array", "filled-new-array/range", "fill-array-data", "fill-array-data-payload",
        ],
    ),
    # ------------------------------------------------------------------------------------------------ 13
    lesson(
        id="conditions",
        level=L,
        order=13,
        title="Conditions",
        summary="""
            Branch with if-test and if-testz instructions and labels, recognise the inverted conditions compilers
            generate, test references for null, and compare long, float and double values with cmp-long and the
            NaN-aware cmpl/cmpg instructions.
        """,
        minutes=12,
        sections=[
            text("""
                Dalvik has two families of conditional branches. The if-test family compares two registers:
                if-eq, if-ne, if-lt, if-ge, if-gt and if-le. The if-testz family compares one register against
                zero: if-eqz, if-nez, if-ltz, if-gez, if-gtz and if-lez. When the condition holds, execution jumps
                to the label; otherwise it continues with the next instruction.

                Labels are names that start with a colon, such as :cond_0. baksmali generates names like :cond_0
                and :goto_0, but any name works when you write Smali by hand. In the binary, the label becomes a
                signed offset measured in 16-bit code units.
            """, title="Two families of branches"),
            code("""
                .method public static max(II)I
                    .registers 2
                    if-le p0, p1, :cond_0
                    return p0

                    :cond_0
                    return p1
                .end method
            """, title="Inverted condition"),
            java("""
                public static int max(int a, int b) {
                    if (a > b) {
                        return a;
                    }
                    return b;
                }
            """, title="Equivalent Java"),
            text("""
                Notice that the Java condition is a > b, yet the Smali tests a <= b. Compilers usually emit the
                opposite condition and jump over the then-block, so the then-block can follow the branch directly.
                When you translate Smali back to Java in your head, invert the test first.

                The if-testz instructions cover several Java idioms. Booleans are stored as 0 or 1, so if (flag)
                becomes if-eqz flag, :else. A reference compared with zero is a null check: if-eqz jumps when the
                reference is null and if-nez when it is not. if-eq and if-ne on two references compare identity,
                exactly like == in Java; calling equals() needs a real invoke.
            """, title="Reading compiled conditions"),
            code("""
                .method public static lengthOrZero(Ljava/lang/String;)I
                    .registers 2
                    if-nez p0, :cond_0
                    const/4 v0, 0x0
                    return v0

                    :cond_0
                    invoke-virtual {p0}, Ljava/lang/String;->length()I
                    move-result v0
                    return v0
                .end method

                .method public static isBefore(JJ)Z
                    .registers 5
                    cmp-long v0, p0, p2
                    if-gez v0, :cond_0
                    const/4 v0, 0x1
                    return v0

                    :cond_0
                    const/4 v0, 0x0
                    return v0
                .end method
            """, title="Null checks and long comparisons"),
            text("""
                The if instructions only work on int-like values and references. For long, float and double, a
                compare instruction first produces -1, 0 or 1 in an int register, and an if-testz branches on that
                result. cmp-long compares two long pairs.

                Floating-point values can be NaN, which is not less than, equal to or greater than anything. The
                two compare variants differ only in what they return for NaN: cmpl-float and cmpl-double ("lt
                bias") return -1, while cmpg-float and cmpg-double ("gt bias") return 1. A compiler picks the
                variant that makes the comparison false when NaN is involved: cmpg for < and <=, cmpl for > and >=.
            """, title="Comparing long, float and double"),
            code("""
                .method public static isPositive(F)Z
                    .registers 3
                    const/4 v0, 0x0
                    # NaN gives -1 here, so the method returns false for NaN
                    cmpl-float v1, p0, v0
                    if-lez v1, :cond_0
                    const/4 v0, 0x1

                    :cond_0
                    return v0
                .end method
            """, title="A NaN-safe float test"),
            tip("""
                Branch offsets are 16-bit for all if instructions, so a conditional jump reaches about 32K code
                units in each direction. if-test registers are 4-bit (v0-v15) because format 22t packs two
                registers into one byte, while if-testz uses format 21t with an 8-bit register (v0-v255).
            """),
        ],
        exercises=[
            exercise(
                "conditions-ex1",
                "Write isZero(I)Z that returns true when its argument is 0 and false otherwise.",
                solution="""
                    .method public static isZero(I)Z
                        .registers 2
                        if-nez p0, :not_zero
                        const/4 v0, 0x1
                        return v0

                        :not_zero
                        const/4 v0, 0x0
                        return v0
                    .end method
                """,
                hint="Jump away when the value is not zero; booleans are returned as the ints 1 and 0.",
            ),
            exercise(
                "conditions-ex2",
                "Write min(II)I equivalent to the Java expression a < b ? a : b, using a single if-test instruction.",
                solution="""
                    .method public static min(II)I
                        .registers 2
                        if-ge p0, p1, :cond_0
                        return p0

                        :cond_0
                        return p1
                    .end method
                """,
                hint="Invert the condition: jump to the code that returns b when a >= b.",
            ),
            exercise(
                "conditions-ex3",
                """
                    Which compare instruction and which branch would a compiler typically use for the Java test
                    if (d < 1.0) { ... } where d is a double held in a register pair?
                """,
                solution="""
                    cmpg-double, because the condition uses <: a NaN operand makes cmpg return 1, so the test is false.
                    The result is then branched on with if-gez, which skips the then-block when the result is 0 or 1.
                """,
                hint="Choose the bias that turns NaN into the 'false' answer, then invert the branch.",
            ),
        ],
        quiz=[
            question(
                "conditions-q1",
                "When does this instruction jump to :cond_0?",
                ["When v0 is 0", "When v0 is greater than 0", "When v0 is less than 0", "When v0 is not 0"],
                1,
                "if-gtz branches when the register is greater than zero.",
                code="if-gtz v0, :cond_0",
            ),
            question(
                "conditions-q2",
                """
                    This Smali was compiled from an if statement, with a in v0 and b in v1. Which Java condition
                    guards the return v0?
                """,
                ["a <= b", "a > b", "a < b", "a == b"],
                1,
                """
                    The branch jumps past return v0 when a <= b, so return v0 runs only when a > b. Compilers emit the
                    inverted condition to skip over the then-block.
                """,
                code="""
                    if-le v0, v1, :cond_0
                    return v0
                    :cond_0
                    return v1
                """,
            ),
            question(
                "conditions-q3",
                "How is a comparison of two long values usually compiled?",
                [
                    "if-lt with both register pairs",
                    "cmp-long into an int register, followed by an if-testz",
                    "sub-long followed by if-ltz on the high half",
                    "invoke-static Long.compare every time",
                ],
                1,
                """
                    The if instructions work only on single-word values. cmp-long writes -1, 0 or 1 to an int
                    register, and an if-testz branches on it.
                """,
            ),
            question(
                "conditions-q4",
                "What does cmpl-float return when one operand is NaN?",
                ["-1", "0", "1", "It throws ArithmeticException"],
                0,
                """
                    cmpl has an "lt bias" and returns -1 for NaN comparisons; cmpg returns 1. No exception is thrown.
                """,
            ),
            question(
                "conditions-q5",
                "What does if-eq v0, v1 test when both registers hold String references?",
                [
                    "Whether the strings have the same characters",
                    "Whether both references point to the same object",
                    "Whether the strings have the same length",
                    "It is invalid for references",
                ],
                1,
                """
                    if-eq on references compares identity, like == in Java. Comparing contents requires calling
                    equals() with an invoke instruction.
                """,
            ),
        ],
        related=[
            "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez", "if-ltz", "if-gez",
            "if-gtz", "if-lez", "cmp-long", "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double",
        ],
    ),
    # ------------------------------------------------------------------------------------------------ 14
    lesson(
        id="loops",
        level=L,
        order=14,
        title="Loops",
        summary="""
            Recognise for, while and do-while loops in Smali by their backward branches, walk arrays with
            array-length and aget, follow the iterator calls behind an enhanced for loop, and see how break and
            continue become plain jumps.
        """,
        minutes=12,
        sections=[
            text("""
                Dalvik has no loop instruction. A loop is simply a branch that jumps backwards to an earlier label,
                called a back-edge. Once you spot a goto or if instruction whose target is above it, you have found
                a loop; the label it jumps to is the loop head.

                A typical for or while loop compiled by d8 looks like this: the condition is tested at the top with
                an inverted if that exits the loop, the body follows, then the update step, and finally a goto jumps
                back to the condition. A do-while loop puts the test at the bottom instead, so the back-edge is the
                conditional branch itself.
            """, title="Loops are backward jumps"),
            code("""
                .method public static sumTo(I)I
                    .registers 3
                    const/4 v0, 0x0
                    const/4 v1, 0x1

                    :goto_0
                    if-gt v1, p0, :cond_0
                    add-int/2addr v0, v1
                    add-int/lit8 v1, v1, 0x1
                    goto :goto_0

                    :cond_0
                    return v0
                .end method

                .method public static countDigits(I)I
                    .registers 3
                    const/4 v0, 0x0

                    :loop
                    add-int/lit8 v0, v0, 0x1
                    div-int/lit8 p0, p0, 0xa
                    if-nez p0, :loop

                    return v0
                .end method
            """, title="A for loop and a do-while loop"),
            java("""
                public static int sumTo(int n) {
                    int sum = 0;
                    for (int i = 1; i <= n; i++) {
                        sum += i;
                    }
                    return sum;
                }

                public static int countDigits(int n) {
                    int count = 0;
                    do {
                        count++;
                        n /= 10;
                    } while (n != 0);
                    return count;
                }
            """, title="Equivalent Java"),
            text("""
                An enhanced for loop over an Iterable has no special bytecode. The compiler calls iterator() once,
                then repeatedly calls hasNext() and next() through invoke-interface. Because of generic type
                erasure, next() returns Object, so a check-cast to the element type follows it.

                Loops over arrays usually load array-length into a register before the loop and compare the index
                against it with if-ge.
            """, title="Iterator loops"),
            code("""
                .method public static printAll(Ljava/util/List;)V
                    .registers 4
                    invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                    move-result-object v0

                    :goto_0
                    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                    move-result v1
                    if-eqz v1, :cond_0

                    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                    move-result-object v1
                    check-cast v1, Ljava/lang/String;

                    sget-object v2, Ljava/lang/System;->out:Ljava/io/PrintStream;
                    invoke-virtual {v2, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V
                    goto :goto_0

                    :cond_0
                    return-void
                .end method
            """, title="for (String s : list) System.out.println(s);"),
            text("""
                break and continue compile to ordinary jumps. break jumps to the first instruction after the loop,
                and continue jumps to the update step (in a for loop) or the condition (in a while loop). In the
                listing below, a zero element ends the loop early and negative elements are skipped.
            """, title="break and continue"),
            code("""
                .method public static sumUntilZero([I)I
                    .registers 5
                    const/4 v0, 0x0
                    const/4 v1, 0x0
                    array-length v2, p0

                    :loop
                    if-ge v1, v2, :end
                    aget v3, p0, v1
                    # break
                    if-eqz v3, :end
                    # continue
                    if-ltz v3, :next
                    add-int/2addr v0, v3

                    :next
                    add-int/lit8 v1, v1, 0x1
                    goto :loop

                    :end
                    return v0
                .end method
            """, title="break and continue as jumps"),
            tip("""
                When reading an unfamiliar loop, find three things: the loop head (target of the back-edge), the
                exit branch (the if that jumps past the back-edge) and the induction variable (usually updated with
                add-int/lit8 just before the goto).
            """),
        ],
        exercises=[
            exercise(
                "loops-ex1",
                "Write an iterative factorial(I)I that multiplies the result by n and decrements n until n is no longer positive.",
                starter="""
                    .method public static factorial(I)I
                        .registers 2
                        const/4 v0, 0x1
                        # loop while p0 > 0
                        return v0
                    .end method
                """,
                solution="""
                    .method public static factorial(I)I
                        .registers 2
                        const/4 v0, 0x1

                        :loop
                        if-lez p0, :done
                        mul-int/2addr v0, p0
                        add-int/lit8 p0, p0, -0x1
                        goto :loop

                        :done
                        return v0
                    .end method
                """,
                hint="Test the exit condition at the loop head with if-lez, and close the loop with goto.",
            ),
            exercise(
                "loops-ex2",
                "Write countTrue([Z)I that returns how many elements of a boolean[] are true.",
                solution="""
                    .method public static countTrue([Z)I
                        .registers 5
                        const/4 v0, 0x0
                        const/4 v1, 0x0
                        array-length v2, p0

                        :loop
                        if-ge v1, v2, :done
                        aget-boolean v3, p0, v1
                        if-eqz v3, :next
                        add-int/lit8 v0, v0, 0x1

                        :next
                        add-int/lit8 v1, v1, 0x1
                        goto :loop

                        :done
                        return v0
                    .end method
                """,
                hint="Use aget-boolean for the element and skip the increment with if-eqz when it is false.",
            ),
        ],
        quiz=[
            question(
                "loops-q1",
                "What identifies a loop in Smali?",
                [
                    "A .loop directive",
                    "A branch whose target label comes before the branch",
                    "Any goto instruction",
                    "A label named :loop",
                ],
                1,
                """
                    There is no loop instruction or directive. A loop exists wherever a goto or if jumps back to an
                    earlier instruction. Label names are arbitrary.
                """,
            ),
            question(
                "loops-q2",
                "Which Java loop does this Smali correspond to?",
                ["for loop", "while loop", "do-while loop", "enhanced for loop"],
                2,
                """
                    The body runs before the condition is checked, and the back-edge is the conditional branch at the
                    bottom. That is the shape of a do-while loop.
                """,
                code="""
                    :loop
                    add-int/lit8 v0, v0, 0x1
                    div-int/lit8 p0, p0, 0xa
                    if-nez p0, :loop
                """,
            ),
            question(
                "loops-q3",
                "How is for (String s : list) compiled when list is a java.util.List?",
                [
                    "With a special foreach instruction",
                    "With array-length and aget-object",
                    "With iterator(), hasNext() and next() called through invoke-interface",
                    "With invoke-static calls to Collections",
                ],
                2,
                """
                    An enhanced for loop over an Iterable uses the Iterator protocol. The calls go through
                    invoke-interface because List and Iterator are interfaces.
                """,
            ),
            question(
                "loops-q4",
                "In a compiled for loop, where does continue usually jump?",
                ["To the method's return", "To the update step before the back-edge", "To the first instruction of the method", "To the end of the loop"],
                1,
                """
                    continue skips the rest of the body but still runs the update (for example i++), so it jumps to the
                    update step, which then goes back to the condition.
                """,
            ),
            question(
                "loops-q5",
                "Why does a check-cast follow Iterator.next() in a loop over List<String>?",
                [
                    "next() returns Object because of generic type erasure",
                    "To check for null",
                    "Because invoke-interface requires it",
                    "To convert the value to a primitive",
                ],
                0,
                """
                    Generic type parameters are erased, so the method reference returns Ljava/lang/Object;. The
                    check-cast restores the static type and throws ClassCastException on a wrong element.
                """,
            ),
        ],
        related=["goto", "if-ge", "if-gt", "if-nez", "if-eqz", "add-int/lit8", "array-length", "aget", "invoke-interface", "check-cast"],
    ),
    # ------------------------------------------------------------------------------------------------ 15
    lesson(
        id="fields",
        level=L,
        order=15,
        title="Fields",
        summary="""
            Declare static and instance fields with .field, read and write them with the iget/iput and sget/sput
            families, understand the field reference syntax, and see where static initialisers and constants end
            up in the compiled class.
        """,
        minutes=11,
        sections=[
            text("""
                A field is declared with .field followed by its access flags, its name and its type descriptor,
                for example .field private count:I. Static fields add the static flag.

                Instructions refer to a field with a full field reference: the defining class, an arrow, the name,
                a colon and the type, as in Lcom/acme/demo/Counter;->count:I. The type is part of the reference, so
                the same name with a different type would be a different field.
            """, title="Declaring and referencing fields"),
            code("""
                .class public Lcom/acme/demo/Counter;
                .super Ljava/lang/Object;

                .field public static final MAX:I = 0x64
                .field private static instances:I

                .field private count:I
                .field private name:Ljava/lang/String;
                .field private total:J

                .method public constructor <init>(Ljava/lang/String;)V
                    .registers 3
                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    iput-object p1, p0, Lcom/acme/demo/Counter;->name:Ljava/lang/String;

                    sget v0, Lcom/acme/demo/Counter;->instances:I
                    add-int/lit8 v0, v0, 0x1
                    sput v0, Lcom/acme/demo/Counter;->instances:I
                    return-void
                .end method

                .method public increment()V
                    .registers 6
                    iget v0, p0, Lcom/acme/demo/Counter;->count:I
                    add-int/lit8 v0, v0, 0x1
                    iput v0, p0, Lcom/acme/demo/Counter;->count:I

                    iget-wide v1, p0, Lcom/acme/demo/Counter;->total:J
                    const-wide/16 v3, 0x1
                    add-long/2addr v1, v3
                    iput-wide v1, p0, Lcom/acme/demo/Counter;->total:J
                    return-void
                .end method
            """, title="Static and instance fields"),
            java("""
                public class Counter {
                    public static final int MAX = 100;
                    private static int instances;

                    private int count;
                    private String name;
                    private long total;

                    public Counter(String name) {
                        this.name = name;
                        instances++;
                    }

                    public void increment() {
                        count++;
                        total += 1;
                    }
                }
            """, title="Equivalent Java"),
            text("""
                Instance fields use iget and iput with three operands: the value register, the object register and
                the field reference. Static fields use sget and sput, which have no object register. Both families
                have typed variants that must match the field's type:
                - iget / iput and sget / sput: int and float,
                - -wide: long and double, using a register pair,
                - -object: any reference type, including arrays,
                - -boolean, -byte, -char and -short for the narrow types.

                iget and iput use format 22c, so their two registers must be v0-v15. sget and sput use format 21c
                with a single 8-bit register.
            """, title="The get and put families"),
            code("""
                .class public Lcom/acme/demo/Config;
                .super Ljava/lang/Object;

                .field public static final TAGS:Ljava/util/List;

                .method static constructor <clinit>()V
                    .registers 1
                    new-instance v0, Ljava/util/ArrayList;
                    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                    sput-object v0, Lcom/acme/demo/Config;->TAGS:Ljava/util/List;
                    return-void
                .end method
            """, title="Static initialisation in <clinit>"),
            text("""
                A static field can carry an initial value directly in its .field line, like MAX:I = 0x64 above. The
                value is stored in the class definition and assigned when the class is initialised. Compilers use
                this for compile-time constants (primitives and strings). Java compilers also inline such
                constants at every use site, so other classes often contain const/16 v0, 0x64 instead of an sget.

                Every other static initialiser, such as creating the ArrayList, becomes code in the static
                constructor <clinit>, which runs once before the class is first used. Instance field initialisers
                cannot be expressed in the .field line at all: they become iput instructions inside each
                constructor.
            """, title="Initial values and <clinit>"),
            warning("""
                The instruction must match the field: using iget on a static field, or sget on an instance field,
                throws IncompatibleClassChangeError, and a reference to a field that does not exist throws
                NoSuchFieldError. A mismatched type variant, such as iget on a long field, fails verification.
            """),
        ],
        exercises=[
            exercise(
                "fields-ex1",
                "Write the getter getName()Ljava/lang/String; for the Counter class above.",
                starter="""
                    .method public getName()Ljava/lang/String;
                        .registers 2
                    .end method
                """,
                solution="""
                    .method public getName()Ljava/lang/String;
                        .registers 2
                        iget-object v0, p0, Lcom/acme/demo/Counter;->name:Ljava/lang/String;
                        return-object v0
                    .end method
                """,
                hint="The receiver is p0, the field holds a reference, so use the -object variant of iget.",
            ),
            exercise(
                "fields-ex2",
                """
                    Declare a private static boolean field enabled in Lcom/acme/demo/Settings; and write a static
                    method enable()V that sets it to true.
                """,
                solution="""
                    .field private static enabled:Z

                    .method public static enable()V
                        .registers 1
                        const/4 v0, 0x1
                        sput-boolean v0, Lcom/acme/demo/Settings;->enabled:Z
                        return-void
                    .end method
                """,
                hint="Booleans are written as 0 or 1 with sput-boolean; static writes need no object register.",
            ),
        ],
        quiz=[
            question(
                "fields-q1",
                "In this field reference, what does the final I mean?",
                ["The field is an int", "The field is an instance field", "The field is an interface", "The field is immutable"],
                0,
                "The part after the colon is the field's type descriptor, and I is the descriptor for int.",
                code="Lcom/acme/demo/User;->age:I",
            ),
            question(
                "fields-q2",
                "Which instruction reads an instance field of type double?",
                ["iget", "iget-wide", "sget-wide", "iget-object"],
                1,
                """
                    long and double fields are 64-bit and use the -wide variants. Instance fields use iget, static
                    fields sget.
                """,
            ),
            question(
                "fields-q3",
                "What does this instruction do?",
                [
                    "Writes v0 into System.out",
                    "Reads the static field System.out into v0",
                    "Reads the instance field out of the object in v0",
                    "Calls System.out.println",
                ],
                1,
                """
                    sget-object reads a static reference field. The value of System.out, a PrintStream, is stored in
                    v0. There is no object operand because the field is static.
                """,
                code="sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
            ),
            question(
                "fields-q4",
                "Where do Java instance field initialisers such as private int count = 5; end up?",
                [
                    "In the .field line as = 0x5",
                    "In <clinit>",
                    "As iput instructions in the constructors",
                    "In a separate initialiser method called <finit>",
                ],
                2,
                """
                    Initial values in the .field line are only supported for static fields. Instance initialisers are
                    compiled into each constructor, after the superclass constructor call.
                """,
            ),
            question(
                "fields-q5",
                "Why might another class read Counter.MAX with const/16 instead of sget?",
                [
                    "sget cannot read final fields",
                    "The Java compiler inlined the compile-time constant",
                    "d8 converts every static field into a constant",
                    "MAX is stored in a register",
                ],
                1,
                """
                    Compile-time constants (static final primitives and strings with constant initialisers) are copied
                    into the code of the classes that use them, so no field access remains.
                """,
            ),
        ],
        related=[
            "iget", "iput", "iget-wide", "iput-wide", "iget-object", "iput-object", "iget-boolean", "sget",
            "sput", "sget-object", "sput-object", "sput-boolean",
        ],
    ),
]

FLASHCARDS = [
    card("fc-i1-01", "Opcodes", "Which methods are called with invoke-direct?",
         "Non-overridable instance methods: private methods and constructors (<init>), including the superclass constructor call."),
    card("fc-i1-02", "Formats", "What is the difference between formats 35c and 3rc for invokes?",
         """
            35c lists up to five 4-bit argument registers individually. 3rc (the /range forms) takes a contiguous
            block {vN .. vM} of up to 255 registers with 16-bit register numbers.
         """),
    card("fc-i1-03", "Opcodes", "What does move-result-wide do?",
         """
            Copies a long or double return value of the immediately preceding invoke into a register pair
            (vAA and vAA+1).
         """,
         code="""
            invoke-static {}, Ljava/lang/System;->nanoTime()J
            move-result-wide v0
         """),
    card("fc-i1-04", "Registers", "How many argument registers does a long parameter use in an invoke?",
         "Two: a long or double occupies a register pair, and both registers are listed in the braces."),
    card("fc-i1-05", "Opcodes", "What can filled-new-array not create?",
         """
            Arrays of long or double: the elements must be single-word values. int arrays and reference arrays
            are allowed. The result must be fetched with move-result-object.
         """),
    card("fc-i1-06", "Directives", "What does the number after .array-data mean?",
         "The element width in bytes (1, 2, 4 or 8) for the fill-array-data payload that follows."),
    card("fc-i1-07", "Opcodes", "Which instruction reads an element of a float[] array?",
         "Plain aget. There is no aget-float; aget and aput handle all 32-bit primitive elements such as int and float."),
    card("fc-i1-08", "Opcodes", "What do cmpl-float and cmpg-float return for NaN?",
         "cmpl-* (lt bias) returns -1 and cmpg-* (gt bias) returns 1. Compilers use cmpg for < and <=, cmpl for > and >=."),
    card("fc-i1-09", "Formats", "Why can if-eqz use v200 but if-eq cannot?",
         """
            if-testz uses format 21t with an 8-bit register. if-test uses format 22t, which packs two 4-bit
            registers into one byte, so both registers must be v0-v15.
         """),
    card("fc-i1-10", "Opcodes", "What does if-eqz test when the register holds a reference?",
         "Whether the reference is null. if-nez jumps when it is not null.",
         code="if-eqz p1, :cond_null"),
    card("fc-i1-11", "Descriptors", "What are the parts of a field reference such as Lcom/acme/demo/A;->count:I?",
         "The defining class descriptor, ->, the field name, a colon and the field type descriptor."),
    card("fc-i1-12", "Formats", "Which formats do iget and sget use?",
         """
            iget/iput use 22c (two 4-bit registers plus a field index); sget/sput use 21c (one 8-bit register plus
            a field index).
         """),
    card("fc-i1-13", "Runtime", "When does aput-object throw ArrayStoreException?",
         "When the stored value's runtime type is not assignable to the array's actual element type."),
]
