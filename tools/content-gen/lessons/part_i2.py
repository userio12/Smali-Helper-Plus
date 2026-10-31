"""Intermediate track, part 2 (orders 16-19)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "intermediate"

LESSONS = [
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="arithmetic",
        level=L,
        order=16,
        title="Arithmetic",
        summary="""
            Dalvik has a separate instruction for every arithmetic operation and operand type. Learn the
            three-register, /2addr and literal forms, reverse subtraction, masked shifts, division by zero,
            64-bit register pairs and the conversion instructions.
        """,
        minutes=14,
        sections=[
            text("""
                Arithmetic instructions are typed: add-int adds two 32-bit ints, add-long two 64-bit longs,
                add-float and add-double floating-point values. The register itself carries no type; the opcode
                decides how its bits are interpreted, and the verifier checks that the types line up.

                Each binary operation exists in up to four shapes:
                - binop vAA, vBB, vCC (format 23x): three 8-bit registers, so any of v0..v255.
                - binop/2addr vA, vB (format 12x): vA is both the first source and the destination. Both registers
                  are 4-bit, so only v0..v15, but the instruction is a single 16-bit code unit.
                - binop/lit16 vA, vB, #+CCCC (format 22s): 4-bit registers and a signed 16-bit constant.
                - binop/lit8 vAA, vBB, #+CC (format 22b): 8-bit registers and a signed 8-bit constant.

                Literal forms exist only for int operations. /lit16 covers add, rsub, mul, div, rem, and, or and
                xor; /lit8 adds the three shifts on top of those.

                Because /2addr, /lit16 and the unary operations use 4-bit register fields, they only work when every
                register involved is v0..v15. In large methods the parameter registers can sit above v15, so
                compilers copy values down with move/from16 or use the 23x and 22b forms instead.
            """, title="One opcode per operation and type"),
            code("""
                .method public static compute(II)I
                    .registers 4
                    # p0 = a, p1 = b
                    add-int v0, p0, p1          # v0 = a + b
                    mul-int/lit8 v0, v0, 0xa    # v0 = v0 * 10
                    div-int v1, p0, p1          # v1 = a / b  (throws if b == 0)
                    sub-int/2addr v0, v1        # v0 = v0 - v1
                    return v0
                .end method
            """, title="Three shapes in one method"),
            java("""
                static int compute(int a, int b) {
                    return (a + b) * 10 - a / b;
                }
            """, title="Equivalent Java"),
            text("""
                There is no sub-int/lit8 or sub-int/lit16. To subtract a constant, compilers add its negative:
                x - 5 becomes add-int/lit8 v0, v0, -0x5. The opposite case, constant minus register, uses the
                reverse-subtract instructions: rsub-int vA, vB, #+CCCC (the /lit16 member of the family, written
                without a suffix) and rsub-int/lit8 vAA, vBB, #+CC both compute literal - vB.

                Shifts mask their distance: shl-int, shr-int and ushr-int use only the low 5 bits of the shift
                amount (b & 0x1f), the long variants only the low 6 bits. Shifting an int by 33 therefore shifts
                by 1, exactly as in Java. shr is the arithmetic (sign-preserving) shift, ushr the logical one.
            """, title="Constants, reverse subtraction and shifts"),
            code("""
                .method public static literals(I)V
                    .registers 8
                    # p0 = x (also known as v7)
                    rsub-int/lit8 v0, p0, 0x64      # v0 = 100 - x
                    rsub-int v1, p0, 0x3e8          # v1 = 1000 - x (16-bit literal form)
                    add-int/lit8 v2, p0, -0x1       # v2 = x - 1
                    shl-int/lit8 v3, p0, 0x3        # v3 = x << 3
                    ushr-int/lit8 v4, p0, 0x1f      # v4 = sign bit of x (0 or 1)
                    neg-int v5, p0                  # v5 = -x
                    not-int v6, p0                  # v6 = ~x (ones' complement)
                    return-void
                .end method
            """, title="Literal forms in practice"),
            text("""
                Longs and doubles occupy register pairs. In add-long v0, v2, v4 the result goes to v0 and v1, and
                the operands are read from v2/v3 and v4/v5; you only ever write the lower register number. The
                exception is the shift distance of shl-long, shr-long and ushr-long, which is a single int
                register.

                Conversions are unary instructions in format 12x: int-to-long, long-to-int (keeps the low 32
                bits), int-to-float, float-to-int, double-to-long and so on. float-to-int and double-to-int round
                toward zero, turn NaN into 0 and clamp out-of-range values to the int minimum or maximum.
                int-to-byte and int-to-short truncate with sign extension; int-to-char truncates to 16 bits
                without sign extension.
            """, title="Wide values and conversions"),
            code("""
                .method public static average(JJ)J
                    .registers 8
                    # p0/p1 = a, p2/p3 = b (each long uses two registers)
                    add-long v0, p0, p2             # v0/v1 = a + b
                    const-wide/16 v2, 0x2           # v2/v3 = 2L
                    div-long/2addr v0, v2           # v0/v1 = v0/v1 / 2
                    long-to-int v2, v0              # v2 = (int) result, just to show the conversion
                    return-wide v0
                .end method
            """, title="Working with register pairs"),
            warning("""
                div-int, rem-int, div-long, rem-long and their /2addr and literal forms throw
                ArithmeticException when the divisor is zero. Floating-point division never throws: dividing by
                0.0 produces Infinity or NaN, following IEEE 754. Note also that rem-float and rem-double are
                defined like Java's % operator (a - roundTowardZero(a / b) * b), not as the IEEE remainder.
            """, title="Division by zero"),
        ],
        exercises=[
            exercise(
                "arithmetic-ex1",
                """
                    Implement static int toFahrenheit(int c) that returns c * 9 / 5 + 32. Use the /lit8 forms so
                    that every instruction takes a single register plus a constant.
                """,
                starter="""
                    .method public static toFahrenheit(I)I
                        .registers 2
                        # p0 = c
                    .end method
                """,
                solution="""
                    .method public static toFahrenheit(I)I
                        .registers 2
                        mul-int/lit8 v0, p0, 0x9
                        div-int/lit8 v0, v0, 0x5
                        add-int/lit8 v0, v0, 0x20
                        return v0
                    .end method
                """,
                hint="9, 5 and 32 all fit in a signed 8-bit literal (-128..127).",
            ),
            exercise(
                "arithmetic-ex2",
                """
                    Rewrite each instruction in its shorter /2addr form where that preserves the meaning. Leave
                    any instruction that cannot be rewritten as it is and explain why in a comment.
                """,
                starter="""
                    add-int v0, v0, v1
                    mul-int v2, v2, v3
                    sub-int v4, v5, v4
                """,
                solution="""
                    add-int/2addr v0, v1
                    mul-int/2addr v2, v3
                    # sub-int v4, v5, v4 stays: the destination is the second operand and subtraction
                    # is not commutative, so v4 = v4 - v5 would compute a different value.
                    sub-int v4, v5, v4
                """,
                hint="/2addr always computes vA = vA op vB.",
            ),
            exercise(
                "arithmetic-ex3",
                """
                    Write static long sum(long a, long b) returning a + b. Work out how many parameter registers
                    the method has before choosing the .registers value.
                """,
                solution="""
                    .method public static sum(JJ)J
                        .registers 4
                        # p0/p1 = a, p2/p3 = b; no extra locals needed
                        add-long/2addr p0, p2
                        return-wide p0
                    .end method
                """,
                hint="Each long parameter takes two registers, so the parameters alone need four.",
            ),
        ],
        quiz=[
            question(
                "arithmetic-q1",
                "Which instruction computes 10 - v1 and stores it in v0?",
                [
                    "rsub-int/lit8 v0, v1, 0xa",
                    "sub-int/lit8 v0, v1, 0xa",
                    "add-int/lit8 v0, v1, -0xa",
                    "neg-int v0, v1",
                ],
                0,
                """
                    rsub-int/lit8 computes literal minus register. sub-int/lit8 does not exist, and
                    add-int/lit8 v0, v1, -0xa computes v1 - 10, the opposite subtraction.
                """,
            ),
            question(
                "arithmetic-q2",
                "If v1 holds 1 and v2 holds 33, what does v0 contain afterwards?",
                ["2", "0", "8589934592", "An ArithmeticException is thrown"],
                0,
                """
                    Int shifts use only the low 5 bits of the distance: 33 & 0x1f is 1, so the result is 1 << 1 = 2.
                    The register is 32-bit, so a 64-bit result is impossible.
                """,
                code="shl-int v0, v1, v2",
            ),
            question(
                "arithmetic-q3",
                "What happens when this runs with v2 = 0?",
                [
                    "An ArithmeticException is thrown",
                    "v0 becomes 0",
                    "v0 becomes Integer.MAX_VALUE",
                    "The verifier rejects the method",
                ],
                0,
                """
                    Integer division and remainder throw ArithmeticException for a zero divisor. The verifier cannot
                    know a register's runtime value, so the code is valid; only float and double division return
                    Infinity or NaN instead of throwing.
                """,
                code="div-int v0, v1, v2",
            ),
            question(
                "arithmetic-q4",
                "Which registers does add-long v0, v2, v4 read and write?",
                [
                    "Writes v0 and v1; reads v2, v3, v4 and v5",
                    "Writes v0; reads v2 and v4",
                    "Writes v0 and v1; reads v2 and v4",
                    "Writes v0, v2 and v4",
                ],
                0,
                """
                    A long occupies a register pair named by its lower register. Destination and both sources are
                    pairs, so six registers are involved.
                """,
            ),
            question(
                "arithmetic-q5",
                "Why can add-int/2addr not be used when the destination is v16?",
                [
                    "Its registers are 4-bit fields, so only v0 to v15 can be encoded",
                    "/2addr only works on parameter registers",
                    "v16 is reserved for wide values",
                    "/2addr requires a literal operand",
                ],
                0,
                """
                    /2addr uses format 12x, which packs two 4-bit register numbers into one code unit. For higher
                    registers the compiler must use the three-register 23x form.
                """,
            ),
        ],
        related=[
            "add-int", "sub-int", "mul-int", "div-int", "rem-int", "add-int/2addr", "sub-int/2addr",
            "add-int/lit8", "mul-int/lit8", "rsub-int", "rsub-int/lit8", "shl-int", "ushr-int", "add-long",
            "div-long/2addr", "neg-int", "not-int", "int-to-long", "long-to-int", "float-to-int",
        ],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="object-creation",
        level=L,
        order=17,
        title="Object Creation",
        summary="""
            Creating an object in Dalvik takes two steps: new-instance allocates it and invoke-direct runs a
            constructor. Learn the uninitialised-reference rule, how constructors chain to their superclass, and
            how check-cast and instance-of work with the objects you create.
        """,
        minutes=12,
        sections=[
            text("""
                The Java expression new Point(1, 2) is a single step in source code but two instructions in
                bytecode. new-instance vAA, type allocates an object of a (non-array) class and stores the
                reference in vAA. The object is not yet usable: its fields hold default values and no constructor
                has run. Next, invoke-direct calls one of the class's <init> methods, passing the new reference as
                the first argument, followed by the constructor arguments.

                Until that constructor call happens, the verifier treats the reference as uninitialised. You may
                copy it or pass it to <init>, but calling any other method on it, storing it in a field, or
                returning it is rejected with a VerifyError.
            """, title="Allocate, then initialise"),
            code("""
                .class public Lcom/acme/demo/Point;
                .super Ljava/lang/Object;

                .field private final x:I
                .field private final y:I

                .method public constructor <init>(II)V
                    .registers 3
                    # p0 = this, p1 = x, p2 = y
                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    iput p1, p0, Lcom/acme/demo/Point;->x:I
                    iput p2, p0, Lcom/acme/demo/Point;->y:I
                    return-void
                .end method

                .method public static origin()Lcom/acme/demo/Point;
                    .registers 2
                    new-instance v0, Lcom/acme/demo/Point;
                    const/4 v1, 0x0
                    invoke-direct {v0, v1, v1}, Lcom/acme/demo/Point;-><init>(II)V
                    return-object v0
                .end method
            """, title="A class, its constructor and a factory"),
            java("""
                public class Point {
                    private final int x;
                    private final int y;

                    public Point(int x, int y) {
                        super();
                        this.x = x;
                        this.y = y;
                    }

                    public static Point origin() {
                        return new Point(0, 0);
                    }
                }
            """, title="Equivalent Java"),
            text("""
                Constructors are methods named <init> with a void return type, flagged with the constructor access
                flag. Inside a constructor, this (p0) also starts out uninitialised: before using it for anything
                else, the constructor must call another constructor on it, either one of its own class (this(...))
                or one of the direct superclass (super(...)). That call is an invoke-direct to the superclass's
                <init>, not invoke-super, because constructors are never dispatched virtually.

                Static initialisers are different: <clinit>()V is a static constructor that runs once when the
                class is initialised and never receives a this register.
            """, title="Constructor chaining"),
            code("""
                .class public Lcom/acme/demo/LabeledPoint;
                .super Lcom/acme/demo/Point;

                .field private final label:Ljava/lang/String;

                .method public constructor <init>(Ljava/lang/String;)V
                    .registers 3
                    # p0 = this, p1 = label
                    const/4 v0, 0x0
                    invoke-direct {p0, v0, v0}, Lcom/acme/demo/Point;-><init>(II)V
                    iput-object p1, p0, Lcom/acme/demo/LabeledPoint;->label:Ljava/lang/String;
                    return-void
                .end method
            """, title="Calling the superclass constructor"),
            text("""
                Once you have an object, two instructions check its type. instance-of vA, vB, type stores 1 in vA
                if the reference in vB is an instance of the type and 0 otherwise (null gives 0). check-cast vAA,
                type throws ClassCastException if the reference cannot be cast; null always passes. After a
                successful check-cast, the verifier treats the register as having the new type, which is why
                compilers emit it before calling a method that only exists on the subtype.
            """, title="instance-of and check-cast"),
            code("""
                .method public static lengthOrMinusOne(Ljava/lang/Object;)I
                    .registers 2
                    instance-of v0, p0, Ljava/lang/String;
                    if-eqz v0, :not_string
                    check-cast p0, Ljava/lang/String;
                    invoke-virtual {p0}, Ljava/lang/String;->length()I
                    move-result v0
                    return v0

                    :not_string
                    const/4 v0, -0x1
                    return v0
                .end method
            """, title="Type test before a cast"),
            tip("""
                String concatenation is a common source of object creation. For "x=" + x, D8 typically emits a
                StringBuilder chain: new-instance, invoke-direct <init>, one append per piece (each followed by
                move-result-object), then toString(). Recognising that pattern lets you read concatenations at a
                glance.
            """, title="Spotting StringBuilder chains"),
        ],
        exercises=[
            exercise(
                "object-creation-ex1",
                "Write static java.util.List make() that creates and returns a new java.util.ArrayList.",
                solution="""
                    .method public static make()Ljava/util/List;
                        .registers 1
                        new-instance v0, Ljava/util/ArrayList;
                        invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                        return-object v0
                    .end method
                """,
                hint="Allocate with new-instance, then call the no-argument <init> with invoke-direct.",
            ),
            exercise(
                "object-creation-ex2",
                """
                    This code assembles but fails verification when the class is loaded. Find the problem and fix
                    it.
                """,
                starter="""
                    new-instance v0, Ljava/lang/StringBuilder;
                    const-string v1, "hi"
                    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                """,
                solution="""
                    new-instance v0, Ljava/lang/StringBuilder;
                    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
                    const-string v1, "hi"
                    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                """,
                hint="Which instruction must run before any other method is called on a freshly allocated object?",
            ),
            exercise(
                "object-creation-ex3",
                """
                    Write static String label(int x) that returns "x=" + x using a StringBuilder. Use the
                    StringBuilder(String) constructor to pass the prefix.
                """,
                solution="""
                    .method public static label(I)Ljava/lang/String;
                        .registers 3
                        new-instance v0, Ljava/lang/StringBuilder;
                        const-string v1, "x="
                        invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
                        invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
                        move-result-object v0
                        invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v0
                        return-object v0
                    .end method
                """,
                hint="append(I) returns the same builder, so you can keep it in v0 with move-result-object.",
            ),
        ],
        quiz=[
            question(
                "object-creation-q1",
                "Which instruction calls a constructor on a newly allocated object?",
                ["invoke-direct", "invoke-virtual", "invoke-super", "invoke-static"],
                0,
                """
                    Constructors are direct methods: they are never dispatched through the vtable, so both
                    new Foo() and super(...) calls use invoke-direct on <init>.
                """,
            ),
            question(
                "object-creation-q2",
                "What is wrong with this sequence?",
                [
                    "toString() is called on an object whose constructor has not run",
                    "new-instance cannot create java.lang.Object",
                    "invoke-virtual cannot be used on v0",
                    "Nothing; it returns a valid string",
                ],
                0,
                """
                    After new-instance the reference is uninitialised. The verifier only allows it to be passed to
                    <init>; calling toString() first causes a VerifyError.
                """,
                code="""
                    new-instance v0, Ljava/lang/Object;
                    invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;
                """,
            ),
            question(
                "object-creation-q3",
                "What does instance-of store when the source register holds null?",
                ["0", "1", "It throws NullPointerException", "It leaves the destination unchanged"],
                0,
                """
                    null is not an instance of any type, so instance-of stores 0. check-cast, in contrast, lets
                    null pass without throwing.
                """,
            ),
            question(
                "object-creation-q4",
                "Which type can new-instance not allocate?",
                ["An array type such as [I", "An abstract class's concrete subclass", "java.lang.Object", "A nested class"],
                0,
                """
                    new-instance requires a non-array class. Arrays are created with new-array or
                    filled-new-array.
                """,
            ),
            question(
                "object-creation-q5",
                "In a subclass constructor, what must happen before this (p0) is used for anything else?",
                [
                    "Another constructor of this class or the direct superclass must be invoked on p0",
                    "check-cast p0 to the subclass",
                    "monitor-enter p0",
                    "Nothing; p0 is fully initialised on entry",
                ],
                0,
                """
                    this starts uninitialised inside <init>. The verifier requires a call to this(...) or the
                    superclass's <init> on p0 before it can be used like a normal object, for example to call its
                    methods or return it.
                """,
            ),
        ],
        related=["new-instance", "invoke-direct", "check-cast", "instance-of", "iput", "iput-object", "move-result-object", "return-object"],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="return-instructions",
        level=L,
        order=18,
        title="Return Instructions",
        summary="""
            Four instructions end a method: return-void, return, return-wide and return-object. Learn how to pick
            the one that matches the method's return type, how constructors return, and why compilers often
            route several branches through one shared exit.
        """,
        minutes=9,
        sections=[
            text("""
                The return instruction must match the return type in the method prototype:
                - return-void for V, including every constructor and static initialiser;
                - return vAA for 32-bit primitives: I, Z, B, S, C and F;
                - return-wide vAA for J and D, naming the lower register of the pair;
                - return-object vAA for any reference type: classes, interfaces and arrays.

                The register is 8 bits wide, so v0..v255 can be returned directly. A mismatch, such as return in a
                method declared ()J, assembles without complaint but is rejected by the verifier.
            """, title="Choosing the right return"),
            code("""
                .method public static twice(J)J
                    .registers 4
                    # p0/p1 = value
                    add-long v0, p0, p0
                    return-wide v0
                .end method

                .method public static isEmpty(Ljava/lang/String;)Z
                    .registers 2
                    invoke-virtual {p0}, Ljava/lang/String;->length()I
                    move-result v0
                    if-nez v0, :not_empty
                    const/4 v0, 0x1
                    return v0

                    :not_empty
                    const/4 v0, 0x0
                    return v0
                .end method
            """, title="Wide and boolean returns"),
            tip("""
                A boolean is just an int that is 0 or 1, so it is returned with plain return. Returning null from a
                method with a reference return type uses const/4 v0, 0x0 followed by return-object v0.
            """),
            text("""
                A method may contain any number of return instructions, and every path through the code must end in
                one (or in throw or an infinite loop): execution is not allowed to run past the last instruction.
                Some compilers return directly from each branch; others assign the result to a register and jump
                to a single exit block. Both are valid, and you will see both styles in real apps.
            """, title="Multiple returns or one exit"),
            code("""
                .method public static sign(I)I
                    .registers 2
                    if-lez p0, :not_positive
                    const/4 v0, 0x1
                    goto :exit

                    :not_positive
                    if-nez p0, :negative
                    const/4 v0, 0x0
                    goto :exit

                    :negative
                    const/4 v0, -0x1

                    :exit
                    return v0
                .end method
            """, title="A shared exit block"),
            java("""
                static int sign(int value) {
                    int result;
                    if (value > 0) {
                        result = 1;
                    } else if (value == 0) {
                        result = 0;
                    } else {
                        result = -1;
                    }
                    return result;
                }
            """, title="Equivalent Java"),
            warning("""
                Forgetting return-void at the end of a constructor or void method is a classic hand-editing
                mistake. The assembler accepts it, but the verifier reports that control flow falls off the end
                of the code.
            """, title="Do not fall off the end"),
        ],
        exercises=[
            exercise(
                "return-instructions-ex1",
                "This method should return 4294967296L. Fix the return instruction.",
                starter="""
                    .method public static big()J
                        .registers 2
                        const-wide v0, 0x100000000L
                        return v0
                    .end method
                """,
                solution="""
                    .method public static big()J
                        .registers 2
                        const-wide v0, 0x100000000L
                        return-wide v0
                    .end method
                """,
                hint="The return type J is 64 bits wide.",
            ),
            exercise(
                "return-instructions-ex2",
                'Write static String choose(boolean b) that returns "yes" when b is true and null otherwise.',
                solution="""
                    .method public static choose(Z)Ljava/lang/String;
                        .registers 2
                        if-eqz p0, :no
                        const-string v0, "yes"
                        return-object v0

                        :no
                        const/4 v0, 0x0
                        return-object v0
                    .end method
                """,
                hint="null is the constant 0 returned with return-object.",
            ),
            exercise(
                "return-instructions-ex3",
                "This constructor fails verification. What is missing? Write the corrected method.",
                starter="""
                    .method public constructor <init>()V
                        .registers 1
                        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                    .end method
                """,
                solution="""
                    .method public constructor <init>()V
                        .registers 1
                        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                        return-void
                    .end method
                """,
                hint="Constructors have the return type V.",
            ),
        ],
        quiz=[
            question(
                "return-instructions-q1",
                "Which instruction returns a double?",
                ["return-wide", "return", "return-object", "return-double"],
                0,
                "double and long are 64-bit values held in a register pair, returned with return-wide. return-double does not exist.",
            ),
            question(
                "return-instructions-q2",
                "A method is declared ()[I. Which return instruction does it use?",
                ["return-object", "return", "return-wide", "return-void"],
                0,
                "Arrays are references, so even an int array is returned with return-object.",
            ),
            question(
                "return-instructions-q3",
                "How does a constructor return?",
                ["return-void", "return-object p0", "return p0", "It needs no return instruction"],
                0,
                """
                    <init> methods have the return type V. The new object is already in the caller's register from
                    new-instance, so nothing is returned.
                """,
            ),
            question(
                "return-instructions-q4",
                "What happens with this method?",
                [
                    "It assembles, but the verifier rejects it",
                    "The assembler refuses to build it",
                    "It returns 1 as a long",
                    "It returns 1 and the caller ignores the upper half",
                ],
                0,
                """
                    The assembler does not check types. At class load the verifier sees a 32-bit return in a
                    method whose prototype returns J and throws VerifyError.
                """,
                code="""
                    .method public static one()J
                        .registers 1
                        const/4 v0, 0x1
                        return v0
                    .end method
                """,
            ),
            question(
                "return-instructions-q5",
                "Which statement about return instructions is true?",
                [
                    "A method may contain several return instructions",
                    "Only the last instruction of a method may be a return",
                    "return can only use registers v0 to v15",
                    "return-void is optional at the end of void methods",
                ],
                0,
                """
                    Any branch may return. The return opcodes use format 11x with an 8-bit register, and control
                    must never fall off the end of a method.
                """,
            ),
        ],
        related=["return-void", "return", "return-wide", "return-object", "goto"],
    ),
    # ------------------------------------------------------------------------------------------------------------
    lesson(
        id="exception-handling",
        level=L,
        order=19,
        title="Exception Handling",
        summary="""
            Smali marks protected code with try labels and attaches handlers with .catch and .catchall. Learn how
            move-exception and throw work, how handlers are ordered, why finally blocks appear twice, and how
            synchronized blocks use a catch-all handler to release their monitor.
        """,
        minutes=15,
        sections=[
            text("""
                In a .dex file, exception handling is a table attached to the method's code: each entry covers a
                range of instructions and lists handler addresses for exception types. Smali writes that table
                with labels. You place a start label and an end label around the protected instructions and then
                declare a handler:

                .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

                .catchall {:try_start_0 .. :try_end_0} :catchall_0 does the same for every Throwable. The label
                names are free to choose; baksmali simply generates try_start_N, try_end_N and catch_N.
            """, title="Try ranges and handlers"),
            code("""
                .method public static parseOrDefault(Ljava/lang/String;I)I
                    .registers 3
                    # p0 = text, p1 = fallback
                    :try_start_0
                    invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
                    move-result v0
                    :try_end_0
                    .catch Ljava/lang/NumberFormatException; {:try_start_0 .. :try_end_0} :catch_0

                    return v0

                    :catch_0
                    move-exception v0
                    return p1
                .end method
            """, title="Catching an exception"),
            java("""
                static int parseOrDefault(String text, int fallback) {
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                }
            """, title="Equivalent Java"),
            text("""
                When an exception is thrown inside a covered range, the runtime checks that range's handlers in
                the order they are listed and jumps to the first one whose type matches; a catch-all handler is
                always considered last. List more specific types first: a catch for IOException placed before one
                for FileNotFoundException would swallow both.

                move-exception vAA copies the caught exception into a register. It must be the very first
                instruction of a handler, and it is not allowed anywhere else; a handler that does not need the
                exception object can omit it. throw vAA raises the exception held in a register, which can be one
                you just created or one you caught.
            """, title="Handler order, move-exception and throw"),
            code("""
                .method public static requirePositive(I)I
                    .registers 3
                    if-lez p0, :bad
                    return p0

                    :bad
                    new-instance v0, Ljava/lang/IllegalArgumentException;
                    const-string v1, "value must be positive"
                    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V
                    throw v0
                .end method
            """, title="Creating and throwing an exception"),
            text("""
                Dalvik has no instruction for finally. The compiler copies the finally code onto every exit path:
                once after the protected code for normal completion and once in a catch-all handler that runs the
                cleanup and rethrows. Seeing the same calls twice is therefore expected, not a bug.

                synchronized blocks follow the same idea. monitor-enter acquires the lock, the body is covered by a
                catch-all, and both the normal path and the handler execute monitor-exit before leaving. Compilers
                usually protect the handler's own monitor-exit too, so the lock is released even if that step
                faults. try-with-resources expands into the same building blocks: nested try ranges, a call to
                close() on each path, and addSuppressed() when close() throws during an exception.
            """, title="finally and synchronized"),
            code("""
                .method public increment()V
                    .registers 3
                    monitor-enter p0
                    :try_start_0
                    iget v0, p0, Lcom/acme/demo/Counter;->count:I
                    add-int/lit8 v0, v0, 0x1
                    iput v0, p0, Lcom/acme/demo/Counter;->count:I
                    monitor-exit p0
                    :try_end_0
                    .catchall {:try_start_0 .. :try_end_0} :catchall_0
                    return-void

                    :catchall_0
                    move-exception v1
                    :try_start_1
                    monitor-exit p0
                    :try_end_1
                    .catchall {:try_start_1 .. :try_end_1} :catchall_0
                    throw v1
                .end method
            """, title="synchronized (this) { count++; }"),
            warning("""
                When editing smali, keep every instruction that can throw inside the intended try range and do not
                move labels casually. Code placed between :try_end and the handler is not protected, and an
                instruction other than move-exception at the start of a handler that uses the exception makes the
                method fail verification.
            """, title="Labels define the protection"),
        ],
        exercises=[
            exercise(
                "exception-handling-ex1",
                """
                    Write static boolean pause(long millis) that calls Thread.sleep(millis) and returns true, or
                    returns false if an InterruptedException is caught.
                """,
                solution="""
                    .method public static pause(J)Z
                        .registers 4
                        # p0/p1 = millis
                        :try_start_0
                        invoke-static {p0, p1}, Ljava/lang/Thread;->sleep(J)V
                        :try_end_0
                        .catch Ljava/lang/InterruptedException; {:try_start_0 .. :try_end_0} :catch_0

                        const/4 v0, 0x1
                        return v0

                        :catch_0
                        move-exception v0
                        const/4 v1, 0x0
                        return v1
                    .end method
                """,
                hint="A long argument is passed as both registers of its pair: {p0, p1}.",
            ),
            exercise(
                "exception-handling-ex2",
                "The handler below fails verification. Explain why and fix it.",
                starter="""
                    .method public static safeLength(Ljava/lang/String;)I
                        .registers 3
                        :try_start_0
                        invoke-virtual {p0}, Ljava/lang/String;->length()I
                        move-result v0
                        :try_end_0
                        .catch Ljava/lang/NullPointerException; {:try_start_0 .. :try_end_0} :catch_0
                        return v0

                        :catch_0
                        const/4 v1, 0x0
                        move-exception v0
                        return v1
                    .end method
                """,
                solution="""
                    .method public static safeLength(Ljava/lang/String;)I
                        .registers 3
                        :try_start_0
                        invoke-virtual {p0}, Ljava/lang/String;->length()I
                        move-result v0
                        :try_end_0
                        .catch Ljava/lang/NullPointerException; {:try_start_0 .. :try_end_0} :catch_0
                        return v0

                        # move-exception must be the first instruction of the handler
                        :catch_0
                        move-exception v0
                        const/4 v1, 0x0
                        return v1
                    .end method
                """,
                hint="Look at the order of the first two instructions after :catch_0.",
            ),
            exercise(
                "exception-handling-ex3",
                """
                    Write the instructions that throw new IllegalStateException("closed"). Assume v0 and v1 are
                    free.
                """,
                solution="""
                    new-instance v0, Ljava/lang/IllegalStateException;
                    const-string v1, "closed"
                    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V
                    throw v0
                """,
                hint="Allocate, call the (Ljava/lang/String;)V constructor, then throw the register.",
            ),
        ],
        quiz=[
            question(
                "exception-handling-q1",
                "Where may move-exception appear?",
                [
                    "Only as the first instruction of an exception handler",
                    "Anywhere inside a try range",
                    "Directly after any invoke instruction",
                    "Only at the end of a method",
                ],
                0,
                """
                    The bytecode rules require move-exception to be the first instruction of a handler and forbid it
                    everywhere else. Handlers that ignore the exception can leave it out.
                """,
            ),
            question(
                "exception-handling-q2",
                "Both handlers cover the same range. Which one runs for a FileNotFoundException?",
                [
                    ":catch_io, because handlers are checked in the listed order",
                    ":catch_missing, because it is the most specific type",
                    "Both, one after the other",
                    "Neither; the verifier rejects overlapping handlers",
                ],
                0,
                """
                    FileNotFoundException is a subclass of IOException. The first matching handler wins, so
                    specific types must be listed before their superclasses.
                """,
                code="""
                    :try_start_0
                    invoke-virtual {p0}, Ljava/io/InputStream;->read()I
                    :try_end_0
                    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_io
                    .catch Ljava/io/FileNotFoundException; {:try_start_0 .. :try_end_0} :catch_missing
                    return-void

                    :catch_io
                    return-void

                    :catch_missing
                    return-void
                """,
            ),
            question(
                "exception-handling-q3",
                "Why does the cleanup call of a finally block often appear twice in smali?",
                [
                    "The compiler copies it onto the normal path and into a catch-all handler that rethrows",
                    "baksmali duplicates code to make it easier to read",
                    "Dalvik executes finally blocks twice",
                    "One copy is for 32-bit and one for 64-bit devices",
                ],
                0,
                """
                    Dalvik has no finally instruction. Compilers inline the finally code on each exit path, using a
                    .catchall handler for the exceptional one.
                """,
            ),
            question(
                "exception-handling-q4",
                "What does .catchall catch?",
                [
                    "Any Throwable thrown in its range",
                    "Only unchecked exceptions",
                    "Only java.lang.Exception and subclasses",
                    "Only exceptions thrown with the throw instruction",
                ],
                0,
                """
                    A catch-all handler has no type and matches every Throwable, including errors and exceptions
                    raised by the runtime itself, such as NullPointerException.
                """,
            ),
            question(
                "exception-handling-q5",
                "In a synchronized block, why is the body covered by a .catchall handler?",
                [
                    "So that monitor-exit runs and the lock is released even when the body throws",
                    "Because monitor-enter cannot be used without one",
                    "To convert checked exceptions into unchecked ones",
                    "To retry the body after an exception",
                ],
                0,
                """
                    The handler calls monitor-exit on the same object and rethrows the exception, mirroring the
                    guarantee that leaving a synchronized block always releases its lock.
                """,
            ),
        ],
        related=["move-exception", "throw", "monitor-enter", "monitor-exit", "new-instance", "invoke-direct"],
    ),
]

FLASHCARDS = [
    card("fc-i2-01", "Opcodes", "What does rsub-int/lit8 v0, v1, 0x5 compute?",
         "v0 = 5 - v1. The rsub (reverse subtract) instructions subtract the register from the literal. "
         "rsub-int is the 16-bit literal form."),
    card("fc-i2-02", "Opcodes", "Which arithmetic instructions throw ArithmeticException?",
         "Integer division and remainder (div-int, rem-int, div-long, rem-long and their /2addr and literal forms) "
         "when the divisor is zero. Float and double division never throw."),
    card("fc-i2-03", "Formats", "What is the format of add-int/2addr and what does it compute?",
         "Format 12x: two 4-bit registers. add-int/2addr vA, vB computes vA = vA + vB, so only v0 to v15 can be used."),
    card("fc-i2-04", "Formats", "How do the /lit8 and /lit16 binop forms differ?",
         "/lit8 is format 22b: 8-bit registers and a signed 8-bit literal. /lit16 is format 22s: 4-bit registers "
         "and a signed 16-bit literal. Only /lit8 has shift variants."),
    card("fc-i2-05", "Registers", "Which registers does add-long v0, v2, v4 use?",
         "It writes the pair v0/v1 and reads the pairs v2/v3 and v4/v5. Wide values are named by their lower register."),
    card("fc-i2-06", "Opcodes", "How many bits of the shift distance do shl-int and shl-long use?",
         "shl-int, shr-int and ushr-int use the low 5 bits (distance & 0x1f); the long versions use the low 6 bits. "
         "The long shifts take the distance from a single int register."),
    card("fc-i2-07", "Opcodes", "What does float-to-int do with NaN and huge values?",
         "It rounds toward zero, converts NaN to 0, and clamps values that are too large to 0x7fffffff or -0x80000000."),
    card("fc-i2-08", "Opcodes", "What must happen after new-instance before the object is used?",
         "A constructor (<init>) must be called on it with invoke-direct. Until then the reference is uninitialised "
         "and the verifier rejects other uses.",
         code="""
            new-instance v0, Ljava/util/ArrayList;
            invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
         """),
    card("fc-i2-09", "Opcodes", "check-cast versus instance-of?",
         "instance-of vA, vB, type stores 1 or 0 (null gives 0). check-cast vAA, type throws ClassCastException on "
         "failure and lets null pass; afterwards the register has the checked type."),
    card("fc-i2-10", "Opcodes", "Where is move-exception allowed?",
         "Only as the first instruction of an exception handler. It stores the caught exception in an 8-bit register."),
    card("fc-i2-11", "Directives", "What is the syntax of a typed catch handler in smali?",
         "List the exception type, the covered label range and the handler label.",
         code="""
            :try_start_0
            invoke-virtual {p0}, Ljava/io/InputStream;->close()V
            :try_end_0
            .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0
            return-void
            :catch_0
            return-void
         """),
    card("fc-i2-12", "Directives", "When do compilers emit .catchall?",
         "For finally blocks (cleanup copied into a handler that rethrows) and synchronized blocks (the handler "
         "runs monitor-exit before rethrowing)."),
    card("fc-i2-13", "Runtime", "What happens if a method declared to return J uses return instead of return-wide?",
         "The assembler accepts it, but the verifier rejects the method when the class is loaded, raising "
         "VerifyError."),
    card("fc-i2-14", "Opcodes", "Which return instruction does a method returning boolean use?",
         "Plain return vAA. Booleans, bytes, chars, shorts, ints and floats are all 32-bit values; return-wide is for "
         "long/double and return-object for references."),
]
