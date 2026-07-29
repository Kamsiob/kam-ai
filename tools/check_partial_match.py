#!/usr/bin/env python3
"""Fails when a guard grants an allowance on a partial match.

The pattern, which has now appeared twice in the highest consequence code here:

  1. `isAllowedOutright` exempted any reply that merely *began* with an allowed
     sentence, unbounded. The model is instructed to produce those sentences, so
     everything after one went unguarded.
  2. `isAnsweringItsOwnExample` treated any message that was a *substring* of an
     example as having asked for it, so typing "remember" released a canned answer
     about taking the stairs.

Both are the same shape: **a decision made from part of an input, applied to all of
it.** Neither was a wording mistake, so neither would have been found by reading the
strings, and the second was found only by sweeping for the shape of the first.

What this looks for is narrow on purpose, because a fuzzy rule here would be ignored.
A function whose name says it grants an allowance, containing a containment or prefix
test, with no length or coverage comparison anywhere in it, is flagged. A length
comparison is taken as evidence somebody thought about how much of the input the
decision rests on. That is a proxy rather than a proof, and it is the strongest proxy
available without understanding the code.

Run via tools/check_scripts.sh, which is the single entry point.
"""
import re
import sys
from pathlib import Path

# Functions that decide whether something is permitted. The two defects were in
# `isAllowedOutright` and `isAnsweringItsOwnExample`, and the naming is the only
# reliable signal that a boolean is an allowance rather than a detection.
ALLOWANCE = re.compile(
    r"\bfun\s+(?:[A-Za-z0-9_.]+\.)?"
    r"(is(?:Allowed|Legit|Legitimate|Safe|Exempt|Permitted|Ok|Clean|Trusted|Answering)"
    r"[A-Za-z0-9_]*|allows?[A-Za-z0-9_]*|exempt[A-Za-z0-9_]*)\s*\(",
    re.IGNORECASE,
)

PARTIAL = re.compile(r"\.(startsWith|endsWith|contains|indexOf)\s*\(")
# Any comparison of a size against something, which is what a floor looks like.
FLOOR = re.compile(r"\.(length|size|count)\b[^\n]*[<>]=?|[<>]=?[^\n]*\.(length|size|count)\b")


def function_bodies(text):
    """Yields (name, line_number, body) by brace matching from each match."""
    for m in ALLOWANCE.finditer(text):
        name = m.group(1)
        line_no = text.count("\n", 0, m.start()) + 1
        # Walk forward to the opening brace of the body, then match braces. An
        # expression-bodied function (`= expr`) ends at the blank line after it.
        i = m.end()
        depth_paren = 1
        while i < len(text) and depth_paren > 0:
            if text[i] == "(":
                depth_paren += 1
            elif text[i] == ")":
                depth_paren -= 1
            i += 1
        rest = text[i:]
        brace = rest.find("{")
        equals = rest.find("=")
        if brace != -1 and (equals == -1 or brace < equals):
            depth = 0
            j = i + brace
            start = j
            while j < len(text):
                if text[j] == "{":
                    depth += 1
                elif text[j] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            yield name, line_no, text[start : j + 1]
        else:
            # Expression body: take until a blank line, which is where these end.
            chunk = rest[: rest.find("\n\n")] if "\n\n" in rest else rest[:600]
            yield name, line_no, chunk


def strip_comments(body):
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return "\n".join(
        line for line in body.splitlines() if not line.strip().startswith("//")
    )


def main():
    root = Path(__file__).resolve().parent.parent
    findings = []
    for path in sorted((root / "app/src/main/java").rglob("*.kt")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if not ALLOWANCE.search(text):
            continue
        for name, line_no, body in function_bodies(text):
            code = strip_comments(body)
            if not PARTIAL.search(code):
                continue
            if FLOOR.search(code):
                continue
            # An explicit, reasoned opt-out. Some partial matches are bounded by
            # construction: testing whether a *known* value starts with the input
            # means the input is shorter than the known value and has no remainder.
            # That cannot be seen from the shape, so it has to be stated, and stating
            # it costs a sentence saying why.
            if "partial-match: bounded" in body:
                continue
            findings.append(
                f"{path.relative_to(root)}:{line_no}: {name} grants an allowance from a "
                f"partial match with no length floor"
            )

    if findings:
        print("ALLOWANCE ON A PARTIAL MATCH:", file=sys.stderr)
        for f in findings:
            print(f"  {f}", file=sys.stderr)
        print(
            "\nA check that tests a prefix or a substring and then clears the whole\n"
            "input is the shape of two real defects here. Either bound how much of the\n"
            "input the decision rests on, or strip the matched part and judge the rest.",
            file=sys.stderr,
        )
        return 1
    print("kotlin guards: no allowances granted on an unbounded partial match.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
