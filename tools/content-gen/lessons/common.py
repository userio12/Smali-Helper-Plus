"""Small helpers used by the lesson source modules (beginner.py, intermediate.py, advanced.py).

Every helper returns plain dicts that match the Kotlin DTOs in
app/src/main/java/com/riyadm/smalihelperplus/core/content/ContentModels.kt (LessonDto, LessonSectionDto,
ExerciseDto, QuizQuestionDto, FlashcardDto). Multi-line strings are dedented and stripped automatically, so
code can be written as indented triple-quoted blocks.

Keys starting with an underscore (e.g. "_check") are generator-only metadata and are removed before the JSON
is written.
"""
import textwrap


def _clean(s):
    if s is None:
        return None
    return textwrap.dedent(s).strip("\n").rstrip()


def _para(s):
    """Prose: dedent, then join hard-wrapped lines inside a paragraph. Blank lines separate paragraphs and lines
    starting with "- " or "1. " style markers are kept as list items."""
    s = _clean(s)
    out = []
    for block in s.split("\n\n"):
        lines = [l.strip() for l in block.split("\n") if l.strip()]
        merged = []
        for line in lines:
            is_item = line.startswith(("- ", "* ", "• ")) or (len(line) > 2 and line[0].isdigit() and line[1:3] in (". ", ") "))
            if merged and not is_item and not merged[-1].endswith(":"):
                merged[-1] = merged[-1] + " " + line
            else:
                merged.append(line)
        out.append("\n".join(merged))
    return "\n\n".join(out)


def text(body, title=None):
    return {"type": "text", "title": title, "body": _para(body)}


def tip(body, title=None):
    return {"type": "tip", "title": title, "body": _para(body)}


def warning(body, title=None):
    return {"type": "warning", "title": title, "body": _para(body)}


def code(body, title=None, check=True):
    """Smali code. check=False only for intentionally invalid/illustrative listings (say why in the title/text)."""
    return {"type": "code", "title": title, "body": _clean(body), "_check": check}


def java(body, title=None):
    return {"type": "java", "title": title, "body": _clean(body)}


def exercise(id, prompt, solution, starter=None, hint=None, check_starter=True):
    """Smali-looking starter code and solutions are assembled by generate.py. Set check_starter=False only for
    "find the bug" exercises whose starter code is intentionally invalid."""
    return {
        "id": id,
        "prompt": _para(prompt),
        "starterCode": _clean(starter),
        "solution": _clean(solution),
        "hint": _para(hint) if hint else None,
        "_check_starter": check_starter,
    }


def question(id, question, options, correct, explanation, code=None):
    assert 0 <= correct < len(options), id
    return {
        "id": id,
        "question": _para(question),
        "code": _clean(code),
        "options": [o.strip() for o in options],
        "correctIndex": correct,
        "explanation": _para(explanation),
    }


def lesson(id, level, order, title, summary, minutes, sections, exercises, quiz, related):
    return {
        "id": id,
        "level": level,
        "order": order,
        "title": title,
        "summary": _para(summary),
        "estimatedMinutes": minutes,
        "sections": sections,
        "exercises": exercises,
        "quiz": quiz,
        "relatedOpcodes": related,
    }


def card(id, topic, front, back, code=None):
    return {"id": id, "topic": topic, "front": _para(front), "back": _para(back), "code": _clean(code)}
