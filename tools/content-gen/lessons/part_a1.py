"""Advanced track, part 1 (orders 20-24)."""
from common import card, code, exercise, java, lesson, question, text, tip, warning

L = "advanced"

LESSONS = [
    # ------------------------------------------------------------------------------------------------ 20
    lesson(
        id="dex-structure",
        level=L,
        order=20,
        title="DEX Structure",
        summary="""
            Look inside a classes.dex file: the 0x70-byte header, the sorted ID tables that every class, field,
            method and string reference points into, the data section and the map_list that describes it all.
            You will learn how a line of smali turns into table indices.
        """,
        minutes=14,
        sections=[
            text("""
                A .dex file is a single container for many classes. Instead of every class carrying its own
                constant pool (as JVM .class files do), all classes in the file share a set of de-duplicated
                tables. The file is laid out in this order:
                - header
                - string_ids, type_ids, proto_ids, field_ids, method_ids, class_defs
                - call_site_ids and method_handles (DEX version 038 and later)
                - data (everything the tables point at)
                - link_data (unspecified, empty in normal app files)

                All multi-byte values are little-endian. Most tables are arrays of fixed-size items, so the n-th
                entry can be found with simple arithmetic: offset + n * item size.
            """, title="One file, shared tables"),
            text("""
                The header_item starts at offset 0 and, for format versions up to 040, is exactly 0x70 (112)
                bytes long:
                - 0x00 magic: 8 bytes, "dex\\n" followed by a three-digit version and a zero byte, e.g.
                  64 65 78 0a 30 33 39 00 for "dex\\n039\\0".
                - 0x08 checksum: Adler-32 of everything after this field (the whole file minus magic and checksum).
                - 0x0C signature: 20-byte SHA-1 of everything after the signature field.
                - 0x20 file_size, 0x24 header_size (0x70), 0x28 endian_tag (0x12345678, stored as 78 56 34 12).
                - 0x2C link_size, 0x30 link_off, 0x34 map_off.
                - 0x38 to 0x67: size/offset pairs for string_ids, type_ids, proto_ids, field_ids, method_ids and
                  class_defs.
                - 0x68 data_size, 0x6C data_off.

                The version tells you which features may appear: 035 is the classic format, 037 arrived with
                Android 7.0 (default interface methods), 038 with Android 8.0 (invoke-polymorphic, invoke-custom,
                method handles, call sites), 039 with Android 9 (const-method-handle, const-method-type) and 040
                with Android 10 (wider SimpleName character set). Version 041, an experimental container format in
                Android 16, enlarges the header to 0x78 bytes.
            """, title="The header"),
            text("""
                Each ID table is sorted and free of duplicates, which lets tools binary-search them:
                - string_id_item (4 bytes): offset of a string_data_item, which holds a ULEB128 UTF-16 length
                  followed by MUTF-8 bytes and a terminating 0. Sorted by string contents.
                - type_id_item (4 bytes): descriptor_idx, an index into string_ids such as "Ljava/lang/String;".
                - proto_id_item (12 bytes): shorty_idx, return_type_idx and parameters_off (a type_list, or 0).
                - field_id_item (8 bytes): class_idx (ushort), type_idx (ushort), name_idx (uint).
                - method_id_item (8 bytes): class_idx (ushort), proto_idx (ushort), name_idx (uint).
                - class_def_item (32 bytes): class_idx, access_flags, superclass_idx, interfaces_off,
                  source_file_idx, annotations_off, class_data_off, static_values_off.
                - call_site_id_item (4 bytes) and method_handle_item (8 bytes) for the invoke-custom and
                  method-handle features.

                class_defs has one extra rule: a superclass or implemented interface defined in the same file
                must appear before the classes that extend it.
            """, title="The ID tables"),
            code("""
                .class public Lcom/acme/demo/Counter;
                .super Ljava/lang/Object;
                .source "Counter.java"

                # field_ids entry: class_idx -> Lcom/acme/demo/Counter;, type_idx -> I, name_idx -> "count"
                .field private count:I

                .method public increment()V
                    .registers 3

                    # field@ index into field_ids
                    iget v0, p0, Lcom/acme/demo/Counter;->count:I
                    add-int/lit8 v0, v0, 0x1
                    iput v0, p0, Lcom/acme/demo/Counter;->count:I

                    # string@ index into string_ids
                    const-string v1, "Counter"

                    # meth@ index into method_ids; its proto_idx points at the shorty "VL"
                    invoke-static {v1}, Lcom/acme/demo/Counter;->log(Ljava/lang/String;)V
                    return-void
                .end method

                .method private static log(Ljava/lang/String;)V
                    .registers 1
                    return-void
                .end method
            """, title="Every reference becomes an index"),
            text("""
                The data section holds everything variable-sized: class_data_item (the field and method lists of a
                class, ULEB128-encoded), code_item (registers_size, ins_size, outs_size, tries_size,
                debug_info_off, insns_size and the instructions themselves), string data, type_lists, annotation
                structures, encoded arrays with static field initial values and debug info.

                map_list, found at header.map_off inside the data section, is a table of contents for the whole
                file. Each 12-byte map_item records a type code (0x0000 header_item, 0x0001 string_id_item,
                0x1000 map_list, 0x2001 code_item and so on), an item count and an offset. Entries are ordered by
                offset and never overlap, which makes map_list the easiest way to walk a file you know nothing
                about.

                link_data is reserved for statically linked files; its format is left to the runtime and it is
                empty in the dex files produced by D8 and R8.
            """, title="The data section and map_list"),
            tip("""
                Instructions such as invoke-* and the field accessors encode their reference as a 16-bit index
                (meth@BBBB, field@CCCC). A single dex file can therefore reference at most 65,536 methods, which is
                why large apps are split into classes.dex, classes2.dex and so on (multidex). Strings have an escape
                hatch: const-string/jumbo takes a 32-bit string index.
            """, title="Where the 64K limit comes from"),
            warning("""
                Patching bytes in a .dex file by hand invalidates the checksum and signature. Reassembling with
                smali (or any dex writer) recomputes both; a stale checksum makes tools and the runtime treat the
                file as corrupt.
            """, title="Checksums matter"),
        ],
        exercises=[
            exercise(
                "dex-structure-ex1",
                """
                    A file starts with the bytes 64 65 78 0a 30 33 38 00. Which format version is it, and which
                    instructions might you find in it that a 035 file cannot contain?
                """,
                solution="""
                    The magic decodes to "dex\\n038\\0", so this is version 038 (introduced with Android 8.0). It may
                    contain invoke-polymorphic, invoke-polymorphic/range, invoke-custom and invoke-custom/range,
                    together with call_site_ids and method_handles tables. const-method-handle and const-method-type
                    need version 039.
                """,
                hint="Bytes 0x30-0x39 are the ASCII digits '0'-'9'.",
            ),
            exercise(
                "dex-structure-ex2",
                """
                    List every ID table that is touched when the runtime resolves this instruction:
                    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                """,
                solution="""
                    meth@ selects a method_id_item. Its class_idx points into type_ids (Ljava/lang/StringBuilder;),
                    whose descriptor_idx points into string_ids. Its name_idx points into string_ids ("append"). Its
                    proto_idx points into proto_ids, whose shorty_idx is the string "LL", whose return_type_idx is
                    the StringBuilder type, and whose parameters_off leads to a type_list containing the type id of
                    Ljava/lang/String;. So: method_ids, type_ids, proto_ids and string_ids (plus a type_list in the
                    data section).
                """,
                hint="Follow class_idx, proto_idx and name_idx, then follow what each of those points to.",
            ),
            exercise(
                "dex-structure-ex3",
                """
                    A huge generated class has more than 65,536 distinct strings. Write a static method
                    banner()Ljava/lang/String; that loads the string "Welcome" with an instruction that works no
                    matter how large the string index becomes.
                """,
                solution="""
                    .method public static banner()Ljava/lang/String;
                        .registers 1
                        const-string/jumbo v0, "Welcome"
                        return-object v0
                    .end method
                """,
                hint="const-string uses a 16-bit index (format 21c); its jumbo variant uses format 31c.",
            ),
        ],
        quiz=[
            question(
                "dex-structure-q1",
                "What is the value of header_size in a version 035-040 dex file?",
                ["0x70 (112 bytes)", "0x40 (64 bytes)", "0x78 (120 bytes)", "It varies with the number of classes"],
                0,
                """
                    The header is a fixed 0x70 bytes up to version 040. Only the experimental 041 container format
                    grows it to 0x78 bytes to add container_size and header_offset.
                """,
            ),
            question(
                "dex-structure-q2",
                "What does the header's checksum field hold?",
                [
                    "An Adler-32 checksum of the file after the checksum field",
                    "A SHA-1 hash of the whole file",
                    "A CRC-32 of the data section only",
                    "The APK signing certificate digest",
                ],
                0,
                """
                    checksum is Adler-32 over everything except magic and checksum itself. The separate 20-byte
                    signature field is a SHA-1 hash over everything after the signature.
                """,
            ),
            question(
                "dex-structure-q3",
                "Which three fields make up a method_id_item?",
                [
                    "class_idx, proto_idx, name_idx",
                    "class_idx, access_flags, code_off",
                    "name_idx, shorty_idx, parameters_off",
                    "type_idx, name_idx, descriptor_idx",
                ],
                0,
                """
                    A method_id_item is 8 bytes: the defining class (ushort index into type_ids), the prototype
                    (ushort index into proto_ids) and the name (uint index into string_ids). Access flags and code
                    live in class_data_item, not in the ID table.
                """,
            ),
            question(
                "dex-structure-q4",
                "What does the operand of this instruction refer to inside the dex file?",
                [
                    "An index into the string_ids table",
                    "A byte offset to the string inside the instruction stream",
                    "An index into type_ids",
                    "A pointer into the APK's resources.arsc",
                ],
                0,
                """
                    const-string encodes string@BBBB, a 16-bit index into string_ids. The string_id_item then
                    points to the MUTF-8 string data in the data section.
                """,
                code='const-string v0, "ok"',
            ),
            question(
                "dex-structure-q5",
                "Where is the map_list stored?",
                [
                    "In the data section, at the offset given by header.map_off",
                    "Immediately after the header, before string_ids",
                    "In the APK's META-INF directory",
                    "At the very end of link_data",
                ],
                0,
                """
                    map_off must be non-zero and point into the data section. The map lists every section with its
                    type, item count and offset, ordered by offset.
                """,
            ),
            question(
                "dex-structure-q6",
                "Which dex format version first allowed invoke-custom and method handles?",
                ["035", "037", "038", "039"],
                2,
                """
                    Version 038 (Android 8.0) added invoke-polymorphic, invoke-custom, call sites and method handles.
                    039 (Android 9) added the const-method-handle and const-method-type instructions.
                """,
            ),
        ],
        related=["const-string", "const-string/jumbo", "iget", "iput", "invoke-static", "invoke-virtual"],
    ),
    # ------------------------------------------------------------------------------------------------ 21
    lesson(
        id="instruction-encoding",
        level=L,
        order=21,
        title="Instruction Encoding",
        summary="""
            Learn how Dalvik instructions are packed into 16-bit code units, how to read format ids such as 12x,
            22c, 35c and 51l, and how to encode and decode instructions by hand. You will also see why payload
            tables must be aligned.
        """,
        minutes=15,
        sections=[
            text("""
                A method's bytecode (code_item.insns) is an array of 16-bit code units, stored little-endian. Every
                instruction occupies one or more whole code units, and the opcode is always the low 8 bits of the
                first unit. The remaining 8 bits of that first unit, and any following units, hold operands.

                Sizes and branch offsets are always counted in code units, not bytes: an instruction at address 3
                with a length of 2 is followed by the instruction at address 5.
            """, title="Code units"),
            text("""
                Every opcode has a format, named by a short id:
                - First digit: number of code units (1-5).
                - Second digit: maximum number of registers, or "r" for a register range.
                - Final letter: kind of extra data. x = none, n = 4-bit literal, b = 8-bit literal, s = 16-bit
                  literal, h = 16-bit literal holding high-order bits, i = 32-bit literal, l = 64-bit literal,
                  t = branch target, c = constant pool index. 45cc and 4rcc carry two pool indices.

                The bitwise layout reads high bits to low; each letter is 4 bits and "op" is the 8-bit opcode:
                - 10x  ØØ|op                       e.g. return-void, nop
                - 12x  B|A|op                      e.g. move vA, vB; add-int/2addr
                - 11n  B|A|op                      const/4 vA, #+B
                - 21s  AA|op BBBB                  const/16 vAA, #+BBBB
                - 22c  B|A|op CCCC                 iget vA, vB, field@CCCC
                - 23x  AA|op CC|BB                 add-int vAA, vBB, vCC
                - 35c  A|G|op BBBB F|E|D|C         invoke-* with up to 5 argument registers
                - 3rc  AA|op BBBB CCCC             invoke-*/range {vCCCC .. vNNNN}
                - 45cc A|G|op BBBB F|E|D|C HHHH    invoke-polymorphic (method + proto)
                - 51l  AA|op BBBB BBBB BBBB BBBB   const-wide vAA, 64-bit literal
            """, title="Reading format ids"),
            code("""
                .method public static demo(II)I
                    .registers 6

                    const/4 v0, 0x1                 # 11n: 1 code unit
                    const/16 v1, 0x2a               # 21s: 2 code units
                    add-int v2, p0, p1              # 23x: 2 code units
                    add-int/2addr v2, v0            # 12x: 1 code unit
                    const-wide v0, 0x123456789L     # 51l: 5 code units (v0 and v1 hold the long)
                    invoke-static {p0, p1}, Ljava/lang/Math;->max(II)I   # 35c: 3 code units
                    move-result v0                  # 11x: 1 code unit
                    return v0                       # 11x: 1 code unit
                .end method
            """, title="Formats in a real method"),
            text("""
                Encode const/4 v0, 1. Format 11n is B|A|op with op = 0x12, A = 0 (register) and B = 1 (literal),
                giving the code unit 0x1012, stored in the file as the bytes 12 10.

                return-void is format 10x with op 0x0e: the unit 0x000e, bytes 0e 00.

                add-int v0, v1, v2 uses 23x (AA|op CC|BB). Unit one is 0x0090 (AA = 0, op = 0x90); unit two
                is 0x0201 (CC = 2 in the high byte, BB = 1 in the low byte). Bytes: 90 00 01 02.

                add-int/2addr v1, v2 is 12x: B = 2, A = 1, op = 0xb0, so 0x21b0, bytes b0 21.

                invoke-virtual {v1, v2}, meth@0005 is 35c (op 0x6e). The first unit packs A = 2 (argument count)
                and G = 0 above the opcode: 0x206e. The second unit is the method index 0x0005. The third unit is
                F|E|D|C = 0|0|2|1, i.e. 0x0021. Bytes: 6e 20 05 00 21 00.
            """, title="Encoding by hand"),
            tip("""
                Formats with 4-bit register fields (12x, 11n, 22c, 22t, 35c and others) can only name v0-v15.
                The assembler refuses move v1, v20 with "Must be between v0 and v15", so you need move/from16,
                and invoke-* calls whose arguments sit in high or many registers need the /range form. This is why
                compilers try hard to keep hot values in low registers.
            """, title="Why 4-bit fields matter"),
            text("""
                The data tables used by packed-switch, sparse-switch and fill-array-data are pseudo-instructions
                identified by the first unit 0x0100, 0x0200 or 0x0300. They must start at an even code-unit
                offset (4-byte aligned), so dex writers insert a nop before a payload when needed. Their sizes in
                code units are:
                - packed-switch-payload: size * 2 + 4
                - sparse-switch-payload: size * 4 + 2
                - fill-array-data-payload: (size * element_width + 1) / 2 + 4

                Payloads are never executed; they are normally placed after the method's last real instruction.
            """, title="Payloads and alignment"),
            warning("""
                Branch offsets (+AA, +AAAA, +AAAAAAAA) are signed and relative to the address of the branching
                instruction itself, measured in code units. Mixing up bytes and code units, or measuring from the
                next instruction, is the most common hand-encoding mistake.
            """),
        ],
        exercises=[
            exercise(
                "instruction-encoding-ex1",
                "Hand-encode const/4 v3, -1. Give the 16-bit code unit and the two bytes as they appear in the file.",
                solution="""
                    Format 11n: B|A|op. -1 as a signed 4-bit value is 0xF, A = 3, op = 0x12. The code unit is
                    0xF312 and the little-endian bytes are 12 f3.
                """,
                hint="Literals in 11n are 4-bit two's complement, so the range is -8..7.",
            ),
            exercise(
                "instruction-encoding-ex2",
                "Decode the bytes 90 02 03 04 into a smali instruction.",
                solution="""
                    Unit one is 0x0290: opcode 0x90 (add-int, format 23x) with AA = 2. Unit two is 0x0403: BB is
                    the low byte (3) and CC the high byte (4). The instruction is add-int v2, v3, v4.
                """,
                hint="Remember that each unit is little-endian and 23x stores CC|BB, high byte first in the layout.",
            ),
            exercise(
                "instruction-encoding-ex3",
                """
                    This method does not assemble because move uses format 12x. Fix it without changing which
                    registers are read and written.
                """,
                starter="""
                    .method public static copy()V
                        .registers 21
                        move v1, v20
                        return-void
                    .end method
                """,
                solution="""
                    .method public static copy()V
                        .registers 21
                        move/from16 v1, v20
                        return-void
                    .end method
                """,
                hint="move/from16 (format 22x) takes an 8-bit destination and a 16-bit source register.",
                check_starter=False,
            ),
        ],
        quiz=[
            question(
                "instruction-encoding-q1",
                "What does the format id 22c tell you?",
                [
                    "2 code units, up to 2 registers, a constant pool index",
                    "22 bits wide with a constant",
                    "2 registers, 2 code units, a branch target",
                    "A compressed format used only by odex files",
                ],
                0,
                """
                    First digit = code units, second digit = register count, letter c = constant pool index. 22c is
                    used by iget/iput (field@CCCC), instance-of and new-array (type@CCCC).
                """,
            ),
            question(
                "instruction-encoding-q2",
                "What is the 16-bit code unit for this instruction?",
                ["0x1012", "0x0112", "0x1210", "0x0012"],
                0,
                """
                    const/4 is opcode 0x12 in format 11n (B|A|op). B = 1, A = 0, so the unit is 0x1012, written to
                    the file as the bytes 12 10.
                """,
                code="const/4 v0, 0x1",
            ),
            question(
                "instruction-encoding-q3",
                "How many argument registers can a 35c invoke list hold?",
                ["5", "4", "15", "255"],
                0,
                """
                    35c has a 4-bit count A and five 4-bit register fields C, D, E, F, G. Calls needing more
                    argument words, or registers above v15, use the 3rc /range form.
                """,
            ),
            question(
                "instruction-encoding-q4",
                "Which format holds a full 64-bit literal?",
                ["51l", "31i", "21h", "3rc"],
                0,
                """
                    51l (used by const-wide) is 5 code units: the opcode unit plus four units of literal. 21h holds
                    only the high 16 bits (const-wide/high16) and 31i a 32-bit value.
                """,
            ),
            question(
                "instruction-encoding-q5",
                "Why might a dex writer emit a nop just before a packed-switch-payload?",
                [
                    "Payloads must start at an even code-unit offset (4-byte aligned)",
                    "The verifier requires a nop before every label",
                    "To give the switch somewhere to fall through",
                    "nop marks the end of the method",
                ],
                0,
                """
                    Payload pseudo-instructions must be 4-byte aligned. If the previous instruction ends at an odd
                    code-unit offset, a one-unit nop pads the gap.
                """,
            ),
        ],
        related=["const/4", "const/16", "const-wide", "add-int", "add-int/2addr", "invoke-virtual", "move/from16", "nop"],
    ),
    # ------------------------------------------------------------------------------------------------ 22
    lesson(
        id="method-references",
        level=L,
        order=22,
        title="Method References",
        summary="""
            Understand what a method reference really is in a dex file: a method_id with a class, a name and a
            prototype with its shorty. Then go beyond classic calls with method handles, invoke-polymorphic and
            invoke-custom call sites introduced in Android 8.0.
        """,
        minutes=14,
        sections=[
            text("""
                In smali a method reference looks like Ljava/util/List;->get(I)Ljava/lang/Object;. In the dex file
                it is a method_id_item made of three indices:
                - class_idx: the type the reference names (Ljava/util/List;),
                - name_idx: the method name ("get"),
                - proto_idx: a proto_id_item describing parameters and return type.

                A proto_id_item stores the return type, a type_list of parameters, and a shorty: a compact string
                with the return type first and one character per parameter, where every reference type (objects
                and arrays alike) collapses to L. (Ljava/lang/String;I[J)Z has the shorty ZLIL.

                The class in a reference is the type the compiler saw at the call site, not necessarily the class
                that declares the code. Calling hashCode() on an ArrayList variable references
                Ljava/util/ArrayList;->hashCode()I, and the runtime resolves it by searching ArrayList and then its
                superclasses.
            """, title="Anatomy of a method reference"),
            code("""
                .method public static describe(Ljava/util/List;)Ljava/lang/String;
                    .registers 3

                    # interface reference: class_idx -> Ljava/util/List;, shorty "I"
                    invoke-interface {p0}, Ljava/util/List;->size()I
                    move-result v0

                    # static reference: shorty "LI"
                    invoke-static {v0}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;
                    move-result-object v1
                    return-object v1
                .end method
            """, title="Classic references"),
            text("""
                java.lang.invoke.MethodHandle (API 26) has two special methods, invoke and invokeExact. They are
                signature polymorphic: their declaration is a native varargs method taking Object[] and returning
                Object, but each call site may pass any argument types and expect any return type.

                Dalvik models this with invoke-polymorphic (format 45cc) and invoke-polymorphic/range (4rcc). Each
                carries two references: meth@BBBB, the declared polymorphic method, and proto@HHHH, the actual
                prototype of this call. The first register is the receiver, the MethodHandle itself. Both
                instructions require dex version 038 (Android 8.0, API 26).
            """, title="Method handles and invoke-polymorphic"),
            code("""
                .method public static apply(Ljava/lang/invoke/MethodHandle;II)I
                    .registers 3

                    # p0 = the handle (receiver), p1 and p2 = the int arguments
                    # method: the declared signature-polymorphic method
                    # proto (II)I: the types used at this call site
                    invoke-polymorphic {p0, p1, p2}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (II)I
                    move-result v0
                    return v0
                .end method
            """, title="invoke-polymorphic"),
            java("""
                import java.lang.invoke.MethodHandle;

                public final class Handles {
                    public static int apply(MethodHandle handle, int a, int b) throws Throwable {
                        // Compiles to invoke-polymorphic with the call-site prototype (II)I
                        return (int) handle.invokeExact(a, b);
                    }
                }
            """, title="Equivalent Java"),
            text("""
                invoke-custom (format 35c) and invoke-custom/range (3rc) reference a call site rather than a
                method. A call_site_id points to a call_site_item: an encoded array whose first three values are a
                method handle for the bootstrap method, a method name and a method type, optionally followed by
                extra constant arguments.

                The first time the instruction executes, the runtime calls the bootstrap method, which must return
                a java.lang.invoke.CallSite. The call site is then bound, and this and every later execution
                invokes the CallSite's target handle exactly, with the instruction's registers as arguments.
                Exceptions thrown during bootstrapping are wrapped in BootstrapMethodError. Like
                invoke-polymorphic, it needs dex 038.
            """, title="invoke-custom and call sites"),
            code("""
                .method public static combine(II)I
                    .registers 2

                    # call_site_0: name "combine", type (II)I, one extra bootstrap argument (0x1)
                    invoke-custom {p0, p1}, call_site_0("combine", (II)I, 0x1)@Lcom/acme/demo/Linker;->bootstrap(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)Ljava/lang/invoke/CallSite;
                    move-result v0
                    return v0
                .end method
            """, title="invoke-custom in smali"),
            tip("""
                You rarely see invoke-custom in ordinary app code: D8 and R8 normally desugar Java lambdas and string
                concatenation into plain classes and StringBuilder calls. It shows up in code built specifically for
                it, in some libraries, and in hand-written tests of the runtime.
            """),
        ],
        exercises=[
            exercise(
                "method-references-ex1",
                "Write the shorty for a method with the descriptor (J[Ljava/lang/String;D)V.",
                solution="""
                    VJLD: return type V first, then J for the long, L for the String array (all reference types,
                    including arrays, become L) and D for the double.
                """,
                hint="Return type first; arrays are references.",
            ),
            exercise(
                "method-references-ex2",
                """
                    Write a static method length(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)I that calls
                    invokeExact on the handle with the String argument and returns the int result.
                """,
                solution="""
                    .method public static length(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)I
                        .registers 2
                        invoke-polymorphic {p0, p1}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (Ljava/lang/String;)I
                        move-result v0
                        return v0
                    .end method
                """,
                hint="The proto describes the call site: one String in, int out. The receiver is not part of it.",
            ),
            exercise(
                "method-references-ex3",
                "Describe the method_id_item and proto_id_item behind Ljava/util/List;->size()I.",
                solution="""
                    method_id_item: class_idx -> type_ids entry for Ljava/util/List;, name_idx -> string "size",
                    proto_idx -> a proto_id_item whose shorty is "I", whose return_type_idx is the type I and whose
                    parameters_off is 0 because there are no parameters.
                """,
                hint="A prototype with no parameters has no type_list.",
            ),
        ],
        quiz=[
            question(
                "method-references-q1",
                "What is the shorty for (Ljava/lang/String;I[J)Z?",
                ["ZLIL", "ZLIJ", "LIJZ", "Z[LI[J"],
                0,
                """
                    Return type first (Z), then one character per parameter. String becomes L, int is I, and the
                    long array [J is a reference type, so it also becomes L.
                """,
            ),
            question(
                "method-references-q2",
                "In this instruction, what does (II)I describe?",
                [
                    "The prototype of this particular call site",
                    "The declared signature of invokeExact",
                    "The bootstrap method's arguments",
                    "The types stored in v0-v2 before the call",
                ],
                0,
                """
                    proto@HHHH gives the argument and return types actually used at the call. The method reference
                    keeps the declared polymorphic signature ([Ljava/lang/Object;)Ljava/lang/Object;.
                """,
                code="invoke-polymorphic {v0, v1, v2}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (II)I",
            ),
            question(
                "method-references-q3",
                "What happens the first time an invoke-custom instruction executes?",
                [
                    "The bootstrap method runs and returns a CallSite that gets bound to the call site",
                    "The verifier rewrites it into invoke-static",
                    "The runtime looks up the method by name in the current class",
                    "It throws unless the call site was linked at install time",
                ],
                0,
                """
                    Call site resolution calls the bootstrap linker method with the values from call_site_item.
                    The returned CallSite's target handle is then invoked with the instruction's arguments.
                """,
            ),
            question(
                "method-references-q4",
                "Which Android version first supports invoke-polymorphic and invoke-custom?",
                ["Android 8.0 (API 26, dex 038)", "Android 5.0 (API 21, dex 035)", "Android 7.0 (API 24, dex 037)", "Android 9 (API 28, dex 039)"],
                0,
                """
                    Both arrived with dex format 038 in Android 8.0. Android 9 and dex 039 added const-method-handle
                    and const-method-type.
                """,
            ),
            question(
                "method-references-q5",
                "An exception thrown inside a bootstrap method surfaces as which error?",
                ["BootstrapMethodError", "IncompatibleClassChangeError", "VerifyError", "NoSuchMethodError"],
                0,
                """
                    The dalvik-bytecode reference states that exceptions from the bootstrap linker method are wrapped
                    in java.lang.BootstrapMethodError, which is also thrown if it returns no CallSite, a null target
                    or a target of the wrong type.
                """,
            ),
        ],
        related=["invoke-virtual", "invoke-interface", "invoke-static", "invoke-polymorphic", "invoke-polymorphic/range", "invoke-custom", "invoke-custom/range"],
    ),
    # ------------------------------------------------------------------------------------------------ 23
    lesson(
        id="control-flow",
        level=L,
        order=23,
        title="Control Flow",
        summary="""
            Split methods into basic blocks, follow every kind of edge, and master the branch instructions:
            the three goto widths, packed and sparse switches with their payload tables, and the shape the
            compilers produce for a switch on strings.
        """,
        minutes=16,
        sections=[
            text("""
                A basic block is a straight run of instructions with one entry and one exit. A new block starts
                (a "leader") at:
                - the first instruction of the method,
                - every branch or switch target and every exception handler,
                - the instruction after a goto, if-*, switch, return-* or throw.

                Edges connect blocks: fall-through to the next block, the taken side of an if-*, one edge per
                switch case plus the fall-through default, and exceptional edges from any instruction that can
                throw inside a try range to its handlers. Drawing this graph is the fastest way to understand an
                unfamiliar method.
            """, title="Basic blocks and edges"),
            code("""
                .method public static sign(I)I
                    .registers 2

                    # Block A: entry
                    if-lez p0, :not_positive

                    # Block B: fall-through of the if
                    const/4 v0, 0x1
                    goto :done

                    # Block C: branch target
                    :not_positive
                    if-eqz p0, :zero

                    # Block D
                    const/4 v0, -0x1
                    goto :done

                    # Block E
                    :zero
                    const/4 v0, 0x0

                    # Block F: join point reached from B, D and E
                    :done
                    return v0
                .end method
            """, title="Six blocks in one method"),
            text("""
                There are three unconditional jumps, differing only in offset width (offsets are signed, in code
                units, relative to the goto itself):
                - goto (10t): 8-bit offset, -128..127
                - goto/16 (20t): 16-bit offset, -32768..32767
                - goto/32 (30t): 32-bit offset

                goto and goto/16 may not have an offset of 0. goto/32 may, so it is the one form that can jump to
                itself; a spin loop can also be built with a nop as the target. Conditional branches (if-test and
                if-testz) always use a 16-bit offset, so they reach only about 32K code units in either direction.
                In smali you write labels and the assembler computes the offsets.
            """, title="goto, goto/16 and goto/32"),
            code("""
                .method public static price(I)I
                    .registers 2

                    packed-switch p0, :pswitch_data
                    const/4 v0, 0x0
                    return v0

                    :pswitch_small
                    const/16 v0, 0xa
                    return v0

                    :pswitch_medium
                    const/16 v0, 0x14
                    return v0

                    :pswitch_large
                    const/16 v0, 0x1e
                    return v0

                    # cases 1, 2, 3 in order
                    :pswitch_data
                    .packed-switch 0x1
                        :pswitch_small
                        :pswitch_medium
                        :pswitch_large
                    .end packed-switch
                .end method
            """, title="packed-switch with its payload"),
            java("""
                static int price(int size) {
                    switch (size) {
                        case 1: return 10;
                        case 2: return 20;
                        case 3: return 30;
                        default: return 0;
                    }
                }
            """, title="Equivalent Java"),
            text("""
                packed-switch suits dense case values: the payload stores first_key and one target per consecutive
                key (size * 2 + 4 code units). sparse-switch suits scattered values: the payload stores sorted keys
                and a matching target for each (size * 4 + 2 code units). In both, targets are relative to the
                address of the switch instruction, not the payload. When no case matches, execution falls through
                to the instruction right after the switch, which is where the default branch lives.

                A switch on a String typically compiles in two steps: call hashCode(), sparse-switch on the hash,
                then confirm each candidate with equals() because different strings can share a hash. javac then
                switches a second time on a small index; D8 or R8 may simplify that shape, so expect variations.
            """, title="Choosing between packed and sparse"),
            code("""
                .method public static colorCode(Ljava/lang/String;)I
                    .registers 3

                    invoke-virtual {p0}, Ljava/lang/String;->hashCode()I
                    move-result v0
                    sparse-switch v0, :sswitch_data

                    :no_match
                    const/4 v0, -0x1
                    return v0

                    :sswitch_red
                    const-string v1, "red"
                    invoke-virtual {p0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :no_match
                    const/4 v0, 0x1
                    return v0

                    :sswitch_blue
                    const-string v1, "blue"
                    invoke-virtual {p0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :no_match
                    const/4 v0, 0x2
                    return v0

                    # keys are "red".hashCode() and "blue".hashCode(), sorted low to high
                    :sswitch_data
                    .sparse-switch
                        0x1b891 -> :sswitch_red
                        0x2e305a -> :sswitch_blue
                    .end sparse-switch
                .end method
            """, title="Switch on strings: hashCode, then equals"),
            tip("""
                When reading a big method, find the switch payloads at the bottom first: they list every case
                label, so they tell you where each case starts before you read a single case body.
            """),
        ],
        exercises=[
            exercise(
                "control-flow-ex1",
                "Split this method into basic blocks and list the edges between them.",
                starter="""
                    .method public static abs(I)I
                        .registers 1
                        if-gez p0, :positive
                        neg-int p0, p0
                        :positive
                        return p0
                    .end method
                """,
                solution="""
                    Block 1: if-gez p0, :positive. Block 2: neg-int p0, p0 (leader because it follows a
                    conditional branch). Block 3: :positive return p0 (leader because it is a branch target).
                    Edges: 1 -> 3 (branch taken), 1 -> 2 (fall-through), 2 -> 3 (fall-through). Block 3 has no
                    successors because it returns.
                """,
                hint="Leaders: the first instruction, branch targets, and instructions after a branch.",
            ),
            exercise(
                "control-flow-ex2",
                """
                    Write a static method weekdayHours(I)I that returns 8 for 0, 8 for 1 and 4 for 2, and 0 for any
                    other value. Use a packed-switch.
                """,
                solution="""
                    .method public static weekdayHours(I)I
                        .registers 2
                        packed-switch p0, :pswitch_data
                        const/4 v0, 0x0
                        return v0
                        :pswitch_full
                        const/16 v0, 0x8
                        return v0
                        :pswitch_half
                        const/4 v0, 0x4
                        return v0
                        :pswitch_data
                        .packed-switch 0x0
                            :pswitch_full
                            :pswitch_full
                            :pswitch_half
                        .end packed-switch
                    .end method
                """,
                hint="Two cases may share the same target label. 8 does not fit in const/4 (range -8..7).",
            ),
            exercise(
                "control-flow-ex3",
                "How many code units do the payloads of a 5-case packed-switch and a 5-case sparse-switch occupy?",
                solution="""
                    packed-switch-payload: 5 * 2 + 4 = 14 code units. sparse-switch-payload: 5 * 4 + 2 = 22 code
                    units. The sparse table is larger because it stores a key for every target.
                """,
                hint="Each key and each target is a 32-bit int, which is two code units.",
            ),
        ],
        quiz=[
            question(
                "control-flow-q1",
                "What offset range does goto/16 support?",
                ["-32768..32767 code units", "-128..127 code units", "-32768..32767 bytes", "Any 32-bit offset"],
                0,
                """
                    goto/16 (format 20t) carries a signed 16-bit offset counted in code units. goto has 8 bits
                    and goto/32 has 32 bits.
                """,
            ),
            question(
                "control-flow-q2",
                "Which jump may legally use an offset of 0 (jump to itself)?",
                ["goto/32", "goto", "goto/16", "if-eqz"],
                0,
                """
                    The bytecode reference forbids a zero offset for goto, goto/16 and the if-* branches. goto/32 is
                    allowed to target itself.
                """,
            ),
            question(
                "control-flow-q3",
                "Switch payload targets are relative to which address?",
                [
                    "The packed-switch or sparse-switch instruction",
                    "The start of the payload table",
                    "The start of the method",
                    "The instruction after the switch",
                ],
                0,
                """
                    Both payload formats document their targets as relative to the switch opcode, not to the table
                    that contains them.
                """,
            ),
            question(
                "control-flow-q4",
                "What happens here when v0 is 7?",
                [
                    "Execution falls through to the instruction after sparse-switch",
                    "An IndexOutOfBoundsException is thrown",
                    "The verifier rejects the method because 7 has no case",
                    "It jumps to :case_ten, the nearest key",
                ],
                0,
                """
                    7 matches no key, so the switch simply continues with the next instruction: v1 becomes 0 and is
                    returned. Compilers put the default case right after the switch.
                """,
                code="""
                    sparse-switch v0, :sswitch_data
                    const/4 v1, 0x0
                    return v1
                    :case_ten
                    const/4 v1, 0x1
                    return v1
                    :sswitch_data
                    .sparse-switch
                        0xa -> :case_ten
                    .end sparse-switch
                """,
            ),
            question(
                "control-flow-q5",
                "Which instruction does NOT end a basic block?",
                ["add-int/lit8", "goto", "return-void", "throw"],
                0,
                """
                    Arithmetic falls straight through to the next instruction. Jumps, returns and throws transfer
                    control, so the following instruction starts a new block.
                """,
            ),
        ],
        related=["goto", "goto/16", "goto/32", "if-eqz", "if-lez", "packed-switch", "sparse-switch", "packed-switch-payload", "sparse-switch-payload"],
    ),
    # ------------------------------------------------------------------------------------------------ 24
    lesson(
        id="annotations",
        level=L,
        order=24,
        title="Annotations",
        summary="""
            Read and write .annotation blocks: the build, runtime and system visibilities, the dalvik.annotation
            metadata that carries generics, throws clauses and inner-class information, parameter annotations,
            and the smali syntax for arrays, enums and nested annotations.
        """,
        minutes=14,
        sections=[
            text("""
                Every annotation in a dex file has a visibility byte:
                - build (0x00): meant only for build-time tools. It matches Java's RetentionPolicy.CLASS, is not
                  visible through reflection, and dexers commonly drop it.
                - runtime (0x01): visible to reflection, e.g. getAnnotation(). Matches RetentionPolicy.RUNTIME.
                - system (0x02): visible to the runtime but not to ordinary user annotation queries. Used for the
                  dalvik.annotation.* metadata that replaces JVM class-file attributes.

                Annotations with RetentionPolicy.SOURCE never reach the dex file at all.
            """, title="Three visibilities"),
            code("""
                .class public Lcom/acme/demo/Api;
                .super Ljava/lang/Object;

                .annotation runtime Lcom/acme/demo/Version;
                    value = 0x2
                .end annotation

                .method public static ping()V
                    .registers 0
                    .annotation runtime Ljava/lang/Deprecated;
                    .end annotation
                    return-void
                .end method
            """, title="Class and method annotations"),
            text("""
                javac class-file attributes become system annotations in dex:
                - Signature: the generic signature of a class, field or method, stored as an array of strings
                  that must be concatenated. It is not verified by the runtime and exists for reflection.
                - Throws: the Class[] of a method's declared exceptions.
                - EnclosingClass: the class lexically containing a member class (or an anonymous class outside
                  any method).
                - EnclosingMethod: the method containing a local or anonymous class. A class never has both
                  EnclosingClass and EnclosingMethod, and either one requires InnerClass.
                - InnerClass: the original simple name (null for anonymous classes) and declared access flags.
                - MemberClasses: on the outer class, the list of its named member classes.
                - MethodParameters: names and flags (0x10 final, 0x1000 synthetic, 0x8000 mandated) of a
                  method's formal parameters. It was added after Android 7.1 and is ignored on older releases.
                - AnnotationDefault: on an annotation interface, the default values of its elements.
            """, title="System annotations"),
            code("""
                .class public Lcom/acme/demo/Outer$Builder;
                .super Ljava/lang/Object;

                .annotation system Ldalvik/annotation/EnclosingClass;
                    value = Lcom/acme/demo/Outer;
                .end annotation

                .annotation system Ldalvik/annotation/InnerClass;
                    accessFlags = 0x9
                    name = "Builder"
                .end annotation

                .method public names()Ljava/util/List;
                    .registers 2
                    .annotation system Ldalvik/annotation/Signature;
                        value = {
                            "()",
                            "Ljava/util/List<",
                            "Ljava/lang/String;",
                            ">;"
                        }
                    .end annotation

                    .annotation system Ldalvik/annotation/Throws;
                        value = {
                            Ljava/io/IOException;
                        }
                    .end annotation

                    new-instance v0, Ljava/util/ArrayList;
                    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                    return-object v0
                .end method
            """, title="Metadata of a static nested class"),
            java("""
                package com.acme.demo;

                public class Outer {
                    public static class Builder {          // accessFlags 0x9 = public | static
                        public java.util.List<String> names() throws java.io.IOException {
                            return new java.util.ArrayList<>();
                        }
                    }
                }
            """, title="Equivalent Java"),
            text("""
                Annotation element values use a small literal syntax:
                - numbers, booleans, chars and strings as usual (0x2, true, 'a', "text"),
                - types (Ljava/lang/String;), field and method references,
                - arrays in braces: value = { "a", "b" },
                - enum constants: .enum Lpkg/Kind;->FAST:Lpkg/Kind;
                - nested annotations: .subannotation Lpkg/Tag; ... .end subannotation

                Parameter annotations live inside a .param block attached to the parameter register, e.g.
                .param p1, "name" followed by its .annotation entries and .end param.
            """, title="Writing annotation values"),
            code("""
                .class public interface abstract annotation Lcom/acme/demo/Retry;
                .super Ljava/lang/Object;
                .implements Ljava/lang/annotation/Annotation;

                .annotation runtime Ljava/lang/annotation/Retention;
                    value = .enum Ljava/lang/annotation/RetentionPolicy;->RUNTIME:Ljava/lang/annotation/RetentionPolicy;
                .end annotation

                .annotation system Ldalvik/annotation/AnnotationDefault;
                    value = .subannotation Lcom/acme/demo/Retry;
                        times = 0x3
                    .end subannotation
                .end annotation

                .method public abstract times()I
                .end method
            """, title="An annotation type with a default"),
            warning("""
                Shrinkers remove metadata they consider unused. If an app relies on generic types or annotations
                at runtime (serialization libraries are typical), R8 must be told to keep them, for example with
                -keepattributes Signature,RuntimeVisibleAnnotations; otherwise those annotations are simply absent
                from the smali you read.
            """, title="Missing annotations after R8"),
        ],
        exercises=[
            exercise(
                "annotations-ex1",
                """
                    Write a method close()V (instance, empty body) whose Java declaration is
                    void close() throws IOException, InterruptedException.
                """,
                solution="""
                    .method public close()V
                        .registers 1
                        .annotation system Ldalvik/annotation/Throws;
                            value = {
                                Ljava/io/IOException;,
                                Ljava/lang/InterruptedException;
                            }
                        .end annotation
                        return-void
                    .end method
                """,
                hint="Throws holds a Class[] array written with braces.",
            ),
            exercise(
                "annotations-ex2",
                "Annotate a String parameter of setName(Ljava/lang/String;)V with the build-time annotation Landroidx/annotation/NonNull;.",
                starter="""
                    .method public setName(Ljava/lang/String;)V
                        .registers 2
                        return-void
                    .end method
                """,
                solution="""
                    .method public setName(Ljava/lang/String;)V
                        .registers 2
                        .param p1, "name"    # Ljava/lang/String;
                            .annotation build Landroidx/annotation/NonNull;
                            .end annotation
                        .end param
                        return-void
                    .end method
                """,
                hint="In an instance method p0 is this, so the String is p1.",
            ),
            exercise(
                "annotations-ex3",
                """
                    An anonymous Runnable is created inside Lcom/acme/demo/Task;->run()V. Which system annotations
                    will its class carry, and what will they contain?
                """,
                solution="""
                    EnclosingMethod with value = Lcom/acme/demo/Task;->run()V (not EnclosingClass, because it is
                    defined inside a method), and InnerClass with name = null because the class is anonymous, plus
                    its accessFlags. The outer class does not list it in MemberClasses, which only contains named
                    member classes.
                """,
                hint="Anonymous classes have no simple name, and EnclosingClass/EnclosingMethod are mutually exclusive.",
            ),
        ],
        quiz=[
            question(
                "annotations-q1",
                "Which visibility does dalvik.annotation.Signature use?",
                ["system", "runtime", "build", "It has no visibility"],
                0,
                """
                    All dalvik.annotation.* metadata uses VISIBILITY_SYSTEM: readable by the runtime to implement
                    reflection, not returned as an ordinary annotation.
                """,
            ),
            question(
                "annotations-q2",
                "Which two system annotations can never appear on the same class?",
                [
                    "EnclosingClass and EnclosingMethod",
                    "InnerClass and EnclosingClass",
                    "Signature and Throws",
                    "MemberClasses and Signature",
                ],
                0,
                """
                    A class is scoped either by another class or by a method. Whichever applies, it must also carry
                    InnerClass.
                """,
            ),
            question(
                "annotations-q3",
                "Can app code read this annotation with Class.getAnnotation() at runtime?",
                [
                    "Yes, runtime visibility is exposed through reflection",
                    "No, only build tools can see it",
                    "Only on Android 8.0 and later",
                    "Only if the class is also annotated with InnerClass",
                ],
                0,
                """
                    runtime corresponds to RetentionPolicy.RUNTIME and is the visibility reflection reports. build
                    is for tools only, and system is reserved for runtime metadata.
                """,
                code="""
                    .annotation runtime Lcom/acme/demo/Keep;
                    .end annotation
                """,
            ),
            question(
                "annotations-q4",
                "What does name = null in a dalvik.annotation.InnerClass annotation mean?",
                [
                    "The class is anonymous",
                    "The class was obfuscated",
                    "The name was stripped by R8",
                    "The class is a lambda",
                ],
                0,
                """
                    InnerClass.name is the originally declared simple name; the format defines it as null for
                    anonymous classes.
                """,
            ),
            question(
                "annotations-q5",
                "How is a generic signature stored in a Signature annotation?",
                [
                    "As an array of strings that are concatenated",
                    "As a single type descriptor",
                    "As a type_list in the proto",
                    "As debug info attached to the method",
                ],
                0,
                """
                    Splitting the signature into an array lets the pieces be shared with existing strings in the
                    file. Readers concatenate the parts to get the full signature.
                """,
            ),
            question(
                "annotations-q6",
                "What happens to a Java annotation declared with RetentionPolicy.SOURCE?",
                [
                    "It is discarded by the compiler and never appears in the dex",
                    "It becomes a build-visibility annotation",
                    "It becomes a system annotation",
                    "It is stored in the debug info",
                ],
                0,
                """
                    SOURCE annotations are dropped by javac or kotlinc and never reach the class files, so the dexer
                    has nothing to convert.
                """,
            ),
        ],
        related=["new-instance", "invoke-direct", "return-object", "return-void"],
    ),
]

FLASHCARDS = [
    card("fc-a1-01", "DEX", "What are the 8 magic bytes of a version 039 dex file?",
         'The bytes 64 65 78 0a 30 33 39 00, i.e. "dex\\n039\\0". The three digits are the format version.'),
    card("fc-a1-02", "DEX", "How large is the dex header?",
         "0x70 (112) bytes for versions up to 040. The experimental 041 container format uses 0x78 bytes."),
    card("fc-a1-03", "DEX", "checksum vs signature in the dex header?",
         "checksum: Adler-32 of everything after the checksum field. signature: 20-byte SHA-1 of everything "
         "after the signature field."),
    card("fc-a1-04", "DEX", "What are the fields of a method_id_item?",
         "class_idx (ushort, type_ids), proto_idx (ushort, proto_ids) and name_idx (uint, string_ids): 8 bytes."),
    card("fc-a1-05", "DEX", "What is the map_list?",
         "A table of contents in the data section, at header.map_off. Each 12-byte entry gives an item type, "
         "a count and an offset, ordered by offset."),
    card("fc-a1-06", "Formats", "What do the characters in a format id like 22c mean?",
         "First digit: code units. Second digit: max registers (r = range). Letter: extra data (c = pool index, "
         "t = branch target, s/i/l/n/b = literals, x = none)."),
    card("fc-a1-07", "Formats", "How is format 35c laid out?",
         "A|G|op BBBB F|E|D|C: A = argument count, B = reference index, C..G = up to five 4-bit argument registers.",
         code="invoke-virtual {v1, v2}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z"),
    card("fc-a1-08", "Formats", "Encode const/4 v0, 1.",
         "Format 11n (B|A|op): B = 1, A = 0, op = 0x12, so the unit is 0x1012, stored as the bytes 12 10."),
    card("fc-a1-09", "Opcodes", "Which goto can have a zero offset?",
         "goto/32. goto and goto/16 must not use offset 0; a spin loop needs goto/32 or a nop as the target."),
    card("fc-a1-10", "Formats", "How many code units do switch payloads use?",
         "packed-switch-payload: size * 2 + 4. sparse-switch-payload: size * 4 + 2. Both must be 4-byte aligned."),
    card("fc-a1-11", "Opcodes", "What does invoke-polymorphic carry besides the method?",
         "A proto@HHHH reference with the call-site prototype. It calls signature-polymorphic methods such as "
         "MethodHandle.invokeExact (dex 038, API 26)."),
    card("fc-a1-12", "Runtime", "How is an invoke-custom call site linked?",
         "On first execution the bootstrap method from call_site_item runs and returns a CallSite; its target "
         "handle is then invoked. Bootstrap failures throw BootstrapMethodError."),
    card("fc-a1-13", "Directives", "What are the three .annotation visibilities?",
         "build (tools only, like RetentionPolicy.CLASS), runtime (reflection, like RUNTIME) and system "
         "(dalvik.annotation.* metadata for the runtime)."),
    card("fc-a1-14", "Descriptors", "What is a shorty descriptor?",
         "The compact prototype string in proto_id_item: return type first, then one character per parameter, "
         "with every reference type written as L. (Ljava/lang/String;I[J)Z becomes ZLIL."),
]
