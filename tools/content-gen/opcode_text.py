"""Per-family text templates and hand-written entries for the Dalvik opcode database.

All explanatory text in this file is original. Facts (opcode values, formats, operand widths, dex versions) come
from the parsed spec passed in through Context; the templates only add explanations around them.
"""
from __future__ import annotations

import textwrap
from dataclasses import dataclass, field

URL_BC = "https://source.android.com/docs/core/runtime/dalvik-bytecode"
URL_IF = "https://source.android.com/docs/core/runtime/instruction-formats"
URL_DEX = "https://source.android.com/docs/core/runtime/dex-format"
URL_MH = "https://developer.android.com/reference/java/lang/invoke/MethodHandle"
URL_VH = "https://developer.android.com/reference/java/lang/invoke/VarHandle"


def ref(title, url):
    return {"title": title, "url": url}


REF_BC = ref("Dalvik bytecode: summary of bytecode set", URL_BC + "#instructions")
REF_MATH = ref("Dalvik bytecode: mathematical operation details", URL_BC + "#math")
REF_IF = ref("Dalvik instruction formats", URL_IF + "#formats")
REF_IF_SYNTAX = ref("Dalvik instruction formats: syntax notation", URL_IF + "#syntax")
REF_DEX_MAGIC = ref("DEX format: DEX_FILE_MAGIC and dex versions", URL_DEX + "#dex-file-magic")
REF_DEX_FIELD = ref("DEX format: field_id_item", URL_DEX + "#field-id-item")
REF_DEX_METHOD = ref("DEX format: method_id_item", URL_DEX + "#method-id-item")
REF_DEX_STRING = ref("DEX format: string_id_item", URL_DEX + "#string-item")
REF_DEX_TYPE = ref("DEX format: type_id_item", URL_DEX + "#type-id-item")
REF_DEX_PROTO = ref("DEX format: proto_id_item", URL_DEX + "#proto-id-item")
REF_DEX_CALLSITE = ref("DEX format: call_site_item", URL_DEX + "#call-site-item")
REF_DEX_MH = ref("DEX format: method_handle_item", URL_DEX + "#method-handle-item")
REF_DEX_CATCH = ref("DEX format: encoded_catch_handler", URL_DEX + "#encoded-catch-handler")
REF_DEX_CODE = ref("DEX format: code_item", URL_DEX + "#code-item")
REF_DEX_ACCESS = ref("DEX format: access_flags definitions", URL_DEX + "#access-flags")
REF_PACKED = ref("Dalvik bytecode: packed-switch-payload format", URL_BC + "#packed-switch")
REF_SPARSE = ref("Dalvik bytecode: sparse-switch-payload format", URL_BC + "#sparse-switch")
REF_FILL = ref("Dalvik bytecode: fill-array-data-payload format", URL_BC + "#fill-array")
REF_MH_API = ref("java.lang.invoke.MethodHandle (Android API reference)", URL_MH)
REF_VH_API = ref("java.lang.invoke.VarHandle (Android API reference)", URL_VH)

R = {4: "v0–v15", 8: "v0–v255", 16: "v0–v65535"}

P_REG_NOTE = ("Remember that pN registers are aliases for the last registers of the frame: in a method with many "
              "locals, p0 may be v20 or higher and then cannot be named by a 4-bit register field.")


@dataclass
class Context:
    spec_by_name: dict
    formats: dict
    payload_tables: dict
    dex_versions: dict
    release_to_api: dict
    all_names: set = field(default_factory=set)

    def api_for_dex(self, dex: str) -> int:
        return self.release_to_api[self.dex_versions[dex]]


def an(word: str) -> str:
    """Indefinite article + word ("an int", "a float")."""
    return ("an " if word.lstrip("([")[:1].lower() in "aeio" else "a ") + word


def ex(s: str) -> str:
    return textwrap.dedent(s).strip("\n")


def opnd(token, fieldname, bits, text):
    return {"name": token, "description": f"{fieldname} ({bits} bits): {text}"}


def derived(token, text):
    return {"name": token, "description": f"Not encoded: {text}"}


def enc(ctx, fmt):
    layout = ctx.formats[fmt]
    units = int(fmt[0])
    return (f"Format {fmt} ({layout}): {units} 16-bit code unit{'s' if units != 1 else ''} "
            f"({2 * units} bytes).")


def make(ctx, name, category, *, syntax, operands, summary, description, technical, example, explanation,
         java, kotlin, registers, mistakes, related, tags, compat_extra="", refs_extra=()):
    s = ctx.spec_by_name[name]
    dex = s.dex_version
    if dex == "035":
        min_api = 1
        compat = ("Original Dalvik instruction: valid in every dex version (035 and later) and on every Android "
                  "release (API 1+), under both Dalvik and ART.")
    else:
        min_api = ctx.api_for_dex(dex)
        release = ctx.dex_versions[dex]
        compat = (f"Requires dex version {dex} or later; the dex format documentation says support for {dex} was "
                  f"added in Android {release} (API {min_api}). Assemble with smali --api {min_api} or higher; "
                  f"older devices refuse to load such a dex file.")
    if compat_extra:
        compat += " " + compat_extra
    refs = [REF_BC, REF_IF] + list(refs_extra)
    seen = set()
    refs = [r for r in refs if not (r["url"] in seen or seen.add(r["url"]))]
    rel = []
    for r in related:
        if r != name and r not in rel:
            rel.append(r)
    return {
        "name": name,
        "opcode": s.opcode,
        "category": category,
        "format": s.fmt,
        "syntax": syntax,
        "operands": operands,
        "summary": summary,
        "description": description,
        "technical": technical,
        "example": example,
        "exampleExplanation": explanation.strip(),
        "javaEquivalent": java,
        "kotlinEquivalent": kotlin,
        "registerUsage": registers,
        "minApi": min_api,
        "dexVersion": dex,
        "compatibility": compat,
        "commonMistakes": mistakes,
        "related": rel,
        "references": refs,
        "tags": tags,
    }


def existing(ctx, names):
    return [n for n in names if n in ctx.all_names]


# =================================================================================================================
# nop
# =================================================================================================================

def gen_nop(ctx):
    return [make(
        ctx, "nop", "advanced",
        syntax="nop",
        operands=[],
        summary="Do nothing; mainly used as alignment padding before data payloads.",
        description=("nop performs no operation and simply advances to the next instruction. Assemblers and "
                     "compilers mostly emit it as a one-code-unit spacer so that switch and array data payloads "
                     "start on a 4-byte boundary. You will see it in disassembly far more often than you will need "
                     "to write it yourself."),
        technical=(enc(ctx, "10x") + " The whole code unit is 0x0000: the high byte must be zero. A unit whose low "
                   "byte is 0x00 but whose high byte is 0x01, 0x02 or 0x03 is not a nop but the ident of a "
                   "packed-switch, sparse-switch or fill-array-data payload. nop reads and writes no registers, does "
                   "not touch the result register and cannot throw. Because branch offsets of 0 are illegal for goto, "
                   "goto/16 and the if-* instructions, a nop can serve as a distinct target when building a tight "
                   "loop."),
        example=ex("""
            .method public static spacer()V
                .locals 0
                nop
                return-void
            .end method
        """),
        explanation=("The nop has no effect; execution continues with return-void. When smali (dexlib2) assembles "
                     "a method, it inserts or removes a padding nop automatically in front of payload tables that "
                     "would otherwise start at an odd code-unit offset."),
        java=None, kotlin=None,
        registers="None. nop has no operands and does not read or write any register.",
        mistakes=[
            "Deleting a nop that sits right before a :pswitch_data / :array_ label in hand-edited smali and then "
            "worrying about alignment: smali re-pads payloads itself, so the nop is not something you must "
            "maintain by hand.",
            "Assuming every code unit with a 0x00 low byte is a nop: 0x0100, 0x0200 and 0x0300 are payload "
            "idents, and decoders must treat them as data.",
            "Replacing an instruction with nops to 'patch it out' without checking that later code doesn't rely on "
            "the registers it wrote or on a move-result that must immediately follow an invoke.",
        ],
        related=["packed-switch-payload", "sparse-switch-payload", "fill-array-data-payload", "goto"],
        tags=["padding", "alignment", "no-op"],
    )]


# =================================================================================================================
# move, move-wide, move-object (+ /from16, /16)
# =================================================================================================================

MOVE_KINDS = {
    "": dict(what="a 32-bit non-object value (int, float, boolean, byte, char or short)", noun="value",
             java="int copy = value;", kotlin="val copy = value", tags=[]),
    "-wide": dict(what="a 64-bit long or double stored in a register pair", noun="register pair",
                  java="long copy = value;   // or double", kotlin="val copy: Long = value", tags=["wide"]),
    "-object": dict(what="an object reference (or null)", noun="reference",
                    java="Object copy = value;", kotlin="val copy: Any? = value", tags=["object"]),
}
MOVE_SIZES = {"": ("12x", 4, 4), "/from16": ("22x", 8, 16), "/16": ("32x", 16, 16)}


def gen_moves(ctx):
    out = []
    for kind, k in MOVE_KINDS.items():
        for size, (fmt, abits, bbits) in MOVE_SIZES.items():
            name = f"move{kind}{size}"
            spec = ctx.spec_by_name[name]
            a_tok = "v" + "A" * (abits // 4)
            b_tok = "v" + "B" * (bbits // 4)
            wide = kind == "-wide"
            dest = "destination register pair" if wide else "destination register"
            src = "source register pair" if wide else "source register"
            operands = [
                opnd(a_tok, "A" * (abits // 4), abits, f"{dest}, {R[abits]}" + (" (the pair is vN, vN+1)." if wide else ".")),
                opnd(b_tok, "B" * (bbits // 4), bbits, f"{src}, {R[bbits]}."),
            ]
            if size == "":
                reach = "Both registers must be in v0–v15."
                ex_code = {
                    "": "move v0, p0", "-wide": "move-wide v0, p0", "-object": "move-object v0, p0"}[kind]
                sig = {"": "(I)I", "-wide": "(J)J", "-object": "(Ljava/lang/Object;)Ljava/lang/Object;"}[kind]
                ret = {"": "return", "-wide": "return-wide", "-object": "return-object"}[kind]
                locals_ = 2 if wide else 1
                example = ex(f"""
                    .method public static identity{sig}
                        .locals {locals_}
                        {ex_code}
                        {ret} v0
                    .end method
                """)
                explanation = (f"The parameter arrives in p0{'/p1' if wide else ''} and {name} copies it into the "
                               f"local {'pair v0/v1' if wide else 'register v0'}, which is then returned. With "
                               f"{'.locals 2' if wide else '.locals 1'} the parameter register p0 is "
                               f"v{locals_}, still low enough for the 4-bit fields of format 12x.")
            elif size == "/from16":
                reach = "The destination must be in v0–v255; the source may be any register up to v65535."
                example = ex(f"""
                    # v300 lives above v255, so it can only be read by /from16 or /16 moves
                    {name} v0, v300
                """)
                explanation = (f"Most instructions can only address low registers. {name} brings the "
                               f"{k['noun']} from the high register v300 down into v0 so that ordinary "
                               f"instructions can work on it.")
            else:
                reach = "Both registers may be anywhere in v0–v65535."
                example = ex(f"""
                    # both registers are above v255
                    {name} v256, v300
                """)
                explanation = (f"{name} is the only way to copy a {k['noun']} directly between two registers that "
                               f"are both above v255. It costs three code units, so compilers use it only in very "
                               f"large methods.")
            tech = enc(ctx, fmt) + f" Copies {k['what']} from {b_tok} to {a_tok}. {reach}"
            if wide:
                tech += (" Both halves of the source pair are read before anything is written, so overlapping "
                         "pairs such as move-wide v1, v0 (vN to vN+1) or v0, v1 (vN to vN-1) are legal.")
            if kind == "":
                tech += (" The verifier requires the source to hold a category-1 primitive; using it on a "
                         "reference or on half of a long/double pair is rejected.")
            elif kind == "-object":
                tech += " The verifier requires the source to hold a reference (or the null constant)."
            tech += " Cannot throw."
            registers = (f"Writes {a_tok}{'/' + a_tok + '+1' if wide else ''}, reads {b_tok}"
                         f"{'/' + b_tok + '+1' if wide else ''}. {reach}")
            if size == "":
                registers += " " + P_REG_NOTE
            mistakes = []
            if kind == "":
                mistakes += ["Copying an object reference with plain move: references must use move-object, "
                             "otherwise the verifier rejects the method.",
                             "Copying a long or double with move: only one half of the pair is copied and the "
                             "verifier rejects it; use move-wide."]
            elif kind == "-wide":
                mistakes += ["Naming the second half of a pair (for example v1 of v0/v1) as the source or "
                             "destination, which splits a different pair and corrupts both values.",
                             "Forgetting that the destination pair also overwrites vA+1, clobbering whatever was "
                             "stored in the next register."]
            else:
                mistakes += ["Using move-object on an int or float register: the verifier expects a reference and "
                             "rejects the method.",
                             "Expecting move-object to clone the object: it only copies the reference, so both "
                             "registers point to the same instance."]
            if size == "":
                mistakes.append(f"Naming a register above v15 (including a pN that maps above v15): smali refuses "
                                f"the 12x form; use {'move' + kind}/from16 or {'move' + kind}/16 instead.")
            else:
                mistakes.append("Choosing the wide-reach form when both registers are low: it works but wastes code "
                                "units; smali does not shrink it for you.")
            fam = [f"move{kind}{s}" for s in MOVE_SIZES]
            other = [f"move{k2}{size}" for k2 in MOVE_KINDS if k2 != kind]
            related = fam + other + ({"": ["move-result"], "-wide": ["move-result-wide"],
                                      "-object": ["move-result-object"]}[kind])
            tags = ["move", "register"] + k["tags"] + ({"": [], "/from16": ["from16"], "/16": ["16-bit-register"]}[size])
            out.append(make(
                ctx, name, "move",
                syntax=spec.spec_syntax,
                operands=operands,
                summary=f"Copy {k['what'].split(' (')[0].replace('stored in a register pair', 'pair')} from "
                        f"{b_tok} into {a_tok}.",
                description=(f"{name} copies {k['what']} from one register to another; the source is left "
                             f"unchanged. "
                             + {"": "Use it only for primitive 32-bit values: references and wide values have "
                                    "their own move variants. ",
                                "-wide": "A long or double occupies two adjacent registers, and the instruction "
                                         "names only the first (lower) one of each pair. ",
                                "-object": "Only the reference is copied, never the object itself. "}[kind]
                             + {"": "This compact form addresses only the first 16 registers.",
                                "/from16": "The /from16 variant lets you read from any register, which is useful "
                                           "for pulling values out of high registers.",
                                "/16": "The /16 variant can address any register for both source and "
                                       "destination."}[size]),
                technical=tech,
                example=example,
                explanation=explanation,
                java=k["java"], kotlin=k["kotlin"],
                registers=registers,
                mistakes=mistakes,
                related=related,
                tags=tags,
            ))
    return out


# =================================================================================================================
# move-result*, move-exception
# =================================================================================================================

def gen_move_results(ctx):
    out = []
    spec = ctx.spec_by_name
    out.append(make(
        ctx, "move-result", "move",
        syntax=spec["move-result"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives the 32-bit primitive result.")],
        summary="Store the 32-bit primitive result of the preceding invoke in vAA.",
        description=("After a method call returns an int, float, boolean, byte, char or short, move-result copies "
                     "that value into a register. It must be the very next instruction after the invoke; if the "
                     "result is not needed, the move-result is simply omitted."),
        technical=(enc(ctx, "11x") + " Reads the hidden result register filled by the immediately preceding "
                   "invoke-kind (including invoke-polymorphic and invoke-custom) and writes it to vAA. Placing it "
                   "anywhere else (after any other instruction, or after a call returning void, long, double or an "
                   "object) is a verification error. Cannot throw."),
        example=ex("""
            .method public static nameLength(Ljava/lang/String;)I
                .locals 1
                invoke-virtual {p0}, Ljava/lang/String;->length()I
                move-result v0
                return v0
            .end method
        """),
        explanation=("String.length() returns an int, so the result is retrieved with move-result directly after "
                     "the invoke-virtual and then returned."),
        java="int len = name.length();", kotlin="val len = name.length",
        registers="Writes vAA (v0–v255). Reads only the implicit result of the previous invoke.",
        mistakes=[
            "Putting any instruction (even a const or a debug-line-only edit that adds code) between the invoke "
            "and move-result: the result is lost and the verifier rejects the method.",
            "Using move-result for a long/double result (needs move-result-wide) or an object result (needs "
            "move-result-object).",
            "Adding move-result after a void method call, which has no result to move.",
        ],
        related=["move-result-wide", "move-result-object", "invoke-virtual", "invoke-static", "move"],
        tags=["move", "result", "invoke"],
    ))
    out.append(make(
        ctx, "move-result-wide", "move",
        syntax=spec["move-result-wide"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "first register of the destination pair, v0–v255; the pair is vAA, vAA+1.")],
        summary="Store the 64-bit (long or double) result of the preceding invoke in the pair vAA/vAA+1.",
        description=("Methods that return long or double hand back a 64-bit value, and move-result-wide stores it "
                     "in two adjacent registers. Like the other move-result forms it has to come immediately after "
                     "the invoke."),
        technical=(enc(ctx, "11x") + " Writes both vAA and vAA+1 from the double-word result of the immediately "
                   "preceding invoke-kind. Valid only directly after an invoke whose return type is J or D. The "
                   "register vAA+1 must exist in the frame. Cannot throw."),
        example=ex("""
            .method public static now()J
                .locals 2
                invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
                move-result-wide v0
                return-wide v0
            .end method
        """),
        explanation=("currentTimeMillis() returns a long, which lands in the pair v0/v1. That is why the method "
                     "declares .locals 2 and returns with return-wide v0."),
        java="long now = System.currentTimeMillis();", kotlin="val now = System.currentTimeMillis()",
        registers="Writes the pair vAA and vAA+1 (vAA in v0–v255).",
        mistakes=[
            "Reserving only one register for the result: vAA+1 is overwritten too.",
            "Using move-result instead of move-result-wide after a call returning long or double.",
            "Later reading vAA+1 on its own as if it were an independent int.",
        ],
        related=["move-result", "move-result-object", "return-wide", "move-wide", "invoke-static"],
        tags=["move", "result", "wide", "invoke"],
    ))
    out.append(make(
        ctx, "move-result-object", "move",
        syntax=spec["move-result-object"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives the object reference.")],
        summary="Store the object result of the preceding invoke or filled-new-array in vAA.",
        description=("When a call returns an object (including strings and arrays), move-result-object puts the "
                     "reference into a register. It is also how you collect the array created by filled-new-array "
                     "or filled-new-array/range. It has to be the instruction right after the producer."),
        technical=(enc(ctx, "11x") + " Copies the reference result of the immediately preceding invoke-kind, "
                   "filled-new-array or filled-new-array/range into vAA. Anywhere else it is invalid. The "
                   "verifier types vAA with the method's declared return type (or the array type). Cannot throw."),
        example=ex("""
            .method public static describe(Ljava/lang/Object;)Ljava/lang/String;
                .locals 1
                invoke-virtual {p0}, Ljava/lang/Object;->toString()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method
        """),
        explanation="toString() returns a String reference, so move-result-object stores it in v0 for the return.",
        java="String s = obj.toString();", kotlin="val s = obj.toString()",
        registers="Writes vAA (v0–v255).",
        mistakes=[
            "Using move-result for an object-returning call: the verifier rejects the type mismatch.",
            "Forgetting move-result-object after filled-new-array, which leaves the new array unreachable.",
            "Separating the invoke and move-result-object when injecting logging code: put the new code after the "
            "move-result-object, not between them.",
        ],
        related=["move-result", "move-result-wide", "filled-new-array", "invoke-virtual", "move-object"],
        tags=["move", "result", "object", "invoke"],
    ))
    out.append(make(
        ctx, "move-exception", "exception",
        syntax=spec["move-exception"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives the caught Throwable.")],
        summary="Store the just-caught exception in vAA; first instruction of a catch handler.",
        description=("When a try block throws and control jumps to a matching .catch handler, move-exception "
                     "retrieves the exception object. It may only appear as the first instruction of a handler. "
                     "Handlers that do not care about the exception object can simply leave it out."),
        technical=(enc(ctx, "11x") + " Valid only as the first instruction of an exception handler (a target listed "
                   "in the method's encoded_catch_handler data, written in smali with .catch/.catchall). The "
                   "verifier types vAA as the caught class, or as the common superclass when one handler serves "
                   "several catch types, or as Throwable for .catchall. Cannot throw."),
        example=ex("""
            .method public static parseOr(Ljava/lang/String;)I
                .locals 1
                :try_start_0
                invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
                move-result v0
                :try_end_0
                .catch Ljava/lang/NumberFormatException; {:try_start_0 .. :try_end_0} :catch_0
                return v0

                :catch_0
                move-exception v0
                const/4 v0, -0x1
                return v0
            .end method
        """),
        explanation=("If parseInt throws NumberFormatException, control continues at :catch_0, where "
                     "move-exception stores the exception in v0. The handler then overwrites v0 with -1 and "
                     "returns it."),
        java="try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }",
        kotlin="return try { s.toInt() } catch (e: NumberFormatException) { -1 }",
        registers="Writes vAA (v0–v255).",
        mistakes=[
            "Placing move-exception anywhere other than the first instruction of a handler: the verifier rejects it.",
            "Jumping to a handler label from normal code (goto :catch_0), which makes move-exception reachable "
            "without an exception.",
            "Forgetting that the try range in .catch is exclusive at :try_end, so the faulting instruction must "
            "come before the end label.",
        ],
        related=["throw", "move-result-object", "monitor-exit"],
        tags=["exception", "catch", "object"],
        refs_extra=[REF_DEX_CATCH],
    ))
    return out


# =================================================================================================================
# return*
# =================================================================================================================

def gen_returns(ctx):
    spec = ctx.spec_by_name
    out = [make(
        ctx, "return-void", "return",
        syntax="return-void",
        operands=[],
        summary="Return from a method whose return type is void.",
        description=("return-void ends the current method without a value and hands control back to the caller. "
                     "Every void method and every constructor (<init>) and static initializer (<clinit>) must end "
                     "each path with it or with a throw."),
        technical=(enc(ctx, "10x") + " Valid only in methods declared with return type V. Execution must never "
                   "fall off the end of a method's code, so the verifier requires every path to end in a return "
                   "or throw. A return does not release monitors taken with monitor-enter (dex code has no implicit "
                   "method lock), so synchronized code must run monitor-exit first."),
        example=ex("""
            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method
        """),
        explanation="A default constructor calls the superclass constructor and then returns with return-void.",
        java="return;", kotlin="return",
        registers="None.",
        mistakes=[
            "Using return-void in a method whose descriptor returns a value: the verifier rejects it.",
            "Deleting the final return-void while patching, so execution would fall off the end of the code.",
            "Forgetting that constructors are void (<init>(...)V) even though new-expressions produce an object.",
        ],
        related=["return", "return-wide", "return-object", "throw"],
        tags=["return", "control-flow"],
    )]
    variants = [
        ("return", "a 32-bit primitive value (int, float, boolean, byte, char or short)",
         "return value register, v0–v255.", "I", "isEven", "(I)Z",
         ["rem-int/lit8 v0, p0, 0x2", "if-nez v0, :odd", "const/4 v0, 0x1", "return v0", ":odd",
          "const/4 v0, 0x0", "return v0"], 1,
         "return n % 2 == 0;", "return n % 2 == 0",
         "isEven returns a boolean, which is a 32-bit value in a register, so both paths end with return v0 "
         "after loading 1 (true) or 0 (false)."),
        ("return-wide", "a 64-bit long or double held in a register pair",
         "first register of the pair holding the result, v0–v255; the pair is vAA, vAA+1.", "J", "twice", "(J)J",
         ["add-long v0, p0, p0", "return-wide v0"], 2,
         "return x + x;", "return x + x",
         "The long sum is built in the pair v0/v1, and return-wide names the first register of the pair."),
        ("return-object", "an object reference (or null)",
         "register holding the returned reference, v0–v255.", "L", "orEmpty",
         "(Ljava/lang/String;)Ljava/lang/String;",
         ["if-nez p0, :done", "const-string p0, \"\"", ":done", "return-object p0"], 0,
         "return s != null ? s : \"\";", "return s ?: \"\"",
         "If the parameter is non-null it is returned unchanged; otherwise p0 is overwritten with the empty "
         "string and returned."),
    ]
    for name, what, a_desc, d, mname, sig, body, locals_, java, kotlin, explain in variants:
        wide = name == "return-wide"
        code = "\n".join("    " + line if not line.startswith(":") else "    " + line for line in body)
        example = f".method public static {mname}{sig}\n    .locals {locals_}\n{code}\n.end method"
        mistakes = {
            "return": ["Returning an object with return instead of return-object, or a long with return instead "
                       "of return-wide.",
                       "Returning a value from a void method: use return-void there.",
                       "Returning an int register from a method declared to return float (or vice versa) after "
                       "editing the descriptor: the verifier checks the register type against the return type."],
            "return-wide": ["Naming the second register of the pair (v1 for the pair v0/v1).",
                            "Using return for a long or double result: it would return only one half.",
                            "Returning a pair that was built from two unrelated int registers: the verifier tracks "
                            "wide halves and rejects it."],
            "return-object": ["Returning a primitive register with return-object.",
                              "Returning an object whose verified type is not assignable to the declared return "
                              "type: add a check-cast first.",
                              "Assuming null needs special handling: const/4 vX, 0x0 is the null reference and can "
                              "be returned with return-object."],
        }[name]
        out.append(make(
            ctx, name, "return",
            syntax=spec[name].spec_syntax,
            operands=[opnd("vAA", "AA", 8, a_desc)],
            summary={"return": "Return a 32-bit primitive value (int, float, boolean, ...) from the method.",
                     "return-wide": "Return a 64-bit long or double held in the pair vAA/vAA+1.",
                     "return-object": "Return an object reference (or null) from the method."}[name],
            description=(f"{name} ends the method and hands {what} back to the caller. The caller retrieves it with "
                         f"{ {'return': 'move-result', 'return-wide': 'move-result-wide', 'return-object': 'move-result-object'}[name] } "
                         f"right after its invoke. The register's type must match the return type in the method "
                         f"descriptor."),
            technical=(enc(ctx, "11x") + f" Reads vAA{' and vAA+1' if wide else ''}. Valid only in methods whose "
                       f"return type is "
                       + {"return": "Z, B, S, C, I or F",
                          "return-wide": "J or D",
                          "return-object": "a reference type (class or array); the value must be assignable to it"}[name]
                       + ". A return does not release monitors taken with monitor-enter, so synchronized code must "
                         "run monitor-exit first."),
            example=example,
            explanation=explain,
            java=java, kotlin=kotlin,
            registers=f"Reads vAA{' and vAA+1' if wide else ''} (vAA in v0–v255). Writes nothing.",
            mistakes=mistakes,
            related=["return-void", "return", "return-wide", "return-object",
                     {"return": "move-result", "return-wide": "move-result-wide",
                      "return-object": "move-result-object"}[name]],
            tags=["return", "control-flow"] + (["wide"] if wide else []) + (["object"] if name == "return-object" else []),
        ))
    return out


# =================================================================================================================
# const*
# =================================================================================================================

def gen_consts(ctx):
    spec = ctx.spec_by_name
    out = []

    def c(name, syntax, operands, summary, description, technical, example, explanation, java, kotlin, registers,
          mistakes, related, tags, refs_extra=()):
        out.append(make(ctx, name, "constant", syntax=syntax, operands=operands, summary=summary,
                        description=description, technical=technical, example=example, explanation=explanation,
                        java=java, kotlin=kotlin, registers=registers, mistakes=mistakes, related=related,
                        tags=["constant", "literal"] + tags if "literal" not in tags and name.startswith("const") and
                        not name.startswith(("const-string", "const-class")) else ["constant"] + tags,
                        refs_extra=refs_extra))

    c("const/4", "const/4 vA, lit4",
      [opnd("vA", "A", 4, "destination register, v0–v15."),
       opnd("lit4", "B", 4, "signed literal, -8..7 (smali: 0x0..0x7 or -0x1..-0x8); sign-extended to 32 bits.")],
      "Load a small signed literal (-8..7) into vA.",
      ("const/4 is the most compact way to load a small number into a register: the whole instruction is a single "
       "code unit. It is the usual way to load 0, 1, true, false and null, because null, false and 0 all have the "
       "same bit pattern."),
      enc(ctx, "11n") + (" The 4-bit field B is sign-extended to 32 bits, so the bit pattern 0xf loads -1. The "
                         "register gets the value 'as bits': the verifier decides later whether it is used as an "
                         "int, float, boolean or, for 0, a null reference. Cannot throw."),
      ex("""
          .method public isEnabled()Z
              .locals 1
              const/4 v0, 0x1
              return v0
          .end method
      """),
      "The method returns true; booleans are just the ints 0 and 1 in registers, so const/4 v0, 0x1 loads true.",
      "boolean enabled = true;   // also: int x = 5; Object o = null;",
      "val enabled = true   // also: val x = 5; val o: Any? = null",
      "Writes vA (v0–v15). " + P_REG_NOTE,
      ["Using a value outside -8..7 (for example 0x8): smali rejects the literal; use const/16 instead.",
       "Loading a float with const/4: 0x1 as float bits is the tiny denormal 1.4E-45, not 1.0f. Use the float bit "
       "pattern (1.0f is 0x3f800000, loaded with const/high16).",
       "Targeting a register above v15 (or a pN that maps above v15); use const/16 there."],
      ["const/16", "const", "const/high16", "const-wide/16"], ["lit4", "null", "boolean"])

    c("const/16", "const/16 vAA, lit16",
      [opnd("vAA", "AA", 8, "destination register, v0–v255."),
       opnd("lit16", "BBBB", 16, "signed literal, -32768..32767; sign-extended to 32 bits.")],
      "Load a signed 16-bit literal (-32768..32767) into vAA.",
      ("const/16 loads integers that do not fit in const/4 but fit in 16 signed bits, such as 1000 or -200. It can "
       "also address registers up to v255."),
      enc(ctx, "21s") + (" The 16-bit literal BBBB is sign-extended to 32 bits (0xffff loads -1). As with every "
                         "const, the register receives raw bits whose type (int or float) is decided by how they "
                         "are used. Cannot throw."),
      ex("""
          .method public static timeoutMs()I
              .locals 1
              const/16 v0, 0x3e8    # 1000
              return v0
          .end method
      """),
      "1000 is too large for const/4 but fits in 16 signed bits, so const/16 is the shortest choice.",
      "int timeout = 1000;", "val timeout = 1000",
      "Writes vAA (v0–v255).",
      ["Expecting const/16 to load 0xffff as 65535: the literal is signed, so it is -1. Use const for 65535.",
       "Using const/16 for values like 0x10000 or larger: use const (or const/high16 when the low 16 bits are 0).",
       "Using const/16 to fill a long register: use const-wide/16 so that both halves of the pair are written."],
      ["const/4", "const", "const/high16", "const-wide/16"], ["lit16"])

    c("const", "const vAA, lit32",
      [opnd("vAA", "AA", 8, "destination register, v0–v255."),
       opnd("lit32", "BBBBBBBB", 32, "arbitrary 32-bit constant (int bits or float bits).")],
      "Load an arbitrary 32-bit constant (int or float bits) into vAA.",
      ("const loads any 32-bit value, so it is used for large ints and for float constants that no shorter form "
       "can express. Resource IDs such as 0x7f0b0012 are a common sight in decompiled apps."),
      enc(ctx, "31i") + (" The literal is stored as two code units (low half first) and copied unchanged into vAA. "
                         "smali also accepts float literals such as 1.5f and writes their IEEE 754 bits. "
                         "Cannot throw."),
      ex("""
          .method public static maxValue()I
              .locals 1
              const v0, 0x7fffffff    # Integer.MAX_VALUE
              return v0
          .end method
      """),
      "Integer.MAX_VALUE needs all 32 bits, so neither const/4 nor const/16 can load it.",
      "int max = Integer.MAX_VALUE;   // or: float f = 1.5f;",
      "val max = Int.MAX_VALUE   // or: val f = 1.5f",
      "Writes vAA (v0–v255).",
      ["Using const for a long value: it only writes one register. Use const-wide/32 or const-wide.",
       "Assuming a resource ID loaded with const is stable across builds: aapt can renumber IDs when resources "
       "change, so patched smali must use the ID from the same build's R class / public.xml.",
       "Choosing const when const/high16 would do (low 16 bits zero): both work, const just costs one code unit more."],
      ["const/4", "const/16", "const/high16", "const-wide"], ["lit32", "float"])

    c("const/high16", "const/high16 vAA, 0xBBBB0000",
      [opnd("vAA", "AA", 8, "destination register, v0–v255."),
       opnd("0xBBBB0000", "BBBB", 16, "the high 16 bits of the value; the low 16 bits are zero. In smali you write "
                                    "the full 32-bit value, e.g. 0x3f800000.")],
      "Load a 32-bit value whose low 16 bits are zero (e.g. 1.0f) into vAA.",
      ("const/high16 stores only the upper half of a 32-bit value and fills the lower half with zeros. Many common "
       "float constants (1.0f, 2.0f, 0.5f, -1.0f) and some masks have that shape, so they fit this two-unit "
       "instruction instead of the three-unit const."),
      enc(ctx, "21h") + (" vAA = BBBB << 16 (right-zero-extended to 32 bits). In smali the operand is written as "
                         "the complete value; the assembler rejects values whose low 16 bits are not zero. "
                         "Cannot throw."),
      ex("""
          .method public static scale()F
              .locals 1
              const/high16 v0, 0x3f800000    # 1.0f
              return v0
          .end method
      """),
      "1.0f has the IEEE 754 bit pattern 0x3f800000, whose low 16 bits are zero, so const/high16 can load it.",
      "float scale = 1.0f;", "val scale = 1.0f",
      "Writes vAA (v0–v255).",
      ["Writing only the high half in smali (const/high16 v0, 0x3f80): smali expects the full value 0x3f800000.",
       "Using it for a value with non-zero low bits such as 1.1f (0x3f8ccccd): use const.",
       "Confusing it with const-wide/high16, which fills a 64-bit pair (used for double constants)."],
      ["const", "const-wide/high16", "const/16"], ["lit16", "float", "high16"])

    c("const-wide/16", "const-wide/16 vAA, lit16",
      [opnd("vAA", "AA", 8, "first register of the destination pair, v0–v255; the pair is vAA, vAA+1."),
       opnd("lit16", "BBBB", 16, "signed literal, -32768..32767; sign-extended to 64 bits.")],
      "Load a signed 16-bit literal, sign-extended to 64 bits, into the pair vAA/vAA+1.",
      ("const-wide/16 loads a small long value such as 0L or 1000L into a register pair. It is the usual way to "
       "initialise long counters and timeouts."),
      enc(ctx, "21s") + (" The 16-bit literal is sign-extended to 64 bits and written to vAA (low word) and vAA+1 "
                         "(high word). smali accepts 0x3e8 or 0x3e8L. The bits can also be used as a double, but "
                         "small integers are rarely useful double bit patterns. Cannot throw."),
      ex("""
          .method public static defaultTimeout()J
              .locals 2
              const-wide/16 v0, 0x3e8    # 1000L
              return-wide v0
          .end method
      """),
      "The long 1000L is placed in the pair v0/v1, which is why .locals is 2.",
      "long timeout = 1000L;", "val timeout = 1000L",
      "Writes the pair vAA and vAA+1 (vAA in v0–v255).",
      ["Using const/16 for a long: only one register is written and the verifier rejects the later wide use.",
       "Loading 1.0 (double) with const-wide/16 v0, 0x1: that is the bit pattern of a tiny denormal. Use "
       "const-wide/high16 v0, 0x3ff0000000000000L.",
       "Forgetting that vAA+1 is overwritten as well."],
      ["const-wide/32", "const-wide", "const-wide/high16", "const/16"], ["lit16", "wide", "long"])

    c("const-wide/32", "const-wide/32 vAA, lit32",
      [opnd("vAA", "AA", 8, "first register of the destination pair, v0–v255."),
       opnd("lit32", "BBBBBBBB", 32, "signed 32-bit literal; sign-extended to 64 bits.")],
      "Load a signed 32-bit literal, sign-extended to 64 bits, into the pair vAA/vAA+1.",
      ("const-wide/32 loads long values that fit in a signed 32-bit int, for example 86400000L milliseconds in a "
       "day. Larger longs need const-wide."),
      enc(ctx, "31i") + (" The 32-bit literal is sign-extended to 64 bits, so 0xffffffff loads -1L, not 4294967295L. "
                         "Cannot throw."),
      ex("""
          .method public static dayMillis()J
              .locals 2
              const-wide/32 v0, 0x5265c00    # 86400000L
              return-wide v0
          .end method
      """),
      "86,400,000 does not fit in 16 bits but fits in 32, so const-wide/32 is the shortest encoding.",
      "long dayMillis = 86_400_000L;", "val dayMillis = 86_400_000L",
      "Writes the pair vAA and vAA+1 (vAA in v0–v255).",
      ["Expecting 0xffffffff to load 4294967295L: the literal is sign-extended, giving -1L; use const-wide.",
       "Using const (32-bit) for a long: only one register of the pair is set.",
       "Using it for doubles: most double constants need const-wide or const-wide/high16."],
      ["const-wide/16", "const-wide", "const-wide/high16", "const"], ["lit32", "wide", "long"])

    c("const-wide", "const-wide vAA, lit64",
      [opnd("vAA", "AA", 8, "first register of the destination pair, v0–v255."),
       opnd("lit64", "BBBBBBBBBBBBBBBB", 64, "arbitrary 64-bit constant (long bits or double bits); smali writes "
                                           "it with an L suffix, e.g. 0x400921fb54442d18L.")],
      "Load an arbitrary 64-bit constant (long or double) into the pair vAA/vAA+1.",
      ("const-wide can load any 64-bit value, which makes it the general way to load large longs and double "
       "constants such as Math.PI. It is the only Dalvik instruction that takes five code units."),
      enc(ctx, "51l") + (" The 64-bit literal is stored in four code units, least significant first, and copied "
                         "unchanged into vAA (low word) and vAA+1 (high word). smali also accepts double literals "
                         "such as 3.14159 and writes their IEEE 754 bits. Cannot throw."),
      ex("""
          .method public static pi()D
              .locals 2
              const-wide v0, 0x400921fb54442d18L    # Math.PI
              return-wide v0
          .end method
      """),
      "Math.PI's bit pattern uses all 64 bits, so only const-wide can load it.",
      "double pi = Math.PI;   // or: long big = 0x123456789L;", "val pi = Math.PI",
      "Writes the pair vAA and vAA+1 (vAA in v0–v255).",
      ["Writing a long literal without the L suffix when it does not fit in an int: smali parses it as an int "
       "literal and fails; write 0x123456789L.",
       "Reaching for const-wide when a shorter form fits: smali does not shrink const-wide for you.",
       "Forgetting that the pair needs two registers inside .locals/.registers."],
      ["const-wide/16", "const-wide/32", "const-wide/high16", "const"], ["lit64", "wide", "long", "double"])

    c("const-wide/high16", "const-wide/high16 vAA, 0xBBBB000000000000L",
      [opnd("vAA", "AA", 8, "first register of the destination pair, v0–v255."),
       opnd("0xBBBB000000000000L", "BBBB", 16, "the top 16 bits of the 64-bit value; all lower 48 bits are zero. "
                                             "smali takes the full value, e.g. 0x3ff0000000000000L.")],
      "Load a 64-bit value whose low 48 bits are zero (e.g. 1.0 as double) into vAA/vAA+1.",
      ("const-wide/high16 is the compact way to load 64-bit values that only use their top 16 bits. Simple double "
       "constants like 1.0, 2.0, 0.5 and -1.0 have that shape, so this is what compilers emit for them."),
      enc(ctx, "21h") + (" The pair receives BBBB << 48 (right-zero-extended to 64 bits). The smali operand is the "
                         "full 64-bit value; the assembler rejects values with any of the low 48 bits set. "
                         "Cannot throw."),
      ex("""
          .method public static one()D
              .locals 2
              const-wide/high16 v0, 0x3ff0000000000000L    # 1.0
              return-wide v0
          .end method
      """),
      "The double 1.0 is 0x3ff0000000000000, so its top 16 bits (0x3ff0) are enough to encode it.",
      "double one = 1.0;", "val one = 1.0",
      "Writes the pair vAA and vAA+1 (vAA in v0–v255).",
      ["Writing only the top 16 bits (0x3ff0) in smali: the operand must be the full 64-bit value.",
       "Using it for 1.1 or Math.PI (low bits non-zero): use const-wide.",
       "Using const/high16 instead, which builds a 32-bit float, not a double pair."],
      ["const-wide", "const/high16", "const-wide/16"], ["high16", "wide", "double"])

    c("const-string", 'const-string vAA, "string"',
      [opnd("vAA", "AA", 8, "destination register, v0–v255; receives a java.lang.String reference."),
       opnd('"string"', "BBBB", 16, "string_ids index (string@BBBB); smali writes the literal and the assembler "
                                   "assigns the index.")],
      "Load a reference to a string constant into vAA.",
      ("const-string puts a reference to a string from the dex file's string pool into a register. Every string "
       "literal in Java or Kotlin source becomes a const-string (or const-string/jumbo). The same literal always "
       "yields the same interned String instance."),
      enc(ctx, "21c") + (" BBBB is an index into string_ids, so it can only reach the first 65,536 strings of the "
                         "dex file; strings with larger indices need const-string/jumbo. The resulting String is "
                         "interned. Resolution can in principle fail with an OutOfMemoryError, but it never throws "
                         "for a valid index."),
      ex("""
          .method public static tag()Ljava/lang/String;
              .locals 1
              const-string v0, "MainActivity"
              return-object v0
          .end method
      """),
      "The literal \"MainActivity\" lives in the string pool; const-string loads a reference to it into v0.",
      'String tag = "MainActivity";', 'val tag = "MainActivity"',
      "Writes vAA (v0–v255).",
      ["Escaping strings incorrectly in smali: use Java-style escapes such as \\n, \\\" and \\u00e9 inside the "
       "quotes.",
       "Comparing two strings with if-eq: it compares references, which only happens to work for interned "
       "literals. Call String.equals instead.",
       "Hand-writing const-string in a huge dex where the index exceeds 0xffff: the tools must use "
       "const-string/jumbo."],
      ["const-string/jumbo", "const-class", "invoke-virtual"], ["string", "object"], refs_extra=[REF_DEX_STRING])

    c("const-string/jumbo", 'const-string/jumbo vAA, "string"',
      [opnd("vAA", "AA", 8, "destination register, v0–v255."),
       opnd('"string"', "BBBBBBBB", 32, "32-bit string_ids index (string@BBBBBBBB).")],
      "Load a string constant whose pool index needs 32 bits into vAA.",
      ("const-string/jumbo does the same job as const-string, but its string index is 32 bits wide. Dex files with "
       "more than 65,536 strings need it for strings beyond that limit. Apart from the size of the index, the "
       "two instructions behave identically."),
      enc(ctx, "31c") + (" The 32-bit index into string_ids is stored low half first. The behaviour is identical to "
                         "const-string: an interned java.lang.String reference is written to vAA. It is one code "
                         "unit longer, which matters when re-assembling: changing const-string to /jumbo shifts "
                         "branch offsets, which smali recomputes for you."),
      ex("""
          # in a large dex this string's index is above 0xffff
          const-string/jumbo v0, "com.acme.demo.analytics.EVENT_OPEN"
          invoke-static {v0}, Lcom/acme/demo/Analytics;->log(Ljava/lang/String;)V
      """),
      "The string index does not fit in 16 bits, so the compiler used the /jumbo form to load it before logging.",
      'String event = "com.acme.demo.analytics.EVENT_OPEN";', 'val event = "com.acme.demo.analytics.EVENT_OPEN"',
      "Writes vAA (v0–v255).",
      ["Assuming /jumbo means a long string: it refers to a large string index, not a long string.",
       "Converting /jumbo back to const-string after merging dex files without checking that the index fits.",
       "Treating jumbo strings as obfuscation: d8/R8 emit them simply because the string pool is large."],
      ["const-string", "const-class"], ["string", "object", "jumbo"], refs_extra=[REF_DEX_STRING])

    c("const-class", "const-class vAA, Ltype;",
      [opnd("vAA", "AA", 8, "destination register, v0–v255; receives a java.lang.Class reference."),
       opnd("Ltype;", "BBBB", 16, "type_ids index (type@BBBB): a class, array or primitive type descriptor.")],
      "Load the java.lang.Class object for a type into vAA.",
      ("const-class loads the Class object of a type, which is what a Java Foo.class literal compiles to. Android "
       "code uses it all the time, for example to build an Intent for an Activity class. For a primitive "
       "descriptor such as I, the result is the primitive's class object (int.class)."),
      enc(ctx, "21c") + (" Resolves the type at type@BBBB and writes the Class reference to vAA. Resolution can fail "
                         "with NoClassDefFoundError if the class is missing, or IllegalAccessError if it is not "
                         "accessible. Loading the Class object does not run the class's static initializer."),
      ex("""
          # Intent intent = new Intent(this, SettingsActivity.class);
          new-instance v0, Landroid/content/Intent;
          const-class v1, Lcom/acme/demo/SettingsActivity;
          invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
      """),
      "const-class supplies the Class argument of the Intent constructor, exactly like SettingsActivity.class "
      "in Java.",
      "Class<?> c = SettingsActivity.class;", "val c = SettingsActivity::class.java",
      "Writes vAA (v0–v255).",
      ["Expecting const-class to initialise the class: static initializers run on first use (new-instance, sget, "
       "invoke-static), not when the Class object is loaded.",
       "Using a dotted name (com.acme.demo.Foo) instead of a descriptor (Lcom/acme/demo/Foo;).",
       "Referencing a class that is stripped by R8 in the patched build, which leads to NoClassDefFoundError at "
       "run time."],
      ["const-string", "check-cast", "instance-of", "new-instance"], ["class", "object", "type"],
      refs_extra=[REF_DEX_TYPE])
    return out


# =================================================================================================================
# monitors, type checks, new-instance, arrays (non-indexed), throw
# =================================================================================================================

def gen_misc(ctx):
    spec = ctx.spec_by_name
    out = []
    sync_example = ex("""
        .method public increment()V
            .locals 1
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
            move-exception v0
            monitor-exit p0
            throw v0
        .end method
    """)
    out.append(make(
        ctx, "monitor-enter", "monitor",
        syntax=spec["monitor-enter"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "register holding the object whose monitor (lock) is acquired, v0–v255.")],
        summary="Acquire the monitor (intrinsic lock) of the object in vAA.",
        description=("monitor-enter takes the lock of an object, blocking while another thread holds it. It marks "
                     "the start of a synchronized block, and in dex code also the start of a synchronized method's "
                     "body. Each monitor-enter must be paired with a monitor-exit on "
                     "every path, including the exception path."),
        technical=(enc(ctx, "11x") + " Throws NullPointerException if vAA is null. Monitors are re-entrant: a thread "
                   "may enter a monitor it already holds, and must exit it the same number of times. In dex files "
                   "ACC_SYNCHRONIZED is only valid on native methods; a Java synchronized method is merely flagged "
                   "ACC_DECLARED_SYNCHRONIZED (no effect on execution), so compilers wrap its body in "
                   "monitor-enter/monitor-exit on this (or on the Class object for static methods)."),
        example=sync_example,
        explanation=("This is the shape javac/d8 produce for synchronized (this) { count++; }: the catch-all "
                     "handler releases the lock and rethrows if anything inside the block throws."),
        java="synchronized (this) { count++; }", kotlin="synchronized(this) { count++ }",
        registers="Reads vAA (v0–v255). Writes nothing.",
        mistakes=[
            "Exiting the method on some path without monitor-exit, which leaves the lock held.",
            "Leaving out the catch-all handler: if code inside the block throws, the monitor is never released.",
            "Locking on a register that may be null, which throws NullPointerException.",
        ],
        related=["monitor-exit", "move-exception", "throw"],
        tags=["monitor", "synchronized", "lock", "object"],
        refs_extra=[REF_DEX_ACCESS],
    ))
    out.append(make(
        ctx, "monitor-exit", "monitor",
        syntax=spec["monitor-exit"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "register holding the object whose monitor is released, v0–v255.")],
        summary="Release the monitor (intrinsic lock) of the object in vAA.",
        description=("monitor-exit releases a lock taken by monitor-enter and marks the end of a synchronized "
                     "block. Compilers emit it twice: once on the normal path and once in a catch-all handler that "
                     "then rethrows the exception."),
        technical=(enc(ctx, "11x") + " Throws NullPointerException if vAA is null and IllegalMonitorStateException "
                   "if the current thread does not hold the monitor. The spec requires that if it throws, it does so "
                   "as if the pc had already advanced past the instruction, so a catch-all range that covers the "
                   "monitor-exit itself can safely retry the release."),
        example=sync_example,
        explanation=("The lock is released on the normal path just before return-void, and again in :catchall_0 "
                     "before the exception is rethrown, so the monitor is released whichever way the block ends."),
        java="} // end of synchronized (this) { ... }", kotlin="// end of synchronized(this) { ... }",
        registers="Reads vAA (v0–v255). Writes nothing.",
        mistakes=[
            "Releasing a different object than the one locked (for example after the register was overwritten): "
            "throws IllegalMonitorStateException.",
            "Removing the monitor-exit in the handler while patching, which can leave the lock held after an "
            "exception.",
            "Exiting more often than entering on some path.",
        ],
        related=["monitor-enter", "move-exception", "throw"],
        tags=["monitor", "synchronized", "lock", "object"],
    ))
    out.append(make(
        ctx, "check-cast", "type",
        syntax="check-cast vAA, Ltype;",
        operands=[opnd("vAA", "AA", 8, "register holding the reference to check, v0–v255."),
                  opnd("Ltype;", "BBBB", 16, "type_ids index (type@BBBB) of the target class or array type.")],
        summary="Throw ClassCastException unless the reference in vAA is null or an instance of the type.",
        description=("check-cast is a Java cast such as (TextView) view. If the object is compatible with the type, "
                     "nothing visible happens and the verifier from then on treats the register as that type. "
                     "Otherwise a ClassCastException is thrown. A null reference always passes."),
        technical=(enc(ctx, "21c") + " Reads vAA; the register is not rewritten, but its verified type is narrowed to "
                   "the target type. Throws ClassCastException on mismatch; class resolution can throw "
                   "NoClassDefFoundError. Because vAA must be a reference, a primitive target type always fails at "
                   "run time."),
        example=ex("""
            const v1, 0x7f080042    # R.id.title
            invoke-virtual {p0, v1}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
            move-result-object v0
            check-cast v0, Landroid/widget/TextView;
            const-string v1, "Hello"
            invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
        """),
        explanation=("findViewById returns a View; check-cast narrows it to TextView so that the verifier accepts "
                     "the following setText call on v0."),
        java="TextView title = (TextView) findViewById(R.id.title);",
        kotlin="val title = findViewById<View>(R.id.title) as TextView",
        registers="Reads vAA (v0–v255) and refines its static type in place; writes nothing.",
        mistakes=[
            "Removing a check-cast while patching: the verifier may then reject later calls that need the narrower "
            "type.",
            "Expecting check-cast to fail for null: null passes every check-cast.",
            "Expecting a result register: unlike instance-of, check-cast has no destination.",
        ],
        related=["instance-of", "const-class", "move-result-object"],
        tags=["type", "cast", "object", "throws"],
        refs_extra=[REF_DEX_TYPE],
    ))
    out.append(make(
        ctx, "instance-of", "type",
        syntax="instance-of vA, vB, Ltype;",
        operands=[opnd("vA", "A", 4, "destination register, v0–v15; receives 1 or 0."),
                  opnd("vB", "B", 4, "register holding the reference to test, v0–v15."),
                  opnd("Ltype;", "CCCC", 16, "type_ids index (type@CCCC) of the class or array type.")],
        summary="Set vA to 1 if the reference in vB is an instance of the type, otherwise 0.",
        description=("instance-of is the Java instanceof operator. It never throws ClassCastException: it just "
                     "writes 1 (true) or 0 (false) into the destination register. A null reference gives 0."),
        technical=(enc(ctx, "22c") + " Writes an int (boolean) 1 or 0 to vA. Both registers use 4-bit fields "
                   "(v0–v15). A primitive type index always produces 0. Class resolution can throw "
                   "NoClassDefFoundError; otherwise it does not throw."),
        example=ex("""
            .method public static isText(Ljava/lang/Object;)Z
                .locals 1
                instance-of v0, p0, Ljava/lang/String;
                return v0
            .end method
        """),
        explanation="v0 becomes 1 when the argument is a String and 0 otherwise (including null), and is returned "
                    "as a boolean.",
        java="boolean isText = obj instanceof String;", kotlin="val isText = obj is String",
        registers="Writes vA, reads vB; both must be in v0–v15. " + P_REG_NOTE,
        mistakes=[
            "Assuming instance-of also narrows the type of vB for the verifier: you still need check-cast before "
            "using vB as the subtype.",
            "Using registers above v15: move the reference into a low register first.",
            "Expecting true for null: instance-of always yields 0 for null.",
        ],
        related=["check-cast", "if-eqz", "if-nez", "const-class"],
        tags=["type", "instanceof", "object"],
        refs_extra=[REF_DEX_TYPE],
    ))
    out.append(make(
        ctx, "array-length", "array",
        syntax=spec["array-length"].spec_syntax,
        operands=[opnd("vA", "A", 4, "destination register, v0–v15; receives the length as an int."),
                  opnd("vB", "B", 4, "register holding the array reference, v0–v15.")],
        summary="Store the number of elements of the array in vB into vA.",
        description=("array-length reads the length of any array, whatever its element type. It is what the "
                     "Java expression array.length compiles to, and it shows up in nearly every for loop over "
                     "an array."),
        technical=(enc(ctx, "12x") + " Throws NullPointerException if vB is null. The verifier requires vB to be an "
                   "array type (or null). The result is a non-negative int."),
        example=ex("""
            .method public static count([Ljava/lang/String;)I
                .locals 1
                array-length v0, p0
                return v0
            .end method
        """),
        explanation="The length of the String[] parameter is written to v0 and returned.",
        java="int n = items.length;", kotlin="val n = items.size",
        registers="Writes vA, reads vB; both in v0–v15.",
        mistakes=[
            "Calling array-length on a register that may be null, which throws NullPointerException.",
            "Expecting it to work on collections: List.size() is a method call (invoke-interface), not array-length.",
            "Using a high register; the fields are only 4 bits.",
        ],
        related=["new-array", "aget", "aput", "filled-new-array"],
        tags=["array", "length"],
    ))
    out.append(make(
        ctx, "new-instance", "object",
        syntax="new-instance vAA, Lclass;",
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives the new, uninitialised object."),
                  opnd("Lclass;", "BBBB", 16, "type_ids index (type@BBBB) of a non-array class.")],
        summary="Allocate a new, not yet constructed instance of a class into vAA.",
        description=("new-instance allocates memory for an object but does not run any constructor. It is always "
                     "followed by an invoke-direct of one of the class's <init> methods on the same register. "
                     "Together, the two instructions form a Java new expression."),
        technical=(enc(ctx, "21c") + " The type must be a non-array class. Until <init> is invoked on the register, "
                   "the verifier treats it as an uninitialised reference that can't be stored, passed or returned. "
                   "Can throw InstantiationError for interfaces and abstract classes, IllegalAccessError, "
                   "NoClassDefFoundError, OutOfMemoryError, and errors from running the class's static "
                   "initializer if this is the first use of the class."),
        example=ex("""
            new-instance v0, Ljava/lang/StringBuilder;
            invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
            const-string v1, "id="
            invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
        """),
        explanation=("new StringBuilder() compiles to new-instance (allocation) plus invoke-direct <init> "
                     "(construction). Only after the constructor call can v0 be used for append."),
        java="StringBuilder sb = new StringBuilder();", kotlin="val sb = StringBuilder()",
        registers="Writes vAA (v0–v255).",
        mistakes=[
            "Forgetting the invoke-direct <init> call: the verifier rejects any use of the uninitialised object.",
            "Calling the constructor on a copy made with move-object before initialisation: initialise the register "
            "returned by new-instance itself.",
            "Using new-instance for arrays: arrays are created with new-array or filled-new-array.",
        ],
        related=["invoke-direct", "new-array", "const-class"],
        tags=["object", "allocation", "constructor"],
        refs_extra=[REF_DEX_TYPE],
    ))
    out.append(make(
        ctx, "new-array", "array",
        syntax="new-array vA, vB, [type",
        operands=[opnd("vA", "A", 4, "destination register, v0–v15; receives the new array."),
                  opnd("vB", "B", 4, "register holding the requested length (int), v0–v15."),
                  opnd("[type", "CCCC", 16, "type_ids index (type@CCCC) of an array type, e.g. [I or "
                                            "[Ljava/lang/String;.")],
        summary="Create a new array of the given array type with vB elements and store it in vA.",
        description=("new-array creates an array whose length comes from a register. Every element starts at its "
                     "default value: 0, false, 0.0 or null. It is what new int[n] or arrayOfNulls<String>(n) "
                     "compiles to."),
        technical=(enc(ctx, "22c") + " The type must be an array type. Throws NegativeArraySizeException if the "
                   "length is negative, OutOfMemoryError if it cannot be allocated, and resolution errors for a "
                   "missing element class. For multi-dimensional types such as [[I it creates only the outer array, "
                   "with null elements."),
        example=ex("""
            .method public static buffer(I)[B
                .locals 1
                new-array v0, p0, [B
                return-object v0
            .end method
        """),
        explanation="A byte[] of length p0 is allocated; all elements are 0.",
        java="byte[] buf = new byte[size];", kotlin="val buf = ByteArray(size)",
        registers="Writes vA, reads vB; both in v0–v15 (vA and vB may be the same register).",
        mistakes=[
            "Passing a non-array type such as I instead of [I.",
            "Forgetting that a negative length throws NegativeArraySizeException at run time.",
            "Expecting new-array [[I to allocate the inner arrays too: only the outer array is created.",
        ],
        related=["filled-new-array", "fill-array-data", "array-length", "aput", "new-instance"],
        tags=["array", "allocation"],
        refs_extra=[REF_DEX_TYPE],
    ))
    out.append(make(
        ctx, "filled-new-array", "array",
        syntax="filled-new-array {vC, vD, vE, vF, vG}, [type",
        operands=[opnd("A", "A", 4, "array size and argument word count, 0–5; smali derives it from the register "
                                    "list."),
                  opnd("vC", "C", 4, "first element value."), opnd("vD", "D", 4, "second element value."),
                  opnd("vE", "E", 4, "third element value."), opnd("vF", "F", 4, "fourth element value."),
                  opnd("vG", "G", 4, "fifth element value."),
                  opnd("[type", "BBBB", 16, "type_ids index (type@BBBB) of an array type with single-word "
                                            "elements.")],
        summary="Create an array from up to five registers; collect it with move-result-object.",
        description=("filled-new-array builds a small array directly from register values. The array is not "
                     "written to a register: like a method call, it becomes the pending result and is picked up "
                     "with move-result-object. Elements must be single-word values (ints or references), not long "
                     "or double."),
        technical=(enc(ctx, "35c") + " A gives the number of elements (0–5), taken from vC..vG in order; each "
                   "register field is 4 bits (v0–v15). The type must be an array type whose elements fit in one "
                   "register: arrays of long or double are not allowed. Can throw OutOfMemoryError and type "
                   "resolution errors."),
        example=ex("""
            const/4 v0, 0x1
            const/4 v1, 0x2
            const/4 v2, 0x3
            filled-new-array {v0, v1, v2}, [I
            move-result-object v3
        """),
        explanation="The three ints become the new int[]{1, 2, 3}, which move-result-object stores in v3.",
        java="int[] values = {1, 2, 3};", kotlin="val values = intArrayOf(1, 2, 3)",
        registers=("Reads vC..vG (only the first A of them), each in v0–v15; writes no register directly. The "
                   "result goes to the pending result, read by move-result-object."),
        mistakes=[
            "Forgetting move-result-object right after the instruction, which loses the array.",
            "Trying to build a long[] or double[]: not allowed. Use new-array plus fill-array-data or aput-wide.",
            "Listing more than five registers: use filled-new-array/range.",
        ],
        related=["filled-new-array/range", "move-result-object", "new-array", "fill-array-data"],
        tags=["array", "allocation", "result"],
        refs_extra=[REF_DEX_TYPE],
    ))
    out.append(make(
        ctx, "filled-new-array/range", "array",
        syntax="filled-new-array/range {vCCCC .. vNNNN}, [type",
        operands=[opnd("AA", "AA", 8, "array size and argument word count, 0–255; smali derives it from the range."),
                  opnd("vCCCC", "CCCC", 16, "first register of the consecutive range, v0–v65535."),
                  derived("vNNNN", "last register of the range, NNNN = CCCC + AA - 1."),
                  opnd("[type", "BBBB", 16, "type_ids index (type@BBBB) of an array type with single-word "
                                            "elements.")],
        summary="Create an array from a consecutive register range; collect it with move-result-object.",
        description=("filled-new-array/range works like filled-new-array, but takes its elements from a run of "
                     "consecutive registers. That removes the five-element limit and lets the elements live in "
                     "high registers."),
        technical=(enc(ctx, "3rc") + " Elements come from vCCCC through vCCCC+AA-1, in order. The same restrictions "
                   "as filled-new-array apply: array type only, no long/double elements. The result must be "
                   "collected with move-result-object. Can throw OutOfMemoryError and type resolution errors."),
        example=ex("""
            # String[] parts = {a, b, c, d, e, f};   (values already in v0..v5)
            filled-new-array/range {v0 .. v5}, [Ljava/lang/String;
            move-result-object v6
        """),
        explanation="Six strings exceed the five-register limit, so the range form gathers v0 to v5 into a new String[].",
        java="String[] parts = {a, b, c, d, e, f};", kotlin="val parts = arrayOf(a, b, c, d, e, f)",
        registers="Reads vCCCC..vNNNN (consecutive); writes no register directly (pending result).",
        mistakes=[
            "Using registers that are not consecutive: the range syntax only names the first and last register.",
            "Writing {v0 .. v5} when the values are actually spread over other registers: move them into place "
            "first.",
            "Forgetting move-result-object.",
        ],
        related=["filled-new-array", "move-result-object", "new-array"],
        tags=["array", "allocation", "range", "result"],
        refs_extra=[REF_DEX_TYPE],
    ))
    fa = ctx.payload_tables["fill-array-data-payload"]
    out.append(make(
        ctx, "fill-array-data", "array",
        syntax="fill-array-data vAA, :array_data",
        operands=[opnd("vAA", "AA", 8, "register holding the primitive array to fill, v0–v255."),
                  opnd(":array_data", "BBBBBBBB", 32, "signed offset, in code units relative to this instruction, of "
                                                     "a fill-array-data-payload; smali uses a label.")],
        summary="Copy constant data from a fill-array-data-payload table into the array in vAA.",
        description=("fill-array-data copies a block of constant values into an existing primitive array in one "
                     "step. Compilers use it for array initialisers such as {2, 3, 5, 7, 11}, after allocating the "
                     "array with new-array. The values themselves sit in a payload table, usually at the end of "
                     "the method."),
        technical=(enc(ctx, "31t") + " The offset points to a fill-array-data-payload (ident 0x0300) that must be "
                   "4-byte aligned. The array must be an array of primitives whose element width matches the "
                   "payload's element_width. The table may be shorter than the array, in which case only the first "
                   "elements are set; it must not be longer (ART throws ArrayIndexOutOfBoundsException). Throws "
                   "NullPointerException if vAA is null. The payload's size in code units is "
                   f"{fa['units']}."),
        example=ex("""
            .method public static primes()[I
                .locals 1
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
        """),
        explanation=("A 5-element int[] is allocated and then filled from the .array-data table (element width 4 "
                     "bytes). smali turns the .array-data block into a fill-array-data-payload and pads it to an "
                     "even code-unit offset."),
        java="int[] primes = {2, 3, 5, 7, 11};", kotlin="val primes = intArrayOf(2, 3, 5, 7, 11)",
        registers="Reads vAA (the array reference, v0–v255); writes the array's elements in memory, no registers.",
        mistakes=[
            "Using it on an object array (String[]): only primitive arrays are allowed.",
            "Declaring the wrong width in .array-data (for example 4 for a byte[]): the width must match the element "
            "type (1 for byte/boolean, 2 for short/char, 4 for int/float, 8 for long/double).",
            "Putting more elements in the table than the array holds.",
        ],
        related=["fill-array-data-payload", "new-array", "filled-new-array", "aput"],
        tags=["array", "payload", "initializer"],
        refs_extra=[REF_FILL],
    ))
    out.append(make(
        ctx, "throw", "exception",
        syntax=spec["throw"].spec_syntax,
        operands=[opnd("vAA", "AA", 8, "register holding the Throwable to throw, v0–v255.")],
        summary="Throw the exception object held in vAA.",
        description=("throw raises the exception object in a register. Control passes to the nearest matching "
                     "catch handler in this method or further up the call stack. The exception is usually created "
                     "just before with new-instance and an <init> call."),
        technical=(enc(ctx, "11x") + " vAA must hold a reference to a java.lang.Throwable (or a subclass). If vAA is "
                   "null, a NullPointerException is thrown instead. Like return, throw ends the current path, so no "
                   "fall-through instruction is needed after it."),
        example=ex("""
            new-instance v0, Ljava/lang/IllegalArgumentException;
            const-string v1, "count < 0"
            invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V
            throw v0
        """),
        explanation="The exception is allocated, constructed with a message and then thrown with throw v0.",
        java='throw new IllegalArgumentException("count < 0");',
        kotlin='throw IllegalArgumentException("count < 0")',
        registers="Reads vAA (v0–v255). Writes nothing.",
        mistakes=[
            "Throwing a register that does not hold a Throwable: the verifier rejects it.",
            "Throwing the object before calling its constructor (an uninitialised reference).",
            "Expecting code after throw to run: the path ends at the throw.",
        ],
        related=["move-exception", "new-instance", "invoke-direct"],
        tags=["exception", "throw", "control-flow", "object"],
    ))
    return out


# =================================================================================================================
# goto
# =================================================================================================================

def gen_gotos(ctx):
    spec = ctx.spec_by_name
    out = []
    data = [
        ("goto", "10t", 8, "AA", "-128..127", "Unconditionally jump by a signed 8-bit code-unit offset.",
         "The 8-bit offset reaches 128 code units backwards or 127 forwards, enough for short loops."),
        ("goto/16", "20t", 16, "AAAA", "-32768..32767", "Unconditionally jump by a signed 16-bit code-unit offset.",
         "The 16-bit offset reaches about 32 K code units in either direction."),
        ("goto/32", "30t", 32, "AAAAAAAA", "the full signed 32-bit range",
         "Unconditionally jump by a signed 32-bit code-unit offset.",
         "The 32-bit offset can reach any instruction in the method."),
    ]
    for name, fmt, bits, letters, rng, summary, reach in data:
        zero = ("Unlike goto and goto/16, the offset may be 0, so goto/32 can branch to itself (a legal spin loop)."
                if name == "goto/32" else
                "The offset must not be 0; a loop that branches to itself has to use goto/32 or a nop as target.")
        if name == "goto":
            example = ex("""
                .method public static sumTo(I)I
                    .locals 2
                    const/4 v0, 0x0    # sum
                    const/4 v1, 0x0    # i
                    :loop
                    if-gt v1, p0, :done
                    add-int/2addr v0, v1
                    add-int/lit8 v1, v1, 0x1
                    goto :loop
                    :done
                    return v0
                .end method
            """)
            explanation = ("goto :loop jumps back a few code units to the loop condition, well within the 8-bit "
                           "range. smali computes the offset from the label.")
        elif name == "goto/16":
            example = ex("""
                :loop_start
                # ... a loop body larger than 127 code units ...
                add-int/lit8 v1, v1, 0x1
                goto/16 :loop_start
            """)
            explanation = ("The loop body is too large for an 8-bit offset, so the back-edge uses goto/16. If you "
                           "write plain goto, smali widens it automatically when the label is out of range.")
        else:
            example = ex("""
                # in a huge generated method the target is more than 32767 code units away
                goto/32 :far_target
            """)
            explanation = ("Only goto/32 reaches targets beyond the 16-bit range. dexlib2 promotes goto and "
                           "goto/16 to goto/32 when needed.")
        out.append(make(
            ctx, name, "jump",
            syntax=f"{name} :label",
            operands=[opnd(":label", letters, bits, f"signed branch offset in 16-bit code units relative to the "
                                                    f"start of this instruction ({rng}); smali uses a label.")],
            summary=summary,
            description=(f"{name} always transfers control to the target instruction. Compilers use it to close "
                         f"loops and to jump over else-branches. {reach}"),
            technical=(enc(ctx, fmt) + f" Target = address of this instruction + {letters} (signed, in code units). "
                       f"{zero} The target must be the start of an instruction inside the same method. Cannot "
                       f"throw. When assembling, smali/dexlib2 automatically widens goto to goto/16 or goto/32 if "
                       f"the computed offset does not fit."),
            example=example,
            explanation=explanation,
            java="while (...) { ... }   // or: break / continue / the jump over an else-branch",
            kotlin="while (...) { ... }   // or: break / continue",
            registers="None.",
            mistakes=[
                "Jumping into the middle of a try range or between an invoke and its move-result, which breaks the "
                "verifier's assumptions.",
                "Hand-computing offsets: let smali compute them from labels.",
                ("Writing goto :self (offset 0) for a spin loop with goto or goto/16: smali assembles it, but the "
                 "spec forbids a zero offset for these forms, so the verifier rejects it; use goto/32."
                 if name != "goto/32" else
                 "Using goto/32 everywhere 'to be safe': it works but costs three code units per jump."),
            ],
            related=[n for n, *_ in data] + ["if-eqz", "if-eq", "packed-switch"],
            tags=["jump", "goto", "control-flow"],
        ))
    return out


# =================================================================================================================
# packed-switch, sparse-switch and the three payloads
# =================================================================================================================

PACKED_EXAMPLE = ex("""
    .method public static label(I)Ljava/lang/String;
        .locals 1
        packed-switch p0, :pswitch_data_0
        const-string v0, "other"
        return-object v0

        :pswitch_0
        const-string v0, "zero"
        return-object v0

        :pswitch_1
        const-string v0, "one"
        return-object v0

        :pswitch_2
        const-string v0, "two"
        return-object v0

        :pswitch_data_0
        .packed-switch 0x0
            :pswitch_0
            :pswitch_1
            :pswitch_2
        .end packed-switch
    .end method
""")

SPARSE_EXAMPLE = ex("""
    .method public static statusText(I)Ljava/lang/String;
        .locals 1
        sparse-switch p0, :sswitch_data_0
        const-string v0, "unknown"
        return-object v0

        :sswitch_0
        const-string v0, "OK"
        return-object v0

        :sswitch_1
        const-string v0, "Not Found"
        return-object v0

        :sswitch_2
        const-string v0, "Server Error"
        return-object v0

        :sswitch_data_0
        .sparse-switch
            0xc8 -> :sswitch_0
            0x194 -> :sswitch_1
            0x1f4 -> :sswitch_2
        .end sparse-switch
    .end method
""")


def gen_switches(ctx):
    out = []
    pk = ctx.payload_tables["packed-switch-payload"]
    sp = ctx.payload_tables["sparse-switch-payload"]
    out.append(make(
        ctx, "packed-switch", "switch",
        syntax="packed-switch vAA, :pswitch_data",
        operands=[opnd("vAA", "AA", 8, "register holding the int value to switch on, v0–v255."),
                  opnd(":pswitch_data", "BBBBBBBB", 32, "signed offset, in code units relative to this instruction, "
                                                       "of a packed-switch-payload; smali uses a label.")],
        summary="Jump through a table of consecutive int cases, or fall through if the value is out of range.",
        description=("packed-switch is the jump table for a switch whose case values are consecutive, such as "
                     "0, 1, 2, 3. The value selects an entry directly by its distance from the first key, so "
                     "dispatch takes constant time. Values outside the table fall through to the next instruction, "
                     "which acts as the default branch."),
        technical=(enc(ctx, "31t") + " The payload (ident 0x0100) holds size, first_key and size branch targets. If "
                   "first_key <= vAA < first_key + size, control goes to targets[vAA - first_key]; otherwise "
                   "execution continues with the next instruction. The targets are relative to the address of the "
                   "packed-switch instruction, not of the table. The payload must be 4-byte aligned and takes "
                   f"{pk['units']} code units. Cannot throw."),
        example=PACKED_EXAMPLE,
        explanation=("Values 0, 1 and 2 jump to their case labels via the table; any other value falls through to "
                     "the code right after packed-switch, which returns \"other\" (the default branch)."),
        java='switch (n) { case 0: return "zero"; case 1: return "one"; case 2: return "two"; default: return "other"; }',
        kotlin='return when (n) { 0 -> "zero"; 1 -> "one"; 2 -> "two"; else -> "other" }',
        registers="Reads vAA (v0–v255). Writes nothing.",
        mistakes=[
            "Pointing two switch instructions at the same payload label: dexlib2/smali refuses this; each switch "
            "needs its own table.",
            "Forgetting that the default case is the fall-through path directly after the switch instruction.",
            "Editing the .packed-switch first key without re-ordering the labels: entry i always belongs to the "
            "value first_key + i.",
        ],
        related=["sparse-switch", "packed-switch-payload", "if-eq", "goto"],
        tags=["switch", "branch", "payload", "control-flow"],
        refs_extra=[REF_PACKED],
    ))
    out.append(make(
        ctx, "sparse-switch", "switch",
        syntax="sparse-switch vAA, :sswitch_data",
        operands=[opnd("vAA", "AA", 8, "register holding the int value to switch on, v0–v255."),
                  opnd(":sswitch_data", "BBBBBBBB", 32, "signed offset, in code units relative to this instruction, "
                                                       "of a sparse-switch-payload; smali uses a label.")],
        summary="Jump using a sorted table of (key, target) pairs, or fall through if no key matches.",
        description=("sparse-switch handles a switch whose case values are far apart, such as 200, 404 and 500, "
                     "where a dense table would waste space. The runtime searches the sorted keys for the value. "
                     "Java string switches also end up here, after switching on hashCode()."),
        technical=(enc(ctx, "31t") + " The payload (ident 0x0200) holds size, keys sorted low-to-high, and one "
                   "target per key; targets are relative to the sparse-switch instruction. Without a match, "
                   "execution continues with the next instruction. Since the keys are sorted, implementations can "
                   f"binary-search them. The payload must be 4-byte aligned and takes {sp['units']} code units. "
                   "Cannot throw."),
        example=SPARSE_EXAMPLE,
        explanation=("200 (0xc8), 404 (0x194) and 500 (0x1f4) are too far apart for a packed table, so the compiler "
                     "used a sparse table of key -> label pairs; any other code falls through to \"unknown\"."),
        java='switch (code) { case 200: return "OK"; case 404: return "Not Found"; case 500: return "Server Error"; default: return "unknown"; }',
        kotlin='return when (code) { 200 -> "OK"; 404 -> "Not Found"; 500 -> "Server Error"; else -> "unknown" }',
        registers="Reads vAA (v0–v255). Writes nothing.",
        mistakes=[
            "Writing keys out of order in a hand-made .sparse-switch: the payload must be sorted low-to-high.",
            "Reusing one payload for two switch instructions, which smali rejects.",
            "Expecting a string switch to compare strings directly: compiled code switches on hashCode() and then "
            "calls equals().",
        ],
        related=["packed-switch", "sparse-switch-payload", "if-eq"],
        tags=["switch", "branch", "payload", "control-flow"],
        refs_extra=[REF_SPARSE],
    ))
    return out


def gen_payloads(ctx):
    out = []

    def payload(name, ident, syntax, fields_name, summary, description, technical, example, explanation, mistakes,
                related, tags, refs):
        t = ctx.payload_tables[name]
        operands = [{"name": f[0], "description": f"{f[1]}: {f[2]}"} for f in t["fields"]]
        return {
            "name": name, "opcode": ident, "category": "advanced", "format": name, "syntax": syntax,
            "operands": operands, "summary": summary, "description": description,
            "technical": technical + f" Total size: {t['units']} code units.",
            "example": example, "exampleExplanation": explanation, "javaEquivalent": None, "kotlinEquivalent": None,
            "registerUsage": "None. Payloads are data tables, not executable instructions, and touch no registers.",
            "minApi": 1, "dexVersion": "035",
            "compatibility": ("Part of the original instruction set: valid in every dex version (035 and later) on "
                              "all Android releases (API 1+)."),
            "commonMistakes": mistakes, "related": related, "references": [REF_BC] + refs, "tags": tags,
        }

    out.append(payload(
        "packed-switch-payload", 0x0100,
        ".packed-switch first_key\n    :case_label ...\n.end packed-switch",
        None,
        "Data table for packed-switch: a first key followed by consecutive branch targets.",
        ("This pseudo-instruction is not executed; it is the jump table that packed-switch reads. It stores the "
         "lowest case value and one branch target for each consecutive value after it. In smali you write it as "
         "a .packed-switch ... .end packed-switch block, usually at the end of the method."),
        ("Layout: ushort ident = 0x0100, ushort size, int first_key, int targets[size]. Each target is a signed "
         "code-unit offset relative to the address of the packed-switch instruction that uses the table, not to "
         "the table itself. The table must start on a 4-byte boundary (an even code-unit offset), so a nop may be "
         "inserted before it, and it must never be reached by normal execution."),
        PACKED_EXAMPLE,
        ("The block after :pswitch_data_0 is the payload. 0x0 is first_key and the three labels are the targets "
         "for the values 0, 1 and 2."),
        ["Placing the payload where execution can fall into it (for example before a return): it must be "
         "unreachable data.",
         "Computing targets relative to the table: they are relative to the switch instruction.",
         "Sharing one payload between two packed-switch instructions; smali requires one table per switch."],
        ["packed-switch", "sparse-switch-payload", "nop"],
        ["payload", "switch", "data", "pseudo-instruction"],
        [REF_PACKED],
    ))
    out.append(payload(
        "sparse-switch-payload", 0x0200,
        ".sparse-switch\n    key -> :case_label ...\n.end sparse-switch",
        None,
        "Data table for sparse-switch: sorted keys and a branch target for each key.",
        ("This pseudo-instruction holds the case table of a sparse-switch: every case key with its own branch "
         "target. The keys are kept sorted so that the runtime can search them quickly. In smali it is a "
         ".sparse-switch block of key -> :label lines."),
        ("Layout: ushort ident = 0x0200, ushort size, int keys[size] sorted low-to-high, int targets[size]. Each "
         "target is a signed code-unit offset relative to the sparse-switch instruction. The table must be 4-byte "
         "aligned and must not be reachable by normal execution."),
        SPARSE_EXAMPLE,
        ("The .sparse-switch block is the payload: three sorted keys (200, 404, 500), each mapped to the label of "
         "its case."),
        ["Listing keys in unsorted order or with duplicates.",
         "Treating it as a packed table: entries are explicit key/target pairs, not offsets from a first key.",
         "Letting execution fall into the table."],
        ["sparse-switch", "packed-switch-payload", "nop"],
        ["payload", "switch", "data", "pseudo-instruction"],
        [REF_SPARSE],
    ))
    out.append(payload(
        "fill-array-data-payload", 0x0300,
        ".array-data element_width\n    value ...\n.end array-data",
        None,
        "Data table for fill-array-data: element width, element count and the raw element bytes.",
        ("This pseudo-instruction holds the constant values that fill-array-data copies into a primitive array. It "
         "records how many bytes each element takes and how many elements there are, followed by the raw data. "
         "smali writes it as an .array-data block; dexlib2 calls it array-payload internally."),
        ("Layout: ushort ident = 0x0300, ushort element_width (bytes per element), uint size (number of elements), "
         "ubyte data[size * element_width], stored little-endian. smali accepts widths 1, 2, 4 and 8 (byte/boolean, "
         "short/char, int/float, long/double). The table must be 4-byte aligned and unreachable by normal "
         "execution; an odd total byte count is padded to whole code units."),
        ex("""
            .method public static weights()[S
                .locals 1
                const/4 v0, 0x3
                new-array v0, v0, [S
                fill-array-data v0, :array_0
                return-object v0

                :array_0
                .array-data 2
                    0xas
                    0x14s
                    0x1es
                .end array-data
            .end method
        """),
        ("The .array-data 2 block is a payload with element_width 2 (short) and three elements: 10, 20 and 30. The "
         "s suffix marks short literals."),
        ["Choosing an element width that doesn't match the array type, so the values are rejected or land in the "
         "wrong slots.",
         "Expecting it to work for String[] or other object arrays: only primitive arrays can be filled.",
         "Letting execution fall into the table."],
        ["fill-array-data", "new-array", "nop"],
        ["payload", "array", "data", "pseudo-instruction"],
        [REF_FILL],
    ))
    return out


# =================================================================================================================
# cmp*
# =================================================================================================================

def gen_cmps(ctx):
    out = []
    data = {
        "cmpl-float": ("float", "F", False, -1), "cmpg-float": ("float", "F", False, 1),
        "cmpl-double": ("double", "D", True, -1), "cmpg-double": ("double", "D", True, 1),
        "cmp-long": ("long", "J", True, None),
    }
    for name, (t, d, wide, nan) in data.items():
        src = "register pair" if wide else "register"
        operands = [opnd("vAA", "AA", 8, "destination register, v0–v255; receives -1, 0 or 1 as an int."),
                    opnd("vBB", "BB", 8, f"first source {src}, v0–v255."),
                    opnd("vCC", "CC", 8, f"second source {src}, v0–v255.")]
        if nan is None:
            summary = "Compare two longs: vAA = -1, 0 or 1 for vBB <, ==, > vCC."
            desc = ("cmp-long compares two 64-bit signed integers and writes -1, 0 or 1. Dalvik has no if-* test "
                    "for longs, so compilers emit cmp-long followed by an if-*z test on the result.")
            tech_nan = "There is no NaN case for integers."
            java = "int r = Long.compare(a, b);   // same sign; cmp-long yields exactly -1, 0 or 1"
            kotlin = "val r = a.compareTo(b)"
            example = ex("""
                .method public static isLater(JJ)Z
                    .locals 1
                    cmp-long v0, p0, p2
                    if-lez v0, :not_later
                    const/4 v0, 0x1
                    return v0
                    :not_later
                    const/4 v0, 0x0
                    return v0
                .end method
            """)
            explanation = ("Each long parameter takes two registers (p0/p1 and p2/p3). cmp-long writes 1 only when "
                           "a > b, so if-lez skips to the false branch for -1 and 0.")
            mistakes = ["Trying if-lt directly on long registers: if-* only works on 32-bit values; compare with "
                        "cmp-long first.",
                        "Naming the second half of a pair (p1 or p3) as an operand.",
                        "Assuming cmp-long's result register is wide: the result is a plain int in a single "
                        "register."]
            tags = ["comparison", "long", "wide"]
        else:
            bias = "lt" if nan == -1 else "gt"
            summary = (f"Compare two {t}s: vAA = -1, 0 or 1; NaN gives {nan} ({bias} bias).")
            desc = (f"{name} compares two {t} values and writes -1 if the first is smaller, 0 if they are equal "
                    f"and 1 if it is larger. If either operand is NaN the result is {nan}. That bias lets "
                    f"compilers make every comparison involving NaN come out false.")
            tech_nan = (f"If either operand is NaN the result is {nan} (\"{bias} bias\"). -0.0 and +0.0 compare "
                        f"equal. The spec recommends cmpg for testing x < y: the result -1 then means the test is "
                        f"true, and NaN gives 1, which reads as false. Likewise cmpl suits x > y.")
            if nan == -1:
                java = f"int r = (a > b) ? 1 : (a == b) ? 0 : -1;   // NaN -> -1; javac uses this for a > b, a >= b"
                kotlin = "// emitted for a > b and a >= b on " + t + " values"
                example = ex(f"""
                    .method public static isGreater({d}{d})Z
                        .locals 1
                        {name} v0, p0, {'p2' if wide else 'p1'}
                        if-lez v0, :not_greater
                        const/4 v0, 0x1
                        return v0
                        :not_greater
                        const/4 v0, 0x0
                        return v0
                    .end method
                """)
                explanation = (f"For a > b, {name} gives 1 only when a is really greater; NaN gives -1, so "
                               f"if-lez takes the false branch, just as Java requires. "
                               + ("Each double occupies a register pair (p0/p1 and p2/p3)." if wide else ""))
            else:
                java = f"int r = (a < b) ? -1 : (a == b) ? 0 : 1;   // NaN -> 1; javac uses this for a < b, a <= b"
                kotlin = "// emitted for a < b and a <= b on " + t + " values"
                example = ex(f"""
                    .method public static isLess({d}{d})Z
                        .locals 1
                        {name} v0, p0, {'p2' if wide else 'p1'}
                        if-gez v0, :not_less
                        const/4 v0, 0x1
                        return v0
                        :not_less
                        const/4 v0, 0x0
                        return v0
                    .end method
                """)
                explanation = (f"For a < b, {name} gives -1 only when a is really smaller; NaN gives 1, so if-gez "
                               f"takes the false branch. "
                               + ("Each double occupies a register pair (p0/p1 and p2/p3)." if wide else ""))
            other = name.replace("cmpl", "cmpX").replace("cmpg", "cmpl").replace("cmpX", "cmpg")
            mistakes = [f"Swapping cmpl and cmpg when patching: with NaN input the condition flips. Use {other} only "
                        f"if you also invert the test.",
                        "Comparing floats with if-eq/if-lt on the raw bits: that is an integer comparison and is "
                        "wrong for negative values, NaN and -0.0.",
                        "Expecting Float.compare/Double.compare semantics: those order NaN above everything and "
                        "-0.0 below 0.0, which cmpl/cmpg do not."]
            if wide:
                mistakes.append("Naming the second half of a double pair as an operand.")
            tags = ["comparison", t, "nan"] + (["wide"] if wide else [])
        tech = (enc(ctx, "23x") + f" vAA = 0 if vBB == vCC, 1 if vBB > vCC, -1 if vBB < vCC. "
                + ("vBB and vCC each name the first register of a pair. " if wide else "")
                + tech_nan + " Cannot throw.")
        related = ["cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long", "if-ltz", "if-gez",
                   "if-lez", "if-gtz"]
        out.append(make(
            ctx, name, "comparison", syntax=f"{name} vAA, vBB, vCC", operands=operands, summary=summary,
            description=desc, technical=tech, example=example, explanation=explanation, java=java, kotlin=kotlin,
            registers=(f"Writes vAA (single register, int result); reads {'the pairs vBB/vBB+1 and vCC/vCC+1' if wide else 'vBB and vCC'}. "
                       "All in v0–v255."),
            mistakes=mistakes[:4], related=related, tags=tags, refs_extra=[REF_MATH] if nan is not None else []))
    return out


# =================================================================================================================
# if-test / if-testz
# =================================================================================================================

IF_TESTS = {
    "eq": ("==", "equal to", "!="), "ne": ("!=", "not equal to", "=="), "lt": ("<", "less than", ">="),
    "ge": (">=", "greater than or equal to", "<"), "gt": (">", "greater than", "<="),
    "le": ("<=", "less than or equal to", ">"),
}
INVERSE = {"eq": "ne", "ne": "eq", "lt": "ge", "ge": "lt", "gt": "le", "le": "gt"}


def gen_ifs(ctx):
    out = []
    for t, (sym, words, inv_sym) in IF_TESTS.items():
        name = f"if-{t}"
        refs_ok = t in ("eq", "ne")
        operands = [opnd("vA", "A", 4, "first register to compare, v0–v15."),
                    opnd("vB", "B", 4, "second register to compare, v0–v15."),
                    opnd(":label", "CCCC", 16, "signed branch offset in code units relative to this instruction "
                                               "(-32768..32767, never 0); smali uses a label.")]
        desc = (f"{name} jumps to the label if the value in vA is {words} the value in vB; otherwise execution "
                f"continues with the next instruction. "
                + ("It works on ints and also on two object references, where it checks identity (the same "
                   "object), not equals(). " if refs_ok else
                   "The comparison is a signed 32-bit integer comparison. ")
                + f"Compilers usually emit the inverse test to skip over a block: if (a {sym} b) {{...}} becomes "
                  f"if-{INVERSE[t]} a, b, :skip.")
        tech = (enc(ctx, "22t") + f" Branches when vA {sym} vB. The registers are compared as 32-bit values: "
                + ("ints (and the narrower int types) or two references. " if refs_ok else
                   "signed int comparison only; references and floats are not allowed. ")
                + "Long, float and double values must be compared with cmp-long/cmpl-*/cmpg-* followed by an "
                  "if-*z test. The offset is in code units from the start of this instruction and must not be 0. "
                  "Both register fields are 4 bits. Cannot throw.")
        example = ex(f"""
            .method public static check(II)Z
                .locals 1
                {name} p0, p1, :yes
                const/4 v0, 0x0
                return v0
                :yes
                const/4 v0, 0x1
                return v0
            .end method
        """)
        mistakes = [
            f"Forgetting that javac inverts conditions: the if-{INVERSE[t]} you see in decompiled code usually "
            f"guards the body of if (a {sym} b).",
            "Using it on long/float/double registers: compare with cmp-long, cmpl-* or cmpg-* first.",
            "Using registers above v15: move them into low registers first or use cmp + if-*z.",
        ]
        if refs_ok:
            mistakes.insert(1, f"Using {name} to compare strings or boxed numbers by value: it compares references; "
                               f"call equals() instead.")
            mistakes = mistakes[:4]
        out.append(make(
            ctx, name, "branch", syntax=f"{name} vA, vB, :label", operands=operands,
            summary=f"Branch to the label if vA {sym} vB (32-bit compare).",
            description=desc, technical=tech, example=example,
            explanation=(f"If p0 {sym} p1, the branch goes to :yes and the method returns true; otherwise it falls "
                         f"through and returns false."),
            java=f"if (a {sym} b) {{ ... }}", kotlin=f"if (a {sym} b) {{ ... }}"
            + (f"   // a {'===' if t == 'eq' else '!=='} b for references" if refs_ok else ""),
            registers="Reads vA and vB (both v0–v15). Writes nothing. " + P_REG_NOTE,
            mistakes=mistakes,
            related=[f"if-{INVERSE[t]}", f"if-{t}z"] + [f"if-{x}" for x in IF_TESTS if x != t] + ["cmp-long"],
            tags=["branch", "condition", "control-flow"] + (["object"] if refs_ok else []),
        ))
    for t, (sym, words, inv_sym) in IF_TESTS.items():
        name = f"if-{t}z"
        refs_ok = t in ("eq", "ne")
        operands = [opnd("vAA", "AA", 8, "register to compare with zero, v0–v255."),
                    opnd(":label", "BBBB", 16, "signed branch offset in code units relative to this instruction "
                                               "(-32768..32767, never 0); smali uses a label.")]
        meaning = {"eq": "is zero, false or null", "ne": "is non-zero, true or a non-null reference",
                   "lt": "is negative", "ge": "is zero or positive", "gt": "is positive",
                   "le": "is zero or negative"}[t]
        desc = (f"{name} jumps to the label if the value in vAA {meaning}. "
                + ("Because null, false and 0 share the same representation, this one instruction covers null "
                   "checks, boolean tests and comparisons with 0. " if refs_ok else
                   "It is the usual partner of cmp-long, cmpl-* and cmpg-*, whose -1/0/1 result it tests. ")
                + "If the condition is false, execution falls through.")
        tech = (enc(ctx, "21t") + f" Branches when vAA {sym} 0. "
                + ("vAA may hold an int-like value or a reference (null compares equal to 0). " if refs_ok else
                   "vAA must hold an int-like value (signed comparison). ")
                + "The offset is in code units from the start of this instruction and must not be 0. "
                  "Cannot throw.")
        if refs_ok:
            example = ex(f"""
                .method public static {'isNull' if t == 'eq' else 'isPresent'}(Ljava/lang/Object;)Z
                    .locals 1
                    {name} p0, :yes
                    const/4 v0, 0x0
                    return v0
                    :yes
                    const/4 v0, 0x1
                    return v0
                .end method
            """)
            explanation = (f"{name} on a reference tests for {'null' if t == 'eq' else 'non-null'}: the method "
                           f"returns true when the argument {'is' if t == 'eq' else 'is not'} null.")
            java = (f"if (obj {sym} null) {{ ... }}   // also: if (x {sym} 0), "
                    + ("if (!flag)" if t == "eq" else "if (flag)"))
            kotlin = (f"if (obj {sym} null) {{ ... }}   // also: if (x {sym} 0), "
                      + ("if (!flag)" if t == "eq" else "if (flag)"))
        else:
            example = ex(f"""
                .method public static check(I)Z
                    .locals 1
                    {name} p0, :yes
                    const/4 v0, 0x0
                    return v0
                    :yes
                    const/4 v0, 0x1
                    return v0
                .end method
            """)
            explanation = f"The method returns true when p0 {sym} 0 and false otherwise."
            java = f"if (x {sym} 0) {{ ... }}"
            kotlin = f"if (x {sym} 0) {{ ... }}"
        mistakes = [
            f"Misreading inverted logic: compiled code typically uses if-{INVERSE[t]}z to skip the block of "
            f"if (x {sym} 0).",
            ("Patching a boolean check by swapping if-eqz/if-nez without considering every path that reaches "
             "the label." if refs_ok else
             "Testing a long, float or double register directly: compare with cmp-long/cmpl-*/cmpg-* first."),
            ("Treating a non-zero int other than 1 as 'not true' in boolean logic: if-nez accepts any non-zero "
             "value." if t == "ne" else
             "Using it on a long register pair: only one half would be tested, and the verifier rejects it."),
        ]
        out.append(make(
            ctx, name, "branch", syntax=f"{name} vAA, :label", operands=operands,
            summary=f"Branch to the label if vAA {sym} 0" + (" (also null / false checks)." if refs_ok else "."),
            description=desc, technical=tech, example=example, explanation=explanation, java=java, kotlin=kotlin,
            registers="Reads vAA (v0–v255). Writes nothing.",
            mistakes=mistakes,
            related=[f"if-{INVERSE[t]}z", f"if-{t}"] + [f"if-{x}z" for x in IF_TESTS if x != t]
            + (["instance-of"] if refs_ok else ["cmp-long", "cmpg-float"]),
            tags=["branch", "condition", "zero", "control-flow"] + (["null-check", "object"] if refs_ok else []),
        ))
    return out


# =================================================================================================================
# aget/aput, iget/iput, sget/sput
# =================================================================================================================

VT = {
    "": dict(jt="int", kt="Int", d="I", what="32-bit int or float", fld="count", arr="[I", kotlin_arr="IntArray",
             tags=["int", "float"]),
    "-wide": dict(jt="long", kt="Long", d="J", what="64-bit long or double", fld="timestamp", arr="[J",
                  kotlin_arr="LongArray", tags=["wide", "long", "double"]),
    "-object": dict(jt="String", kt="String?", d="Ljava/lang/String;", what="object reference", fld="name",
                    arr="[Ljava/lang/String;", kotlin_arr="Array<String?>", tags=["object"]),
    "-boolean": dict(jt="boolean", kt="Boolean", d="Z", what="boolean", fld="enabled", arr="[Z",
                     kotlin_arr="BooleanArray", tags=["boolean"]),
    "-byte": dict(jt="byte", kt="Byte", d="B", what="byte", fld="flags", arr="[B", kotlin_arr="ByteArray",
                  tags=["byte"]),
    "-char": dict(jt="char", kt="Char", d="C", what="char", fld="initial", arr="[C", kotlin_arr="CharArray",
                  tags=["char"]),
    "-short": dict(jt="short", kt="Short", d="S", what="short", fld="port", arr="[S", kotlin_arr="ShortArray",
                   tags=["short"]),
}


ARR_PHRASE = {"": "an int[] or float[] array", "-wide": "a long[] or double[] array",
              "-object": "an array of references (objects or nested arrays)", "-boolean": "a boolean[] array",
              "-byte": "a byte[] array", "-char": "a char[] array", "-short": "a short[] array"}
FIELD_TYPE = {"": "int or float", "-wide": "long or double", "-object": "reference (object or array)",
              "-boolean": "boolean", "-byte": "byte", "-char": "char", "-short": "short"}


def load_note(sfx):
    return {"": "The 32 bits are copied unchanged; the instruction does not care whether they are an int or a "
                "float.",
            "-wide": "Both halves of the 64-bit value are transferred together.",
            "-object": "Only the reference is copied.",
            "-boolean": "A boolean is loaded as the int 0 or 1.",
            "-byte": "The byte is sign-extended to a 32-bit int when loaded (0xff becomes -1).",
            "-char": "The char is zero-extended to a 32-bit int when loaded (0xffff becomes 65535).",
            "-short": "The short is sign-extended to a 32-bit int when loaded."}[sfx]


def store_note(sfx):
    return {"": "The 32 bits are stored unchanged.",
            "-wide": "Both halves of the pair are stored as one 64-bit value.",
            "-object": "Only the reference is stored.",
            "-boolean": "Compilers keep booleans as 0 or 1 in registers.",
            "-byte": "Only the low 8 bits are stored; compilers narrow the int first with int-to-byte.",
            "-char": "Only the low 16 bits are stored; compilers narrow the int first with int-to-char.",
            "-short": "Only the low 16 bits are stored; compilers narrow the int first with int-to-short."}[sfx]


def accepted(sfx, what):
    return {"": f"int or float {what}s",
            "-wide": f"long or double {what}s",
            "-object": f"{what}s of any reference type (objects or arrays)",
            "-boolean": f"boolean {what}s", "-byte": f"byte {what}s", "-char": f"char {what}s",
            "-short": f"short {what}s"}[sfx]


def gen_arrays(ctx):
    out = []
    for kind in ("aget", "aput"):
        for sfx, v in VT.items():
            name = kind + sfx
            wide = sfx == "-wide"
            get = kind == "aget"
            val_desc = ("destination" if get else "source") + (" register pair" if wide else " register")
            operands = [opnd("vAA", "AA", 8, f"{val_desc} for the element, v0–v255."),
                        opnd("vBB", "BB", 8, "register holding the array reference, v0–v255."),
                        opnd("vCC", "CC", 8, "register holding the int index, v0–v255.")]
            arrj = v["arr"]
            exc = ("Throws NullPointerException if the array is null and ArrayIndexOutOfBoundsException if the "
                   "index is negative or >= length.")
            if name == "aput-object":
                exc += (" Also throws ArrayStoreException if the value's runtime type cannot be stored in the "
                        "array (for example an Integer into a String[] seen through an Object[] reference).")
            if get:
                summary = f"Read {an(v['what'])} array element: vAA = vBB[vCC]."
                desc = (f"{name} reads one element from {ARR_PHRASE[sfx]} and stores it in a register. "
                        f"{load_note(sfx)} It is what reading arr[i] compiles to.")
                tech = (enc(ctx, "23x") + f" vAA{'/vAA+1' if wide else ''} = vBB[vCC]. The array must have "
                        f"{'int or float' if sfx == '' else 'long or double' if wide else 'reference' if sfx == '-object' else v['jt']} "
                        f"elements. {load_note(sfx)} {exc}")
                if wide:
                    sig, locals_, code, ret = (f"({arrj})J", 3, ["const/4 v2, 0x0", f"{name} v0, p0, v2"],
                                               "return-wide v0")
                else:
                    sig, locals_, code, ret = (f"({arrj}){v['d']}", 1, ["const/4 v0, 0x0", f"{name} v0, p0, v0"],
                                               f"return{'-object' if sfx == '-object' else ''} v0")
                example = (f".method public static first{sig}\n    .locals {locals_}\n"
                           + "".join(f"    {c}\n" for c in code) + f"    {ret}\n.end method")
                explanation = (f"Index 0 is loaded into a register and {name} reads element 0 of the array in p0. "
                               + ("The long element lands in the pair v0/v1; the index lives in v2 so it doesn't "
                                  "overlap the pair." if wide else
                                  "Reusing v0 for both the index and the result is legal: the index is read before "
                                  "the result is written."))
                java = f"{v['jt']} x = values[i];"
                kotlin = f"val x = values[i]"
                registers = (f"Writes vAA{' and vAA+1' if wide else ''}; reads vBB (array) and vCC (index). "
                             f"All in v0–v255.")
            else:
                summary = f"Write {an(v['what'])} array element: vBB[vCC] = vAA."
                desc = (f"{name} stores a register value into one element of {ARR_PHRASE[sfx]}. "
                        f"{store_note(sfx)} It is what an assignment arr[i] = x compiles to.")
                tech = (enc(ctx, "23x") + f" vBB[vCC] = vAA{'/vAA+1' if wide else ''}. Note that the value register "
                        f"comes first even though it is a source. {store_note(sfx)} {exc}")
                if wide:
                    sig, code = f"({arrj}J)V", [f"{name} p1, p0, v0"]
                    explanation = ("p0 is the array and the long value arrives in the pair p1/p2; aput-wide names "
                                   "only p1. v0 holds index 0.")
                else:
                    sig, code = f"({arrj}{v['d']})V", [f"{name} p1, p0, v0"]
                    explanation = (f"p0 is the array, p1 the value and v0 the index 0, so {name} performs "
                                   f"values[0] = value.")
                example = (f".method public static setFirst{sig}\n    .locals 1\n    const/4 v0, 0x0\n"
                           + "".join(f"    {c}\n" for c in code) + "    return-void\n.end method")
                java = f"values[i] = x;"
                kotlin = f"values[i] = x"
                registers = (f"Reads vAA{' and vAA+1' if wide else ''} (value), vBB (array) and vCC (index); writes "
                             f"only the array element in memory. All registers in v0–v255.")
            mistakes = []
            if sfx == "":
                mistakes.append(f"Using {name} on a boolean[], byte[], char[] or short[]: those need "
                                f"{kind}-boolean/-byte/-char/-short.")
            elif sfx == "-wide":
                mistakes.append("Naming the second half of the value pair, or letting the index register overlap "
                                "the pair.")
            elif sfx == "-object":
                mistakes.append(f"Using plain {kind} for an object array: references need {kind}-object.")
            else:
                mistakes.append(f"Using plain {kind} for a {v['jt']}[]: the element type must match the "
                                f"{kind}{sfx} variant.")
            mistakes.append("Swapping operand order: the value register comes first, then the array, then the "
                            "index.")
            mistakes.append("Not guarding the index: out-of-range values throw ArrayIndexOutOfBoundsException.")
            if name == "aput-object":
                mistakes.append("Storing into an array whose runtime component type is narrower than its static "
                                "type, which throws ArrayStoreException.")
            if sfx in ("-byte", "-char", "-short") and get:
                mistakes.append("Forgetting sign extension: aget-byte of 0xff gives -1; mask with and-int/lit16 "
                                "0xff if you need 0..255." if sfx == "-byte" else
                                "Mixing up char (zero-extended) and short (sign-extended) when reading 16-bit "
                                "data.")
            other = "aput" if get else "aget"
            related = [other + sfx] + [kind + s for s in VT if s != sfx][:3] + ["array-length", "new-array"]
            out.append(make(
                ctx, name, "array", syntax=f"{name} vAA, vBB, vCC", operands=operands, summary=summary,
                description=desc, technical=tech, example=example, explanation=explanation, java=java,
                kotlin=kotlin, registers=registers, mistakes=mistakes[:4], related=related,
                tags=["array", "load" if get else "store"] + v["tags"]))
    return out


def gen_instance_fields(ctx):
    out = []
    for kind in ("iget", "iput"):
        for sfx, v in VT.items():
            name = kind + sfx
            wide = sfx == "-wide"
            get = kind == "iget"
            fref = f"Lcom/acme/demo/Item;->{v['fld']}:{v['d']}"
            operands = [opnd("vA", "A", 4, ("destination" if get else "source") + (" register pair" if wide else
                                                                                   " register") + ", v0–v15."),
                        opnd("vB", "B", 4, "register holding the object whose field is accessed, v0–v15."),
                        opnd("Lclass;->name:Type", "CCCC", 16, "field_ids index (field@CCCC) of an instance field.")]
            exc = ("Throws NullPointerException if the object is null. Field resolution can throw NoSuchFieldError "
                   "or IllegalAccessError, and IncompatibleClassChangeError if the field is static.")
            if get:
                summary = f"Read {an(FIELD_TYPE[sfx])} instance field of the object in vB into vA."
                desc = (f"{name} reads {an(FIELD_TYPE[sfx])} instance field "
                        f"from an object and stores it in a register. {load_note(sfx)} Every this.field or "
                        f"obj.field read in source code becomes an iget variant.")
                tech = (enc(ctx, "22c") + f" vA{'/vA+1' if wide else ''} = vB.field. The variant must match the "
                        f"field's declared type ({accepted(sfx, 'field')}). {load_note(sfx)} {exc}")
                code = [f"iget{sfx} v0, p0, {fref}", f"return{'-wide' if wide else '-object' if sfx == '-object' else ''} v0"]
                example = (f".method public get{v['fld'].capitalize()}(){v['d']}\n    .locals {2 if wide else 1}\n"
                           + "".join(f"    {c}\n" for c in code) + ".end method")
                explanation = (f"p0 is this; {name} reads the {v['fld']} field into "
                               f"{'the pair v0/v1' if wide else 'v0'}, which the getter returns.")
                java = f"{v['jt']} x = item.{v['fld']};"
                kotlin = f"val x = item.{v['fld']}   // a direct field read (e.g. a @JvmField or a backing field)"
                registers = (f"Writes vA{' and vA+1' if wide else ''}; reads vB (object). Both fields are 4 bits "
                             f"(v0–v15). " + P_REG_NOTE)
            else:
                summary = f"Write vA into {an(FIELD_TYPE[sfx])} instance field of the object in vB."
                desc = (f"{name} stores a register value into {an(FIELD_TYPE[sfx])} instance field. "
                        f"{store_note(sfx)} Every "
                        f"this.field = x assignment in source code becomes an iput variant.")
                tech = (enc(ctx, "22c") + f" vB.field = vA{'/vA+1' if wide else ''}. The variant must match the "
                        f"field's declared type ({accepted(sfx, 'field')}). The value register comes first even "
                        f"though it is read. {store_note(sfx)} {exc} Writing a final field outside the class's own "
                        f"constructor is rejected by the verifier.")
                example = (f".method public set{v['fld'].capitalize()}({v['d']})V\n    .locals 0\n"
                           f"    iput{sfx} p1, p0, {fref}\n    return-void\n.end method")
                explanation = (f"p0 is this and the new value arrives in {'the pair p1/p2' if wide else 'p1'}; "
                               f"{name} stores it in the {v['fld']} field.")
                java = f"item.{v['fld']} = x;"
                kotlin = f"item.{v['fld']} = x   // a direct field write (e.g. a @JvmField or a backing field)"
                registers = (f"Reads vA{' and vA+1' if wide else ''} (value) and vB (object); writes the field in "
                             f"memory. Both fields are 4 bits (v0–v15). " + P_REG_NOTE)
            mistakes = []
            if sfx == "":
                mistakes.append(f"Using {name} for a boolean, byte, char or short field: the variant must match the "
                                f"field type exactly.")
            elif sfx == "-object":
                mistakes.append(f"Using plain {kind} for a reference field: references need {kind}-object.")
            elif sfx == "-wide":
                mistakes.append(f"Using plain {kind} for a long/double field, or reserving only one register for "
                                f"the value.")
            else:
                mistakes.append(f"Using plain {kind} for a {v['jt']} field: it must be {name}.")
            mistakes.append(f"Using {kind} on a static field: static fields need s{kind[1:]}{sfx}.")
            mistakes.append("Changing the field's type in the reference (:I vs :Z) without changing the class: the "
                            "field won't resolve (NoSuchFieldError).")
            if get:
                mistakes.append("Using registers above v15; move the object into a low register first.")
            else:
                mistakes.append("Patching a field write into a final field from outside the constructor; the "
                                "verifier rejects it.")
            other = "iput" if get else "iget"
            related = [other + sfx] + [kind + s for s in VT if s != sfx][:3] + [("sget" if get else "sput") + sfx]
            out.append(make(
                ctx, name, "field", syntax=f"{name} vA, vB, Lclass;->name:Type", operands=operands, summary=summary,
                description=desc, technical=tech, example=example, explanation=explanation, java=java,
                kotlin=kotlin, registers=registers, mistakes=mistakes[:4], related=related,
                tags=["field", "instance", "load" if get else "store"] + v["tags"], refs_extra=[REF_DEX_FIELD]))
    return out


def gen_static_fields(ctx):
    out = []
    names = {"": "retryCount", "-wide": "startTime", "-object": "baseUrl", "-boolean": "debug", "-byte": "level",
             "-char": "separator", "-short": "port"}
    for kind in ("sget", "sput"):
        for sfx, v in VT.items():
            name = kind + sfx
            wide = sfx == "-wide"
            get = kind == "sget"
            fld = names[sfx]
            fref = f"Lcom/acme/demo/Config;->{fld}:{v['d']}"
            operands = [opnd("vAA", "AA", 8, ("destination" if get else "source") + (" register pair" if wide else
                                                                                     " register") + ", v0–v255."),
                        opnd("Lclass;->name:Type", "BBBB", 16, "field_ids index (field@BBBB) of a static field.")]
            exc = ("The first access initialises the declaring class, so the class's static initializer may run "
                   "here and an exception it throws surfaces as ExceptionInInitializerError. Resolution can throw "
                   "NoSuchFieldError or IllegalAccessError, and IncompatibleClassChangeError if the field is not "
                   "static.")
            if get:
                summary = f"Read {an(FIELD_TYPE[sfx])} static field into vAA."
                desc = (f"{name} reads {an(FIELD_TYPE[sfx])} static (class-level) field into a register. No object is "
                        f"involved. {load_note(sfx)} Reading ClassName.FIELD compiles to an sget variant, unless the "
                        f"field is a compile-time constant that the compiler inlined.")
                tech = (enc(ctx, "21c") + f" vAA{'/vAA+1' if wide else ''} = Class.field. The variant must match "
                        f"the field's declared type ({accepted(sfx, 'field')}). {load_note(sfx)} {exc}")
                example = (f".method public static get{fld[0].upper() + fld[1:]}(){v['d']}\n"
                           f"    .locals {2 if wide else 1}\n    {name} v0, {fref}\n"
                           f"    return{'-wide' if wide else '-object' if sfx == '-object' else ''} v0\n.end method")
                explanation = (f"{name} reads Config.{fld} into {'the pair v0/v1' if wide else 'v0'}; on the first "
                               f"access this may trigger Config's static initializer.")
                java = f"{v['jt']} x = Config.{fld};"
                kotlin = f"val x = Config.{fld}   // e.g. a @JvmField in a companion/object, or a Java static field"
                registers = f"Writes vAA{' and vAA+1' if wide else ''} (v0–v255)."
            else:
                summary = f"Write vAA into {an(FIELD_TYPE[sfx])} static field."
                desc = (f"{name} stores a register value into {an(FIELD_TYPE[sfx])} static field. "
                        f"{store_note(sfx)} Assignments to "
                        f"ClassName.FIELD, including those in a class's <clinit>, compile to sput variants.")
                tech = (enc(ctx, "21c") + f" Class.field = vAA{'/vAA+1' if wide else ''}. The variant must match the "
                        f"field's declared type ({accepted(sfx, 'field')}). {store_note(sfx)} {exc} Writing a "
                        f"static final field outside the class's <clinit> is rejected by the verifier.")
                example = (f".method public static set{fld[0].upper() + fld[1:]}({v['d']})V\n    .locals 0\n"
                           f"    {name} p0, {fref}\n    return-void\n.end method")
                explanation = (f"The new value arrives in {'the pair p0/p1' if wide else 'p0'} and {name} stores it in "
                               f"Config.{fld}.")
                java = f"Config.{fld} = x;"
                kotlin = f"Config.{fld} = x"
                registers = f"Reads vAA{' and vAA+1' if wide else ''} (v0–v255); writes the static field in memory."
            mistakes = []
            if sfx == "":
                mistakes.append(f"Using {name} for a boolean/byte/char/short field: use the matching suffix.")
            elif sfx == "-object":
                mistakes.append(f"Using plain {kind} for a reference field: references need {name}.")
            elif sfx == "-wide":
                mistakes.append("Forgetting that the value occupies a register pair.")
            else:
                mistakes.append(f"Using plain {kind} for a {v['jt']} field: it must be {name}.")
            mistakes.append(f"Using {kind} on an instance field (needs i{kind[1:]}{sfx} with an object register).")
            if get:
                mistakes.append("Looking for an sget of a constant such as static final int MAX = 10: compilers "
                                "inline such constants, so patching the field definition does not change the "
                                "already-inlined uses.")
            else:
                mistakes.append("Writing a static final field from outside <clinit>; the verifier rejects it.")
            mistakes.append("Forgetting that the first access runs the class's static initializer, which can have "
                            "side effects or throw.")
            other = "sput" if get else "sget"
            related = [other + sfx] + [kind + s for s in VT if s != sfx][:3] + [("iget" if get else "iput") + sfx]
            out.append(make(
                ctx, name, "field", syntax=f"{name} vAA, Lclass;->name:Type", operands=operands, summary=summary,
                description=desc, technical=tech, example=example, explanation=explanation, java=java,
                kotlin=kotlin, registers=registers, mistakes=mistakes[:4], related=related,
                tags=["field", "static", "load" if get else "store"] + v["tags"], refs_extra=[REF_DEX_FIELD]))
    return out


# =================================================================================================================
# invoke-kind (+ /range)
# =================================================================================================================

INVOKES = {
    "virtual": dict(
        summary="Call a virtual method, dispatched on the receiver's runtime class",
        desc=("invoke-virtual calls an ordinary instance method: one that is not static, private or a constructor. "
              "The method that actually runs is chosen at run time from the receiver's class, so overrides in "
              "subclasses are honoured."),
        tech=("The first register is the receiver (this). Dispatch uses the receiver's runtime class's vtable. "
              "Throws NullPointerException for a null receiver; resolution can throw NoSuchMethodError, "
              "IncompatibleClassChangeError (static or interface method) or IllegalAccessError; AbstractMethodError "
              "if the selected method is abstract."),
        example=ex("""
            .method public static describe(Ljava/lang/Object;)Ljava/lang/String;
                .locals 1
                invoke-virtual {p0}, Ljava/lang/Object;->toString()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method
        """),
        explanation=("p0 is the receiver; the call is dispatched to the most specific toString() override, and its "
                     "String result is picked up with move-result-object."),
        java="String s = obj.toString();", kotlin="val s = obj.toString()",
        mistakes=["Using invoke-virtual for a private method or constructor (use invoke-direct) or for an interface "
                  "method (use invoke-interface).",
                  "Forgetting the receiver register: the first register is always this."],
        related=["invoke-super", "invoke-interface", "invoke-direct"],
        range_example=ex("""
            # canvas.drawLine(startX, startY, stopX, stopY, paint): this + 4 floats + paint = 6 words
            invoke-virtual/range {v0 .. v5}, Landroid/graphics/Canvas;->drawLine(FFFFLandroid/graphics/Paint;)V
        """),
        range_explanation=("Six argument words (receiver, four floats and the Paint) exceed the five-register limit, "
                           "so the values are placed in v0..v5 and passed as a range."),
    ),
    "super": dict(
        summary="Call the superclass's (or a default interface) implementation of a method",
        desc=("invoke-super calls the implementation in the superclass instead of dispatching on the receiver's "
              "own class, exactly like super.method() in Java. Android code uses it constantly in lifecycle methods "
              "such as onCreate."),
        tech=("The receiver must be this. With a class method_id, the closest superclass implementation of the "
              "method is invoked. Since dex 037 a method_id that names an interface method invokes the most "
              "specific, non-overridden default method of that interface (Iface.super.m() in Java); before 037 "
              "that was illegal. Same exceptions as invoke-virtual."),
        example=ex("""
            .method protected onCreate(Landroid/os/Bundle;)V
                .locals 0
                invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
                return-void
            .end method
        """),
        explanation="The overriding onCreate first calls Activity.onCreate with the same Bundle, as Android requires.",
        java="super.onCreate(savedInstanceState);", kotlin="super.onCreate(savedInstanceState)",
        mistakes=["Changing the class in the method reference to the current class: that does not call the "
                  "parent implementation the way super does.",
                  "Using invoke-super with an interface method in a dex older than 037; it needs Android 7.0 (API 24)+."],
        related=["invoke-virtual", "invoke-direct"],
        compat=("Calling an interface (default) method through invoke-super needs dex 037 or later; the dex format "
                "page says 037 support was added in Android 7.0 (API 24)."),
        range_example=ex("""
            .method protected onLayout(ZIIII)V
                .locals 0
                invoke-super/range {p0 .. p5}, Landroid/view/View;->onLayout(ZIIII)V
                return-void
            .end method
        """),
        range_explanation=("this plus five arguments makes six words, so the call uses the range p0..p5. Parameter "
                           "registers are consecutive, which makes /range a natural fit."),
    ),
    "direct": dict(
        summary="Call a private instance method or a constructor without virtual dispatch",
        desc=("invoke-direct calls instance methods that can never be overridden: constructors (<init>) and "
              "private methods. Every new expression ends up here, since the object from new-instance is "
              "initialised by an invoke-direct of <init>."),
        tech=("The first register is the receiver. No dispatch takes place: the method named by the reference is "
              "executed. Calling <init> on an uninitialised reference (from new-instance, or this in a constructor) "
              "marks it as initialised for the verifier. Throws NullPointerException for a null receiver; "
              "resolution errors as for invoke-virtual."),
        example=ex("""
            new-instance v0, Ljava/util/ArrayList;
            invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
        """),
        explanation="The allocated ArrayList is constructed by calling its no-argument constructor with invoke-direct.",
        java="List<String> list = new ArrayList<>();", kotlin="val list = ArrayList<String>()",
        mistakes=["Calling a constructor with invoke-virtual: constructors must use invoke-direct.",
                  "Calling <init> twice on the same object, or using the object before calling <init>."],
        related=["new-instance", "invoke-virtual", "invoke-static"],
        range_example=ex("""
            # new Rect(left, top, right, bottom); left..bottom are already in v17..v20
            new-instance v16, Landroid/graphics/Rect;
            invoke-direct/range {v16 .. v20}, Landroid/graphics/Rect;-><init>(IIII)V
        """),
        range_explanation=("The receiver and four ints occupy v16..v20, above the 4-bit reach of the non-range "
                           "form, so /range is required even though there are only five argument words."),
    ),
    "static": dict(
        summary="Call a static method (no receiver)",
        desc=("invoke-static calls a class-level method that has no this receiver, such as Math.max or "
              "TextUtils.isEmpty. Static methods in Kotlin objects and companions annotated with @JvmStatic, and "
              "top-level functions, compile to it too."),
        tech=("All registers are ordinary arguments; there is no receiver. The first call initialises the "
              "declaring class (its <clinit> may run and fail with ExceptionInInitializerError). Resolution can "
              "throw NoSuchMethodError, IncompatibleClassChangeError (if the method is not static) or "
              "IllegalAccessError."),
        example=ex("""
            .method public static clamp(I)I
                .locals 1
                const/16 v0, 0x64
                invoke-static {p0, v0}, Ljava/lang/Math;->min(II)I
                move-result p0
                return p0
            .end method
        """),
        explanation="Math.min(value, 100) has no receiver, so both registers are plain arguments; the int result "
                    "is retrieved with move-result.",
        java="int r = Math.min(value, 100);", kotlin="val r = minOf(value, 100)",
        mistakes=["Adding a receiver register out of habit: static methods take only their declared parameters.",
                  "Calling an instance method with invoke-static, which fails resolution with "
                  "IncompatibleClassChangeError."],
        related=["invoke-direct", "invoke-virtual", "sget"],
        range_example=ex("""
            # Long.valueOf(v20/v21): the long argument sits in a high register pair
            invoke-static/range {v20 .. v21}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
            move-result-object v0
        """),
        range_explanation=("The long argument takes two words (v20 and v21). Those registers are above v15, so the "
                           "range form is needed even though only two words are passed."),
    ),
    "interface": dict(
        summary="Call an interface method on an object whose concrete class is unknown",
        desc=("invoke-interface calls a method declared in an interface, such as List.size() or Runnable.run(). "
              "The implementation is found at run time in the receiver's class. You see it whenever code works "
              "against an interface type."),
        tech=("The first register is the receiver; the method_id must refer to an interface. Throws "
              "NullPointerException for a null receiver, IncompatibleClassChangeError if the receiver's class does "
              "not implement the interface, AbstractMethodError if no implementation exists, and resolution "
              "errors."),
        example=ex("""
            .method public static count(Ljava/util/List;)I
                .locals 1
                invoke-interface {p0}, Ljava/util/List;->size()I
                move-result v0
                return v0
            .end method
        """),
        explanation="List is an interface, so size() is called with invoke-interface and the result read with "
                    "move-result.",
        java="int n = list.size();", kotlin="val n = list.size",
        mistakes=["Using invoke-virtual with an interface method reference: resolution fails with "
                  "IncompatibleClassChangeError.",
                  "Changing the declared receiver type to a concrete class without switching to invoke-virtual."],
        related=["invoke-virtual", "invoke-super"],
        range_example=ex("""
            # map.put(key, value) with the registers above v15
            invoke-interface/range {v16 .. v18}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
        """),
        range_explanation=("Only three words are passed, but they live in v16..v18, beyond the 4-bit register "
                           "fields, so the range form is used."),
    ),
}


def gen_invokes(ctx):
    out = []
    for kind, k in INVOKES.items():
        for rng in (False, True):
            name = f"invoke-{kind}" + ("/range" if rng else "")
            fmt = "3rc" if rng else "35c"
            if rng:
                operands = [opnd("AA", "AA", 8, "argument word count, 0–255; smali derives it from the range."),
                            opnd("vCCCC", "CCCC", 16, "first argument register, v0–v65535."),
                            derived("vNNNN", "last argument register, NNNN = CCCC + AA - 1."),
                            opnd("Lclass;->method(params)ret", "BBBB", 16, "method_ids index (meth@BBBB).")]
                syntax = f"{name} {{vCCCC .. vNNNN}}, Lclass;->method(params)ret"
                regs = ("Reads the consecutive registers vCCCC..vNNNN as the argument words (receiver first for "
                        "non-static calls; long/double arguments take two consecutive words). Writes no register "
                        "directly: the result, if any, is collected with move-result*.")
                limits = ("A range covers up to 255 argument words starting at any register up to v65535, but the "
                          "registers must be consecutive.")
            else:
                operands = [opnd("A", "A", 4, "argument word count, 0–5; smali derives it from the register list."),
                            opnd("vC", "C", 4, "first argument register (the receiver for instance calls)."),
                            opnd("vD", "D", 4, "second argument register."), opnd("vE", "E", 4, "third argument register."),
                            opnd("vF", "F", 4, "fourth argument register."), opnd("vG", "G", 4, "fifth argument register."),
                            opnd("Lclass;->method(params)ret", "BBBB", 16, "method_ids index (meth@BBBB).")]
                syntax = f"{name} {{vC, vD, vE, vF, vG}}, Lclass;->method(params)ret"
                regs = ("Reads the listed registers (at most five, each v0–v15) as argument words, receiver first "
                        "for non-static calls; a long/double argument is listed as two consecutive registers. "
                        "Writes no register directly: the result, if any, is collected with move-result*. "
                        + P_REG_NOTE)
                limits = "At most five argument words, and every register must be in v0–v15."
            tech = (enc(ctx, fmt) + " " + limits + " Argument words count this as one word and each long/double "
                    "as two. " + k["tech"] + " A return value is only available to a move-result* placed "
                    "immediately after the call.")
            if rng:
                desc = (f"{name} is the range form of invoke-{kind}: the arguments are a run of consecutive "
                        f"registers instead of an explicit list. Compilers use it when a call needs more than five "
                        f"argument words or when the arguments sit above v15. The call semantics are identical to "
                        f"invoke-{kind}.")
                example, explanation = k["range_example"], k["range_explanation"]
                mistakes = ["Listing registers that are not consecutive: {v0 .. v5} means exactly v0, v1, v2, v3, v4, v5.",
                            "Counting arguments instead of words: long and double arguments take two registers in "
                            "the range.",
                            k["mistakes"][0]]
            else:
                desc = k["desc"]
                example, explanation = k["example"], k["explanation"]
                mistakes = list(k["mistakes"]) + [
                    "Listing only one register for a long/double argument: write both halves, e.g. {p1, p2} for a "
                    "long in p1/p2.",
                    "Putting other code between the invoke and its move-result*."]
            related = ([f"invoke-{kind}/range" if not rng else f"invoke-{kind}"] + k["related"]
                       + ["move-result", "move-result-object"])
            out.append(make(
                ctx, name, "invoke", syntax=syntax, operands=operands,
                summary=k["summary"] + (", with arguments in a register range." if rng else "."),
                description=desc, technical=tech, example=example, explanation=explanation, java=k["java"],
                kotlin=k["kotlin"], registers=regs, mistakes=mistakes[:4], related=related,
                tags=["invoke", "call", kind] + (["range"] if rng else []),
                compat_extra=k.get("compat", ""), refs_extra=[REF_DEX_METHOD]))
    return out


# =================================================================================================================
# unops: neg-*, not-* and conversions
# =================================================================================================================

TYPEINFO = {
    "int": dict(d="I", wide=False, jt="int", kt="Int"),
    "long": dict(d="J", wide=True, jt="long", kt="Long"),
    "float": dict(d="F", wide=False, jt="float", kt="Float"),
    "double": dict(d="D", wide=True, jt="double", kt="Double"),
    "byte": dict(d="B", wide=False, jt="byte", kt="Byte"),
    "char": dict(d="C", wide=False, jt="char", kt="Char"),
    "short": dict(d="S", wide=False, jt="short", kt="Short"),
}


def ret_for(t):
    return "return-wide" if TYPEINFO[t]["wide"] else "return"


def unop_example(name, src, dst, mname):
    s, d = TYPEINFO[src], TYPEINFO[dst]
    locals_ = 2 if d["wide"] else 1
    return (f".method public static {mname}({s['d']}){d['d']}\n    .locals {locals_}\n    {name} v0, p0\n"
            f"    {ret_for(dst)} v0\n.end method")


CONVERSIONS = {
    "int-to-long": ("int", "long", "Sign-extends the 32-bit int to 64 bits; every int value is represented exactly.",
                    "(long) i", "i.toLong()"),
    "int-to-float": ("int", "float", "Converts using round-to-nearest. ints beyond ±2^24 (16,777,216) may lose "
                     "precision, e.g. 16777217 becomes 16777216.0f.", "(float) i", "i.toFloat()"),
    "int-to-double": ("int", "double", "Converts exactly: every int fits in a double's 53-bit mantissa.",
                      "(double) i", "i.toDouble()"),
    "long-to-int": ("long", "int", "Truncates: keeps the low 32 bits and discards the high word, so large values "
                    "wrap (0x100000001L becomes 1).", "(int) l", "l.toInt()"),
    "long-to-float": ("long", "float", "Converts using round-to-nearest; most large longs lose precision.",
                      "(float) l", "l.toFloat()"),
    "long-to-double": ("long", "double", "Converts using round-to-nearest; longs beyond ±2^53 may lose precision.",
                       "(double) l", "l.toDouble()"),
    "float-to-int": ("float", "int", "Rounds toward zero (truncates the fraction). NaN and -0.0 convert to 0; "
                     "infinities and out-of-range values saturate to 0x7fffffff or -0x80000000 depending on sign.",
                     "(int) f", "f.toInt()"),
    "float-to-long": ("float", "long", "Rounds toward zero. NaN and -0.0 convert to 0; infinities and out-of-range "
                      "values saturate to 0x7fffffffffffffff or -0x8000000000000000.", "(long) f", "f.toLong()"),
    "float-to-double": ("float", "double", "Widens exactly: every float value, including NaN and infinities, is "
                        "preserved.", "(double) f", "f.toDouble()"),
    "double-to-int": ("double", "int", "Rounds toward zero. NaN and -0.0 convert to 0; infinities and out-of-range "
                      "values saturate to 0x7fffffff or -0x80000000.", "(int) d", "d.toInt()"),
    "double-to-long": ("double", "long", "Rounds toward zero. NaN and -0.0 convert to 0; infinities and "
                       "out-of-range values saturate to 0x7fffffffffffffff or -0x8000000000000000.",
                       "(long) d", "d.toLong()"),
    "double-to-float": ("double", "float", "Narrows using round-to-nearest; values too large become ±Infinity and "
                        "precision is lost.", "(float) d", "d.toFloat()"),
    "int-to-byte": ("int", "byte", "Keeps the low 8 bits and sign-extends them back to 32 bits: (a << 24) >> 24. "
                    "200 becomes -56.", "(byte) i", "i.toByte()"),
    "int-to-char": ("int", "char", "Keeps the low 16 bits without sign extension: a & 0xffff. -1 becomes 65535.",
                    "(char) i", "i.toChar()"),
    "int-to-short": ("int", "short", "Keeps the low 16 bits and sign-extends them: (a << 16) >> 16. 40000 becomes "
                     "-25536.", "(short) i", "i.toShort()"),
}


def gen_unops(ctx):
    out = []
    negs = {
        "neg-int": ("int", "-a", "-a", "Two's-complement negation. -Integer.MIN_VALUE overflows back to "
                    "Integer.MIN_VALUE."),
        "not-int": ("int", "~a", "a.inv()", "One's-complement: every bit is flipped, so ~a == -a - 1."),
        "neg-long": ("long", "-a", "-a", "Two's-complement negation of a 64-bit value; -Long.MIN_VALUE is "
                     "Long.MIN_VALUE."),
        "not-long": ("long", "~a", "a.inv()", "One's-complement of all 64 bits."),
        "neg-float": ("float", "-a", "-a", "IEEE 754 negation: flips the sign bit, so -(0.0f) is -0.0f and the "
                      "negation of NaN is NaN. This differs from 0.0f - a, which gives +0.0f for a = 0.0f."),
        "neg-double": ("double", "-a", "-a", "IEEE 754 negation of a double: flips the sign bit (-(0.0) is -0.0)."),
    }
    for name, (t, jexpr, kexpr, sem) in negs.items():
        info = TYPEINFO[t]
        wide = info["wide"]
        is_not = name.startswith("not")
        operands = [opnd("vA", "A", 4, f"destination {'register pair' if wide else 'register'}, v0–v15."),
                    opnd("vB", "B", 4, f"source {'register pair' if wide else 'register'}, v0–v15.")]
        desc = (f"{name} computes the {'bitwise complement' if is_not else 'negation'} of {an(t)} and writes it to "
                f"the destination. "
                + ("Depending on the compiler, ~a appears either as this instruction or as an xor with -1; the two "
                   "are equivalent. " if is_not else
                   f"It is what the unary minus operator on {an(t)} compiles to. ")
                + ("Both operands are register pairs." if wide else "It uses the compact 4-bit register form."))
        mistakes = [("Expecting -MIN_VALUE to be positive: it overflows to MIN_VALUE." if t in ("int", "long") and not is_not
                     else "Using not-int to negate a boolean: it flips all 32 bits, turning 1 into -2. Logical "
                          "!flag is compiled as a branch or as xor-int/lit8 v, v, 0x1." if is_not
                     else "Replacing neg with a subtraction from 0.0: that gives a different result for 0.0 (sign of zero)."),
                    "Using registers above v15: move the value to a low register first.",
                    (f"Using the {t} variant on a {'32-bit value' if wide else 'register pair'}: the type must "
                     f"match exactly.")]
        out.append(make(
            ctx, name, "bitwise" if is_not else "arithmetic", syntax=f"{name} vA, vB", operands=operands,
            summary=f"{'Bitwise NOT of' if is_not else 'Negate'} {an(t)}: vA = {jexpr.replace('a', 'vB')}.",
            description=desc,
            technical=enc(ctx, "12x") + f" vA = {jexpr.replace('a', 'vB')}. {sem} Cannot throw.",
            example=unop_example(name, t, t, "invert" if is_not else "negate"),
            explanation=(f"The {t} parameter in p0{'/p1' if wide else ''} is "
                         f"{'complemented' if is_not else 'negated'} into {'the pair v0/v1' if wide else 'v0'} and "
                         f"returned."),
            java=f"{info['jt']} r = {jexpr};", kotlin=f"val r = {kexpr}",
            registers=(f"Writes vA{'/vA+1' if wide else ''}, reads vB{'/vB+1' if wide else ''}; both in v0–v15."),
            mistakes=mistakes,
            related=[x for x in negs if x != name][:3] + (["xor-int/lit8"] if is_not and t == "int" else
                                                          ["rsub-int"] if t == "int" else [f"sub-{t}"]),
            tags=["unary", t] + (["wide"] if wide else []) + (["bitwise"] if is_not else ["arithmetic"]),
            refs_extra=[REF_MATH]))
    for name, (src, dst, sem, jexpr, kexpr) in CONVERSIONS.items():
        s, d = TYPEINFO[src], TYPEINFO[dst]
        operands = [opnd("vA", "A", 4, f"destination {'register pair' if d['wide'] else 'register'} for the "
                                       f"{dst} result, v0–v15."),
                    opnd("vB", "B", 4, f"source {'register pair' if s['wide'] else 'register'} holding the {src}, "
                                       f"v0–v15.")]
        narrowing = dst in ("byte", "char", "short")
        desc = (f"{name} converts {an(src)} into {an(dst)}, which is what {an(jexpr.split(' ')[0])} cast compiles to. "
                + {True: "The result stays a 32-bit int in the register, just with the narrow type's range. ",
                   False: ""}[narrowing]
                + ("Source and destination use different register widths, so pick the registers carefully. "
                   if s["wide"] != d["wide"] else "")
                + ("It never throws, even for NaN or out-of-range values." if src in ("float", "double")
                   else "It never throws."))
        tech = (enc(ctx, "12x") + f" vA = ({dst}) vB. {sem} "
                + (f"The source is a register pair (vB, vB+1). " if s["wide"] else "")
                + (f"The destination is a register pair (vA, vA+1). " if d["wide"] else "")
                + "Cannot throw.")
        mistakes = []
        if src in ("float", "double") and dst in ("int", "long"):
            mistakes.append("Expecting rounding to the nearest integer: the conversion truncates toward zero "
                            "(2.9 becomes 2, -2.9 becomes -2). Call Math.round first if you need rounding.")
            mistakes.append("Expecting an exception for NaN or huge values: NaN becomes 0 and out-of-range values "
                            "saturate.")
        elif narrowing:
            mistakes.append("Expecting the value to be range-checked: the high bits are simply discarded.")
            mistakes.append("Mixing up sign extension: int-to-byte and int-to-short sign-extend, int-to-char "
                            "zero-extends.")
        elif s["wide"] != d["wide"]:
            mistakes.append("Reserving only one register for a wide result or source: long and double values "
                            "occupy a register pair.")
            mistakes.append("Overlapping the destination pair with the source in a way that clobbers it before use; "
                            "keep them distinct when in doubt.")
        else:
            mistakes.append("Assuming the conversion is exact: check the precision notes for large values.")
            mistakes.append("Converting the raw bits with a move instead of this instruction: a move copies bits, "
                            "it doesn't convert the value.")
        if name in ("int-to-float", "long-to-float", "long-to-double", "double-to-float"):
            mistakes.append("Comparing the converted value for exact equality with the original: rounding may have "
                            "changed it.")
        mistakes.append("Using registers above v15: the 12x format only addresses v0–v15.")
        family = [n for n in CONVERSIONS if n.startswith(src + "-to-") and n != name][:3]
        reverse = f"{dst}-to-{src}"
        related = ([reverse] if reverse in CONVERSIONS else []) + family + (
            [f"{dst}-to-int"] if f"{dst}-to-int" in CONVERSIONS and f"{dst}-to-int" != name else [])
        if narrowing:
            related += ["aput-" + dst, "long-to-int"]
        out.append(make(
            ctx, name, "conversion", syntax=f"{name} vA, vB", operands=operands,
            summary=f"Convert {an(src)} to {an(dst)}: vA = ({dst}) vB.",
            description=desc, technical=tech,
            example=unop_example(name, src, dst, "convert"),
            explanation=(f"The {src} parameter arrives in {'p0/p1' if s['wide'] else 'p0'}; {name} writes the "
                         f"{dst} result to {'the pair v0/v1' if d['wide'] else 'v0'}, which is returned with "
                         f"{ret_for(dst)}."),
            java=f"{d['jt']} r = {jexpr};", kotlin=f"val r = {kexpr}",
            registers=(f"Writes vA{'/vA+1' if d['wide'] else ''}, reads vB{'/vB+1' if s['wide'] else ''}; both in "
                       f"v0–v15."),
            mistakes=mistakes[:4], related=related,
            tags=["conversion", src, dst] + (["wide"] if s["wide"] or d["wide"] else []) + (["narrowing"] if narrowing else []),
            refs_extra=[REF_MATH]))
    return out


# =================================================================================================================
# binops: 23x, /2addr, /lit16, /lit8
# =================================================================================================================

OPS = {
    "add": dict(sym="+", kt="+", cat="arithmetic", verb="Add", mname="sum"),
    "sub": dict(sym="-", kt="-", cat="arithmetic", verb="Subtract", mname="difference"),
    "mul": dict(sym="*", kt="*", cat="arithmetic", verb="Multiply", mname="product"),
    "div": dict(sym="/", kt="/", cat="arithmetic", verb="Divide", mname="quotient"),
    "rem": dict(sym="%", kt="%", cat="arithmetic", verb="Remainder of", mname="remainder"),
    "and": dict(sym="&", kt="and", cat="bitwise", verb="Bitwise AND", mname="mask"),
    "or": dict(sym="|", kt="or", cat="bitwise", verb="Bitwise OR", mname="combine"),
    "xor": dict(sym="^", kt="xor", cat="bitwise", verb="Bitwise XOR", mname="toggle"),
    "shl": dict(sym="<<", kt="shl", cat="bitwise", verb="Shift left", mname="shiftLeft"),
    "shr": dict(sym=">>", kt="shr", cat="bitwise", verb="Arithmetic shift right", mname="shiftRight"),
    "ushr": dict(sym=">>>", kt="ushr", cat="bitwise", verb="Logical shift right", mname="unsignedShiftRight"),
    "rsub": dict(sym="-", kt="-", cat="arithmetic", verb="Reverse subtract", mname="reverseSubtract"),
}

LIT16_VALUES = {"add": ("0x3e8", "1000"), "rsub": ("0x64", "100"), "mul": ("0x3e8", "1000"), "div": ("0x3e8", "1000"),
                "rem": ("0x3e8", "1000"), "and": ("0xfff", "0xfff"), "or": ("0x100", "0x100"),
                "xor": ("0x5a5", "0x5a5")}
LIT8_VALUES = {"add": ("0x1", "1"), "rsub": ("0xa", "10"), "mul": ("0xa", "10"), "div": ("0x2", "2"),
               "rem": ("0xa", "10"), "and": ("0xf", "0xf"), "or": ("0x1", "1"), "xor": ("0x1", "1"),
               "shl": ("0x4", "4"), "shr": ("0x4", "4"), "ushr": ("0x18", "24")}


def semantics(op, t):
    bits = 64 if t in ("long", "double") else 32
    if t in ("int", "long"):
        cls = "Integer" if t == "int" else "Long"
        return {
            "add": f"Two's-complement addition; overflow wraps around silently ({cls}.MAX_VALUE + 1 == {cls}.MIN_VALUE).",
            "sub": f"Two's-complement subtraction; overflow wraps around silently.",
            "mul": f"Two's-complement multiplication; only the low {bits} bits of the product are kept, so overflow "
                   f"goes undetected.",
            "div": f"Signed division rounded toward zero (7 / -2 == -3). Throws java.lang.ArithmeticException if the "
                   f"divisor is 0. {cls}.MIN_VALUE / -1 overflows to {cls}.MIN_VALUE without an exception, as in Java.",
            "rem": f"Signed remainder defined as a - (a / b) * b, so the result has the sign of the dividend "
                   f"(-7 % 3 == -1). Throws java.lang.ArithmeticException if the divisor is 0. {cls}.MIN_VALUE % -1 "
                   f"is 0.",
            "and": f"Bitwise AND of all {bits} bits.",
            "or": f"Bitwise OR of all {bits} bits.",
            "xor": f"Bitwise exclusive OR of all {bits} bits.",
            "shl": f"Shift left by (distance & {'0x1f' if t == 'int' else '0x3f'}): only the low "
                   f"{5 if t == 'int' else 6} bits of the distance count, so shifting by {bits} leaves the value "
                   f"unchanged.",
            "shr": f"Arithmetic (signed) shift right by (distance & {'0x1f' if t == 'int' else '0x3f'}); the sign "
                   f"bit is copied into the vacated high bits.",
            "ushr": f"Logical (unsigned) shift right by (distance & {'0x1f' if t == 'int' else '0x3f'}); zeros are "
                    f"shifted into the high bits.",
            "rsub": "Two's-complement reverse subtraction: literal - value; overflow wraps around silently.",
        }[op]
    prec = "single" if t == "float" else "double"
    return {
        "add": f"IEEE 754 {prec}-precision addition with round-to-nearest; NaN operands give NaN and overflow gives "
               f"±Infinity.",
        "sub": f"IEEE 754 {prec}-precision subtraction with round-to-nearest.",
        "mul": f"IEEE 754 {prec}-precision multiplication with round-to-nearest; overflow gives ±Infinity, "
               f"Infinity × 0 gives NaN.",
        "div": f"IEEE 754 {prec}-precision division. Division by zero does NOT throw: a non-zero value divided by "
               f"0 gives ±Infinity and 0/0 gives NaN.",
        "rem": f"Floating-point remainder defined as a - roundTowardZero(a / b) * b (like Java's % and C's fmod), "
               f"which differs from the IEEE 754 remainder (Math.IEEEremainder). x % 0 is NaN.",
    }[op]


def binop_mistakes(op, t, form):
    m = []
    wide = t in ("long", "double")
    if op in ("div", "rem") and t in ("int", "long"):
        m.append("Not guarding against a zero divisor: integer division and remainder throw ArithmeticException.")
    if op in ("div", "rem") and t in ("float", "double"):
        m.append("Expecting ArithmeticException on division by zero: floating-point division returns Infinity or "
                 "NaN instead.")
    if op == "rem" and t in ("float", "double"):
        m.append("Treating the result like Math.IEEEremainder: rem rounds the quotient toward zero, not to nearest.")
    if op == "rem" and t in ("int", "long"):
        m.append("Expecting a non-negative result for a negative dividend: -7 % 3 is -1. Use Math.floorMod for "
                 "modular arithmetic.")
    if op in ("shl", "shr", "ushr"):
        mask = "0x3f" if t == "long" else "0x1f"
        m.append(f"Expecting a shift by {64 if t == 'long' else 32} or more to clear the value: the distance is "
                 f"masked with {mask}.")
        if t == "long":
            m.append("Passing the shift distance as a long register pair: the distance is a single 32-bit int "
                     "register.")
        if op == "shr":
            m.append("Using shr where an unsigned shift was intended: negative values stay negative; use ushr.")
        if op == "ushr" and form != "lit8":
            m.append("Expecting ushr on a byte/short value to give a small positive number: the value is first "
                     "sign-extended to a full int.")
    if op in ("add", "sub", "mul") and t in ("int", "long"):
        m.append("Assuming overflow is detected: results silently wrap. Use Math.addExact/multiplyExact if you "
                 "need an exception.")
    if op in ("add", "sub", "mul") and t in ("float", "double"):
        m.append("Comparing floating-point results for exact equality: rounding makes 0.1 + 0.2 != 0.3.")
    if op in ("and", "or", "xor"):
        m.append("Confusing the bitwise operator with the short-circuit logical operator (&&, ||): this always "
                 "evaluates both operands.")
    if op == "rsub":
        m.append("Reading rsub as value - literal: it computes literal - value.")
        m.append("Looking for sub-int/lit8 or sub-int/lit16: they do not exist. x - 5 is compiled as add-int/lit8 "
                 "with -5.")
    if form == "23x":
        if wide:
            m.append("Naming the second half of a register pair as an operand, or letting a source pair partially "
                     "overlap the destination pair.")
        m.append(f"Mixing types: {op}-{t} requires {t} operands. The verifier rejects, for example, "
                 + ("a float register where an int is expected." if t == "int" else
                    f"an int register where {an(t)} is expected."))
    elif form == "2addr":
        m.append("Forgetting that the first operand is overwritten: vA is both a source and the destination.")
        m.append("Using registers above v15: /2addr has 4-bit register fields; use the three-register form instead.")
    elif form == "lit16":
        m.append("Using a literal outside -32768..32767: load it with const and use the three-register form.")
        m.append("Using registers above v15: /lit16 has 4-bit register fields; /lit8 reaches v255 for small literals.")
    elif form == "lit8":
        m.append("Using a literal outside -128..127: use the /lit16 form or const plus the three-register form.")
        if op in ("shl", "shr", "ushr"):
            m.append("Looking for a /lit16 shift: shifts by a literal only exist in the /lit8 form.")
    if op == "add" and form in ("lit8", "lit16"):
        m.insert(0, "Looking for sub-int/lit8: there is none. Subtracting a constant is written as add-int with a "
                    "negative literal, e.g. add-int/lit8 v0, v0, -0x1 for i--.")
    return m[:4]


def binop_related(ctx, op, t, form, name):
    cand = []
    if form != "23x" and op != "rsub":
        cand.append(f"{op}-{t}")
    if form != "2addr" and op != "rsub":
        cand.append(f"{op}-{t}/2addr")
    if t == "int":
        cand += [f"{op}-int/lit16", f"{op}-int/lit8", "rsub-int", "rsub-int/lit8"] if op in ("add", "sub", "rsub") \
            else [f"{op}-int/lit16", f"{op}-int/lit8"]
    for t2 in ("int", "long", "float", "double"):
        if t2 != t:
            cand.append(f"{op}-{t2}" + ("/2addr" if form == "2addr" else ""))
    partner = {"add": "sub", "sub": "add", "mul": "div", "div": "rem", "rem": "div", "and": "or", "or": "xor",
               "xor": "and", "shl": "shr", "shr": "ushr", "ushr": "shr", "rsub": "sub"}[op]
    cand.append(f"{partner}-{t}")
    if op == "rsub":
        cand += ["sub-int", "add-int/lit8", "add-int/lit16"]
    if op in ("and", "xor") and t == "int":
        cand.append("not-int")
    if op in ("div", "rem") and t in ("float", "double"):
        cand.append(f"cmpl-{t}")
    return [c for c in dict.fromkeys(existing(ctx, cand)) if c != name][:7]


def gen_binops(ctx):
    out = []
    for name, s in ctx.spec_by_name.items():
        fmt = s.fmt
        base = name.split("/")[0]
        if s.opcode < 0x90 or s.opcode > 0xe2:
            continue
        if base == "rsub-int":
            op, t = "rsub", "int"
        else:
            op, t = base.rsplit("-", 1)
        form = "23x" if fmt == "23x" else "2addr" if name.endswith("/2addr") else "lit16" if fmt == "22s" else "lit8"
        o = OPS[op]
        info = TYPEINFO[t]
        wide = info["wide"]
        shift = op in ("shl", "shr", "ushr")
        sem = semantics(op, t)
        pair = "register pair" if wide else "register"
        jt, kt, d = info["jt"], info["kt"], info["d"]
        if form == "23x":
            syntax = f"{name} vAA, vBB, vCC"
            operands = [opnd("vAA", "AA", 8, f"destination {pair}, v0–v255."),
                        opnd("vBB", "BB", 8, f"first source {pair}, v0–v255."),
                        opnd("vCC", "CC", 8, ("shift distance: a single int register (not a pair), v0–v255."
                                              if shift and wide else f"second source {pair}, v0–v255."))]
            formula = f"vAA = vBB {o['sym']} vCC"
            regs = (f"Writes vAA{'/vAA+1' if wide else ''}; reads vBB{'/vBB+1' if wide else ''} and "
                    f"vCC{'/vCC+1' if wide and not shift else ''}. All in v0–v255.")
            reach = "Three-register form: each operand can be any of the first 256 registers."
            dsig = f"({d}{'I' if shift else d}){d}"
            second = "p2" if wide else "p1"
            body = [f"{name} v0, p0, {second}", f"{ret_for(t)} v0"]
            locals_ = 2 if wide else 1
            jsrc = (f"static {jt} {o['mname']}({jt} a, {'int' if shift else jt} b) {{ return a {o['sym']} b; }}")
            java = f"{jt} r = a {o['sym']} b;"
            kotlin = f"val r = a {o['kt']} b"
            expl = (f"{'p0/p1 holds the long value and p2 the int shift distance' if shift and wide else 'Each ' + t + ' parameter occupies a register pair (p0/p1 and p2/p3)' if wide else 'p0 and p1 hold the two ' + t + ' parameters'}; "
                    f"{name} writes the result to {'the pair v0/v1' if wide else 'v0'}, which is returned.")
        elif form == "2addr":
            syntax = f"{name} vA, vB"
            operands = [opnd("vA", "A", 4, f"destination and first source {pair}, v0–v15."),
                        opnd("vB", "B", 4, ("shift distance: a single int register, v0–v15." if shift and wide
                                            else f"second source {pair}, v0–v15."))]
            formula = f"vA = vA {o['sym']} vB"
            regs = (f"Reads and writes vA{'/vA+1' if wide else ''}; reads vB{'/vB+1' if wide and not shift else ''}. "
                    f"Both in v0–v15. " + P_REG_NOTE)
            reach = ("Two-address form: the result replaces the first operand, which saves a code unit but limits "
                     "both registers to v0–v15.")
            dsig = f"({d}{'I' if shift else d}){d}"
            second = "p2" if wide else "p1"
            body = [f"{name} p0, {second}", f"{ret_for(t)} p0"]
            locals_ = 0
            jsrc = (f"static {jt} {o['mname']}({jt} a, {'int' if shift else jt} b) {{ a {o['sym']}= b; return a; }}")
            java = f"a {o['sym']}= b;"
            kotlin = (f"a {o['kt']}= b" if o['kt'] in "+-*/%" else f"a = a {o['kt']} b")
            expl = (f"With .locals 0 the parameters are the only registers. {name} combines "
                    f"{'p0/p1' if wide else 'p0'} with {second}{'/p3' if wide and not shift else ''} and stores the "
                    f"result back into {'p0/p1' if wide else 'p0'}, which is returned.")
        else:
            lit_tok, bits, letters = ("lit16", 16, "CCCC") if form == "lit16" else ("lit8", 8, "CC")
            lit_hex, lit_dec = (LIT16_VALUES if form == "lit16" else LIT8_VALUES)[op]
            rng = "-32768..32767" if form == "lit16" else "-128..127"
            if form == "lit16":
                syntax = f"{name} vA, vB, lit16"
                operands = [opnd("vA", "A", 4, "destination register, v0–v15."),
                            opnd("vB", "B", 4, "source register, v0–v15.")]
                regs = "Writes vA, reads vB; both in v0–v15. " + P_REG_NOTE
                reach = "Register fields are 4 bits (v0–v15); the literal is a signed 16-bit value."
            else:
                syntax = f"{name} vAA, vBB, lit8"
                operands = [opnd("vAA", "AA", 8, "destination register, v0–v255."),
                            opnd("vBB", "BB", 8, "source register, v0–v255.")]
                regs = "Writes vAA, reads vBB; both in v0–v255."
                reach = "Register fields are 8 bits (v0–v255); the literal is a signed 8-bit value."
            operands.append(opnd(lit_tok, letters, bits, f"signed literal, {rng}, sign-extended to 32 bits"
                                 + ("; only its low 5 bits are used as the shift distance." if shift else ".")))
            dst, srcr = ("vA", "vB") if form == "lit16" else ("vAA", "vBB")
            formula = (f"{dst} = {lit_tok} - {srcr}" if op == "rsub" else f"{dst} = {srcr} {o['sym']} {lit_tok}")
            dsig = "(I)I"
            body = [f"{name} v0, p0, {lit_hex}" + (f"    # {lit_dec}" if lit_dec != lit_hex else ""), "return v0"]
            locals_ = 1
            expr = f"{lit_dec} - a" if op == "rsub" else f"a {o['sym']} {lit_dec}"
            kexpr = f"{lit_dec} - a" if op == "rsub" else f"a {o['kt']} {lit_dec}"
            jsrc = f"static int {o['mname']}(int a) {{ return {expr}; }}"
            java = f"int r = {expr};"
            kotlin = f"val r = {kexpr}"
            expl = (f"The constant {lit_dec} is encoded directly in the instruction, so no const is needed; "
                    f"{name} writes {'the literal minus p0' if op == 'rsub' else 'p0 ' + o['sym'] + ' ' + lit_dec} "
                    f"to v0.")
        example = (f"# Java: {jsrc}\n.method public static {o['mname']}{dsig}\n    .locals {locals_}\n"
                   + "".join(f"    {b}\n" for b in body) + ".end method")
        verb = o["verb"]
        if op == "rsub":
            summary = f"Reverse subtract: {formula} (int, {form} literal)."
            desc = (f"{name} subtracts a register from a constant (literal minus register), the reverse of the "
                    f"usual order. Compilers use it for expressions like 100 - x and, with a literal of 0, for "
                    f"negation. {'The 16-bit literal form is the main opcode of its family, so it has no /lit16 suffix.' if form == 'lit16' else 'This /lit8 form takes a literal from -128 to 127.'}")
        else:
            noun = {"add": "addition", "sub": "subtraction", "mul": "multiplication", "div": "division",
                    "rem": "remainder", "and": "bitwise AND", "or": "bitwise OR", "xor": "bitwise XOR",
                    "shl": "left shift", "shr": "arithmetic right shift", "ushr": "logical right shift"}[op]
            what = {"23x": "two registers", "2addr": "two registers, storing the result in the first",
                    "lit16": "a register and a 16-bit constant", "lit8": "a register and an 8-bit constant"}[form]
            summary = f"{verb} {t}s: {formula}."
            if op in ("and", "or", "xor", "shl", "shr", "ushr"):
                summary = f"{verb} ({t}): {formula}."
            desc = (f"{name} performs {t} {noun} on {what}. "
                    + {"add": "", "sub": "", "mul": "",
                       "div": ("Integer division truncates toward zero and throws ArithmeticException for a zero "
                               "divisor. " if t in ("int", "long") else
                               "Dividing by zero gives Infinity or NaN rather than an exception. "),
                       "rem": ("The result takes the sign of the dividend, and a zero divisor throws "
                               "ArithmeticException. " if t in ("int", "long") else
                               "It behaves like Java's % on floating-point values, not like IEEE remainder. "),
                       "and": "Typical uses are masking bits and testing flags. ",
                       "or": "Typical uses are setting flag bits and combining masks. ",
                       "xor": "Typical uses are toggling bits; xor with 1 is one way compilers implement !flag. ",
                       "shl": f"The shift distance is masked to {'6' if t == 'long' else '5'} bits. ",
                       "shr": "The sign bit is preserved, so negative values stay negative. ",
                       "ushr": "Zeros are shifted in from the left, so the result is never negative "
                               "(unless the distance is 0). "}[op]
                    + {"23x": f"It is the general three-register form of {t} {o['sym']}.",
                       "2addr": f"It is the compact form compilers emit for a {o['sym']}= b, or whenever the "
                                f"destination equals the first source and the registers are low.",
                       "lit16": "Using a literal saves loading the constant into a register first.",
                       "lit8": ("It is the form compilers pick for constants that fit in a signed byte, such as "
                                "i + 1 in loops." if op == "add" else
                                "Compilers pick it whenever the constant fits in a signed byte.")}[form])
        tech = (enc(ctx, fmt) + f" {formula}. {sem} {reach}"
                + (" For long shifts the distance is a single 32-bit register, not a pair." if shift and wide else "")
                + (" Wide operands name the first (lower) register of each pair." if wide and not shift else "")
                + (" Cannot throw." if "throw" not in sem else ""))
        tags = [o["cat"], t] + (["wide"] if wide else []) + ([form] if form != "23x" else ["3-register"]) \
            + (["shift"] if shift else []) + (["floating-point"] if t in ("float", "double") else []) \
            + (["throws"] if op in ("div", "rem") and t in ("int", "long") else [])
        out.append(make(
            ctx, name, o["cat"], syntax=syntax, operands=operands, summary=summary, description=desc,
            technical=tech, example=example, explanation=expl, java=java, kotlin=kotlin, registers=regs,
            mistakes=binop_mistakes(op, t, form), related=binop_related(ctx, op, t, form, name),
            tags=list(dict.fromkeys(tags)), refs_extra=[REF_MATH]))
    return out


# =================================================================================================================
# method handles: invoke-polymorphic(/range), invoke-custom(/range), const-method-handle, const-method-type
# =================================================================================================================

def gen_method_handles(ctx):
    out = []
    poly_compat = ("invoke-polymorphic is only valid for signature-polymorphic methods: MethodHandle.invoke and "
                   "MethodHandle.invokeExact, and the VarHandle accessor methods (get, set, compareAndSet, ...). "
                   "java.lang.invoke.MethodHandle is in the public SDK from API 26; VarHandle from API 33.")
    out.append(make(
        ctx, "invoke-polymorphic", "method_handle",
        syntax="invoke-polymorphic {vC, vD, vE, vF, vG}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (params)ret",
        operands=[opnd("A", "A", 4, "argument word count, 1–5 (the receiver is always present)."),
                  opnd("vC", "C", 4, "the receiver: the MethodHandle (or VarHandle) object."),
                  opnd("vD", "D", 4, "first argument register."), opnd("vE", "E", 4, "second argument register."),
                  opnd("vF", "F", 4, "third argument register."), opnd("vG", "G", 4, "fourth argument register."),
                  opnd("Lclass;->method(params)ret", "BBBB", 16, "method_ids index (meth@BBBB) of the "
                                                                "signature-polymorphic method, e.g. "
                                                                "MethodHandle.invokeExact."),
                  opnd("(params)ret", "HHHH", 16, "proto_ids index (proto@HHHH): the actual argument types and "
                                                  "the expected return type at this call site.")],
        summary="Invoke a signature-polymorphic method such as MethodHandle.invokeExact.",
        description=("invoke-polymorphic calls methods like MethodHandle.invokeExact, whose declared signature is "
                     "just a placeholder: each call site supplies its own argument and return types. The second "
                     "reference, a prototype, records those types. Any result is collected with move-result*, "
                     "as for other invokes."),
        technical=(enc(ctx, "45cc") + " vC is the receiver (the handle); vD..vG are the arguments. The method "
                   "reference must name a signature-polymorphic method, and the proto describes the types actually "
                   "passed and expected back. Exceptions are those of the invoked method: for invokeExact a type "
                   "mismatch throws WrongMethodTypeException, a null receiver throws NullPointerException, and "
                   "anything the target throws propagates. The instruction formats page lists only A = 1..5 for "
                   "45cc."),
        example=ex("""
            # int len = (int) lengthHandle.invokeExact("hello");
            .method public static callLength(Ljava/lang/invoke/MethodHandle;)I
                .locals 1
                const-string v0, "hello"
                invoke-polymorphic {p0, v0}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (Ljava/lang/String;)I
                move-result v0
                return v0
            .end method
        """),
        explanation=("The method reference is always invokeExact([Object)Object, but the proto (Ljava/lang/String;)I "
                     "says this call passes one String and expects an int back, so move-result (not "
                     "move-result-object) collects the result."),
        java='int len = (int) lengthHandle.invokeExact("hello");',
        kotlin='val len = lengthHandle.invokeExact("hello") as Int',
        registers=("Reads the receiver vC and up to four argument registers (v0–v15 each). The result, if any, is "
                   "collected with move-result*. " + P_REG_NOTE),
        mistakes=["Using it for an ordinary method: only signature-polymorphic methods are allowed; everything "
                  "else uses invoke-virtual.",
                  "Writing a proto that doesn't match the handle's type exactly when calling invokeExact: the call "
                  "throws WrongMethodTypeException.",
                  "Choosing the move-result variant from the method reference's return type (Object) instead of "
                  "the proto's return type.",
                  "Assembling for an API level below 26: the dex version must be 038 or later."],
        related=["invoke-polymorphic/range", "invoke-custom", "const-method-handle", "const-method-type",
                 "invoke-virtual"],
        tags=["method-handle", "invoke", "polymorphic", "java.lang.invoke"],
        compat_extra=poly_compat, refs_extra=[REF_DEX_MAGIC, REF_DEX_PROTO, REF_MH_API, REF_VH_API]))
    out.append(make(
        ctx, "invoke-polymorphic/range", "method_handle",
        syntax="invoke-polymorphic/range {vCCCC .. vNNNN}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (params)ret",
        operands=[opnd("AA", "AA", 8, "argument word count, 1–255."),
                  opnd("vCCCC", "CCCC", 16, "first register of the range: the receiver handle, v0–v65535."),
                  derived("vNNNN", "last register of the range, NNNN = CCCC + AA - 1."),
                  opnd("Lclass;->method(params)ret", "BBBB", 16, "method_ids index (meth@BBBB) of the "
                                                                "signature-polymorphic method."),
                  opnd("(params)ret", "HHHH", 16, "proto_ids index (proto@HHHH) describing this call site's types.")],
        summary="Invoke a signature-polymorphic method with arguments in a register range.",
        description=("This is the range form of invoke-polymorphic. The receiver handle and its arguments come from "
                     "consecutive registers, which allows more argument words and high register numbers. The "
                     "behaviour is otherwise identical."),
        technical=(enc(ctx, "4rcc") + " Registers vCCCC..vCCCC+AA-1 hold the receiver followed by the arguments "
                   "(long/double take two). The method and proto references work as for invoke-polymorphic; so do "
                   "exceptions and move-result* handling."),
        example=ex("""
            # Object r = handle.invoke(a, b);   (handle in v0, a in v1, b in v2)
            invoke-polymorphic/range {v0 .. v2}, Ljava/lang/invoke/MethodHandle;->invoke([Ljava/lang/Object;)Ljava/lang/Object;, (II)Ljava/lang/Object;
            move-result-object v3
        """),
        explanation=("The handle and its two int arguments sit in v0..v2. MethodHandle.invoke (unlike invokeExact) "
                     "may adapt argument and return types to the handle's actual type."),
        java="Object r = handle.invoke(a, b);", kotlin="val r = handle.invoke(a, b)",
        registers="Reads vCCCC..vNNNN (consecutive). The result, if any, is collected with move-result*.",
        mistakes=["Using non-consecutive registers: the range covers exactly vCCCC through vNNNN.",
                  "Forgetting that wide arguments take two registers in the range and in the word count.",
                  "Assembling for an API level below 26."],
        related=["invoke-polymorphic", "invoke-custom/range", "const-method-handle", "invoke-virtual/range"],
        tags=["method-handle", "invoke", "polymorphic", "range", "java.lang.invoke"],
        compat_extra=poly_compat, refs_extra=[REF_DEX_MAGIC, REF_DEX_PROTO, REF_MH_API]))

    custom_tech = (" The instruction runs in two phases. Call site resolution: if the call site has no associated "
                   "java.lang.invoke.CallSite yet, its bootstrap linker method is invoked with the arguments "
                   "stored in the dex file's call_site_item; the CallSite it returns becomes associated with the "
                   "call site, unless another thread got there first, in which case the first association wins. "
                   "Call site invocation: the CallSite's target MethodHandle is invoked as if by invoke-polymorphic "
                   "(an exact invocation) with the instruction's argument registers. Exceptions from the bootstrap "
                   "method are wrapped in BootstrapMethodError, which is also thrown when the bootstrap method "
                   "doesn't return a CallSite, the CallSite's target is null, or the target has the wrong type.")
    custom_compat = ("invoke-custom is rare in typical app code because D8 usually desugars invokedynamic (lambdas, "
                     "string concatenation) into ordinary classes and calls.")
    cs_ref = ("call_site_0(\"name\", (params)ret, extraArgs...)@Lbootstrap/Class;->bootstrap(Ljava/lang/invoke/MethodHandles$Lookup;"
              "Ljava/lang/String;Ljava/lang/invoke/MethodType;...)Ljava/lang/invoke/CallSite;")
    custom_example = ex("""
        # result = <dynamically linked "combine" operation>(a, b)
        invoke-custom {p0, p1}, call_site_0("combine", (II)I)@Lcom/acme/demo/Bootstraps;->link(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
        move-result v0
    """)
    out.append(make(
        ctx, "invoke-custom", "method_handle",
        syntax="invoke-custom {vC, vD, vE, vF, vG}, " + cs_ref,
        operands=[opnd("A", "A", 4, "argument word count, 0–5."),
                  opnd("vC", "C", 4, "first argument register."), opnd("vD", "D", 4, "second argument register."),
                  opnd("vE", "E", 4, "third argument register."), opnd("vF", "F", 4, "fourth argument register."),
                  opnd("vG", "G", 4, "fifth argument register."),
                  opnd("call_site", "BBBB", 16, "call_site_ids index (call_site@BBBB). smali writes it as "
                                                "name(\"methodName\", (proto), extra args...)@bootstrapMethod.")],
        summary="Resolve a call site through its bootstrap method and invoke its target (invokedynamic).",
        description=("invoke-custom is Dalvik's counterpart of the JVM's invokedynamic. The first time it runs, a "
                     "bootstrap method decides what the call site should do and returns a CallSite. Every "
                     "execution then calls that CallSite's target method handle with the given arguments."),
        technical=enc(ctx, "35c") + custom_tech + " The result, if any, is retrieved with move-result*.",
        example=custom_example,
        explanation=("On first execution Bootstraps.link receives the lookup, the name \"combine\" and the type "
                     "(II)I and returns a CallSite; the target is then invoked with p0 and p1, and the int result "
                     "is read with move-result."),
        java="// no direct Java syntax: emitted for invokedynamic call sites (e.g. lambdas or string concatenation "
             "when they are not desugared)",
        kotlin=None,
        registers=("Reads up to five argument registers (v0–v15). Writes no register directly; the result, if any, "
                   "is collected with move-result*."),
        mistakes=["Expecting a receiver: invoke-custom has no implicit this; all registers are arguments matching "
                  "the call site's method type.",
                  "Writing a bootstrap method that returns null or a CallSite of the wrong type, which leads to "
                  "BootstrapMethodError.",
                  "Assuming the bootstrap method runs on every call: it runs once, when the call site is first "
                  "resolved.",
                  "Assembling for an API level below 26: requires dex 038."],
        related=["invoke-custom/range", "invoke-polymorphic", "const-method-handle", "const-method-type",
                 "invoke-static"],
        tags=["method-handle", "invoke", "invokedynamic", "call-site", "java.lang.invoke"],
        compat_extra=custom_compat, refs_extra=[REF_DEX_MAGIC, REF_DEX_CALLSITE]))
    out.append(make(
        ctx, "invoke-custom/range", "method_handle",
        syntax="invoke-custom/range {vCCCC .. vNNNN}, " + cs_ref,
        operands=[opnd("AA", "AA", 8, "argument word count, 0–255."),
                  opnd("vCCCC", "CCCC", 16, "first argument register, v0–v65535."),
                  derived("vNNNN", "last argument register, NNNN = CCCC + AA - 1."),
                  opnd("call_site", "BBBB", 16, "call_site_ids index (call_site@BBBB).")],
        summary="invoke-custom with its arguments taken from a consecutive register range.",
        description=("This is the range form of invoke-custom. The call site is resolved and invoked in exactly "
                     "the same way, but the arguments come from consecutive registers, so you can pass more than "
                     "five words or use high registers."),
        technical=enc(ctx, "3rc") + custom_tech + " Arguments are read from vCCCC..vCCCC+AA-1.",
        example=ex("""
            invoke-custom/range {v16 .. v17}, call_site_1("combine", (II)I)@Lcom/acme/demo/Bootstraps;->link(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
            move-result v0
        """),
        explanation="The two int arguments live in v16 and v17, beyond 4-bit register reach, so the range form is used.",
        java="// no direct Java syntax (invokedynamic call site)", kotlin=None,
        registers="Reads vCCCC..vNNNN (consecutive). The result, if any, is collected with move-result*.",
        mistakes=["Using non-consecutive registers.",
                  "Miscounting words for long/double arguments.",
                  "Assembling for an API level below 26."],
        related=["invoke-custom", "invoke-polymorphic/range", "invoke-static/range"],
        tags=["method-handle", "invoke", "invokedynamic", "call-site", "range", "java.lang.invoke"],
        compat_extra=custom_compat, refs_extra=[REF_DEX_MAGIC, REF_DEX_CALLSITE]))
    out.append(make(
        ctx, "const-method-handle", "method_handle",
        syntax="const-method-handle vAA, invoke-static@Lclass;->method(params)ret",
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives a java.lang.invoke.MethodHandle."),
                  opnd("kind@member", "BBBB", 16, "method_handles index (method_handle@BBBB). smali writes the "
                                                  "handle type and member, e.g. invoke-static@Lfoo;->bar()V or "
                                                  "static-get@Lfoo;->FIELD:I.")],
        summary="Load a MethodHandle constant from the dex method_handles table into vAA.",
        description=("const-method-handle loads a ready-made MethodHandle for a method or field accessor that the "
                     "dex file declares as a constant. It is the counterpart of loading a method handle constant "
                     "from the class-file constant pool. Code can then call the handle with invoke-polymorphic."),
        technical=(enc(ctx, "21c") + " The method_handle_item gives the handle type (static-put, static-get, "
                   "instance-put, instance-get, invoke-static, invoke-instance, invoke-constructor, invoke-direct or "
                   "invoke-interface) and the field or method it refers to. Resolving the handle can throw linkage "
                   "errors, for example when the member doesn't exist or isn't accessible."),
        example=ex("""
            const-method-handle v0, invoke-static@Ljava/lang/Integer;->toString(I)Ljava/lang/String;
            const/16 v1, 0x2a
            invoke-polymorphic {v0, v1}, Ljava/lang/invoke/MethodHandle;->invokeExact([Ljava/lang/Object;)Ljava/lang/Object;, (I)Ljava/lang/String;
            move-result-object v2
        """),
        explanation=("v0 receives a handle to Integer.toString(int), which is then called exactly with 42; the "
                     "String result lands in v2."),
        java=("// closest Java (runtime lookup, not a constant): MethodHandles.lookup().findStatic(Integer.class, "
              "\"toString\", MethodType.methodType(String.class, int.class))"),
        kotlin=None,
        registers="Writes vAA (v0–v255).",
        mistakes=["Assembling for an API level below 28: requires dex 039.",
                  "Picking the wrong handle kind (e.g. invoke-instance for a static method), which fails "
                  "resolution.",
                  "Calling the handle with invoke-virtual instead of invoke-polymorphic."],
        related=["const-method-type", "invoke-polymorphic", "invoke-custom", "const-class"],
        tags=["method-handle", "constant", "java.lang.invoke"],
        compat_extra="java.lang.invoke.MethodHandle itself is in the public SDK from API 26.",
        refs_extra=[REF_DEX_MAGIC, REF_DEX_MH, REF_MH_API]))
    out.append(make(
        ctx, "const-method-type", "method_handle",
        syntax="const-method-type vAA, (params)ret",
        operands=[opnd("vAA", "AA", 8, "destination register, v0–v255; receives a java.lang.invoke.MethodType."),
                  opnd("(params)ret", "BBBB", 16, "proto_ids index (proto@BBBB) of the method prototype, e.g. "
                                                  "(ILjava/lang/String;)Ljava/lang/String;.")],
        summary="Load a MethodType constant for a method prototype into vAA.",
        description=("const-method-type turns a method prototype from the dex file into a "
                     "java.lang.invoke.MethodType object. MethodType values describe the parameter and return types "
                     "of method handles, for example to check or adapt a handle's type."),
        technical=(enc(ctx, "21c") + " Resolves the proto_id at proto@BBBB and writes the MethodType reference to "
                   "vAA. Resolution can throw linkage errors (such as NoClassDefFoundError) if a type in the "
                   "prototype cannot be resolved."),
        example=ex("""
            const-method-type v0, (ILjava/lang/String;)Ljava/lang/String;
            invoke-virtual {p1, v0}, Ljava/lang/invoke/MethodHandle;->asType(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;
            move-result-object v1
        """),
        explanation="The MethodType (int, String) -> String is loaded as a constant and used to adapt the handle in p1.",
        java="MethodType t = MethodType.methodType(String.class, int.class, String.class);",
        kotlin="val t = MethodType.methodType(String::class.java, Int::class.java, String::class.java)   // Int::class.java is int.class",
        registers="Writes vAA (v0–v255).",
        mistakes=["Assembling for an API level below 28: requires dex 039.",
                  "Writing the prototype with dotted class names instead of descriptors.",
                  "Confusing it with const-class: the result is a MethodType, not a Class."],
        related=["const-method-handle", "invoke-polymorphic", "const-class"],
        tags=["method-handle", "constant", "method-type", "java.lang.invoke"],
        compat_extra="java.lang.invoke.MethodType is part of the java.lang.invoke API available from API 26.",
        refs_extra=[REF_DEX_MAGIC, REF_DEX_PROTO]))
    return out


# =================================================================================================================

def build_all(ctx: Context):
    ctx.all_names = set(ctx.spec_by_name) | {"packed-switch-payload", "sparse-switch-payload", "fill-array-data-payload"}
    entries = []
    for gen in (gen_nop, gen_moves, gen_move_results, gen_returns, gen_consts, gen_misc, gen_gotos, gen_switches,
                gen_cmps, gen_ifs, gen_arrays, gen_instance_fields, gen_static_fields, gen_invokes, gen_unops,
                gen_binops, gen_method_handles, gen_payloads):
        entries.extend(gen(ctx))
    entries.sort(key=lambda e: e["opcode"])
    return entries
