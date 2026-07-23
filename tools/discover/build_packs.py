#!/usr/bin/env python3
"""Builds Kam AI Discover content packs from English Wikipedia.

One command:  python3 tools/discover/build_packs.py [--limit N]

It walks the Vital Articles branches configured in packs_config.json (a curated,
high-quality set), pulls each article's introduction through the official
Wikipedia API as plain text, cleans it, filters weak entries, and writes one
versioned SQLite pack file per topic under tools/discover/out/, plus a manifest.

Wikimedia etiquette: a descriptive User-Agent with a contact, modest batching,
small delays, and an on-disk cache so reruns are cheap and do not re-hit the API.

Publishing to a GitHub release is a separate step (publish.sh), so this script is
safe to run and inspect without side effects beyond the out/ and .cache/ dirs.
"""

import argparse
import json
import os
import re
import sqlite3
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
CACHE = os.path.join(HERE, ".cache")
API = "https://en.wikipedia.org/w/api.php"
USER_AGENT = "KamAI-Discover/1.0 (https://github.com/Kamsiob/kam-ai; hello@kamsiob.com)"
PACK_VERSION = 1

os.makedirs(OUT, exist_ok=True)
os.makedirs(CACHE, exist_ok=True)


def api_get(params):
    params = dict(params, format="json")
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            if attempt == 3:
                raise
            time.sleep(1.5 * (attempt + 1))
    return {}


def subpages_of(root):
    """All Vital Articles subpages under a root (namespace 4), plus the root."""
    pages = [root]
    prefix = root.split(":", 1)[1] + "/"
    cont = {}
    while True:
        data = api_get({
            "action": "query", "list": "allpages", "apnamespace": "4",
            "apprefix": prefix, "aplimit": "500", **cont,
        })
        for p in data.get("query", {}).get("allpages", []):
            pages.append(p["title"])
        if "continue" in data:
            cont = data["continue"]
            time.sleep(0.1)
        else:
            break
    return pages


def article_links(page):
    """Namespace-0 article titles from a Vital Articles page. Uses action=parse so
    links added through transcluded sub-lists are included (a plain prop=links
    query misses them)."""
    data = api_get({"action": "parse", "page": page, "prop": "links", "redirects": "1"})
    links = data.get("parse", {}).get("links", [])
    return [l["*"] for l in links if l.get("ns") == 0]


SKIP_PATTERNS = [
    re.compile(r"^List of ", re.I),
    re.compile(r"\(disambiguation\)", re.I),
    re.compile(r"^Index of ", re.I),
    re.compile(r"^Outline of ", re.I),
    re.compile(r"^Timeline of ", re.I),
]


def looks_bad_title(title):
    return any(p.search(title) for p in SKIP_PATTERNS)


LANG_LABEL = re.compile(
    r"(?:Arabic|Persian|Old Persian|Farsi|Dari|Pashto|Latin|Ancient Greek|Greek|Hebrew|"
    r"Sanskrit|Chinese|Japanese|Korean|Russian|Turkish|Urdu|Hindi|Egyptian|Coptic|Syriac|"
    r"romani[sz]ed|romani[sz]ation|pronounced|pronunciation|IPA|lit\.|listen|born\b)",
    re.I,
)


def _non_ascii_ratio(s):
    if not s:
        return 0.0
    return sum(1 for c in s if ord(c) > 127) / len(s)


def _strip_parens(text):
    # Remove parentheticals that are empty, punctuation-only, pronunciation or
    # foreign-script clutter, without touching ones that carry real meaning.
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "(":
            depth = 1
            j = i + 1
            while j < n and depth:
                if text[j] == "(":
                    depth += 1
                elif text[j] == ")":
                    depth -= 1
                j += 1
            inner = text[i + 1:j - 1] if depth == 0 else text[i + 1:j]
            stripped = inner.strip(" ,;:·")
            drop = (
                not stripped
                or len(stripped) < 4
                or stripped.lower() in ("or", "and", "also", "or ", "and/or")
                or LANG_LABEL.search(inner)
                or "/" in inner and len(inner) < 40
                or _non_ascii_ratio(inner) > 0.25
            )
            if not drop:
                out.append("(" + inner + ")")
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def clean(text):
    if not text:
        return ""
    text = re.sub(r"\[[0-9]+\]", "", text)  # [1] style refs
    text = _strip_parens(text)
    # Tidy the punctuation a dropped parenthetical can leave behind.
    text = re.sub(r"\s+([,;.])", r"\1", text)
    text = re.sub(r"\(\s*\)", "", text)
    text = re.sub(r"\s{2,}", " ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r" +\n", "\n", text)
    return text.strip()


def cache_path(title):
    safe = re.sub(r"[^A-Za-z0-9_.-]", "_", title)[:150]
    return os.path.join(CACHE, safe + ".json")


def fetch_intro_batch(titles):
    """Batched plain-text intros. Returns {title: extract}. Uses cache."""
    result = {}
    to_fetch = []
    for t in titles:
        cp = cache_path(t)
        if os.path.exists(cp):
            with open(cp) as f:
                result[t] = json.load(f).get("extract", "")
        else:
            to_fetch.append(t)
    for i in range(0, len(to_fetch), 20):
        batch = to_fetch[i:i + 20]
        data = api_get({
            "action": "query", "prop": "extracts", "explaintext": "1",
            "exintro": "1", "exlimit": "20", "redirects": "1",
            "titles": "|".join(batch),
        })
        pages = data.get("query", {}).get("pages", {})
        # Map back through any redirect normalization.
        norm = {n["from"]: n["to"] for n in data.get("query", {}).get("normalized", [])}
        redir = {r["from"]: r["to"] for r in data.get("query", {}).get("redirects", [])}
        got = {}
        for p in pages.values():
            got[p.get("title", "")] = p.get("extract", "") or ""
        for t in batch:
            key = redir.get(norm.get(t, t), norm.get(t, t))
            extract = got.get(key, got.get(t, ""))
            result[t] = extract
            with open(cache_path(t), "w") as f:
                json.dump({"title": t, "extract": extract}, f)
        time.sleep(0.15)
    return result


def preview_of(passage, lo=120, hi=200):
    words = passage.split()
    if len(words) <= hi:
        return passage
    # Cut at a sentence boundary near the target if possible.
    cut = " ".join(words[:hi])
    m = re.search(r"^(.*[.!?])\s", cut[::-1])
    # Simpler: find last sentence end within [lo, hi] words.
    joined = " ".join(words[:hi])
    end = max(joined.rfind(". "), joined.rfind("! "), joined.rfind("? "))
    if end > len(" ".join(words[:lo])):
        return joined[:end + 1]
    return joined


def build_pack(pack, exclude, target, min_words, limit):
    print(f"\n== Pack: {pack['name']} ==")
    candidates = []
    seen = set()
    for root in pack["roots"]:
        print(f"  walking {root}")
        for page in subpages_of(root):
            for title in article_links(page):
                if title in seen or looks_bad_title(title) or title in exclude:
                    continue
                seen.add(title)
                candidates.append(title)
    print(f"  {len(candidates)} candidate articles")
    if limit:
        candidates = candidates[:limit]

    intros = {}
    # Fetch in chunks to show progress.
    for i in range(0, len(candidates), 200):
        chunk = candidates[i:i + 200]
        intros.update(fetch_intro_batch(chunk))
        print(f"  fetched {min(i + 200, len(candidates))}/{len(candidates)} intros", end="\r")
    print()

    rows = []
    for title in candidates:
        passage = clean(intros.get(title, ""))
        if len(passage.split()) < min_words:
            continue
        if "may refer to" in passage[:80].lower():
            continue
        rows.append({
            "id": re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-"),
            "title": title,
            "topic": pack["name"],
            "preview": preview_of(passage),
            "passage": passage,
            "source_title": title,
            "source_url": "https://en.wikipedia.org/wiki/" + urllib.parse.quote(title.replace(" ", "_")),
            "license": "CC BY-SA 4.0",
            "pack_version": PACK_VERSION,
        })
        if len(rows) >= target:
            break

    write_pack(pack["id"], rows)
    print(f"  wrote {len(rows)} moments")
    return len(rows)


def write_pack(pack_id, rows):
    path = os.path.join(OUT, f"{pack_id}-v{PACK_VERSION}.kampack")
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.execute("""CREATE TABLE moments(
        id TEXT PRIMARY KEY, title TEXT, topic TEXT, preview TEXT, passage TEXT,
        source_title TEXT, source_url TEXT, license TEXT, pack_version INTEGER)""")
    db.executemany(
        "INSERT OR REPLACE INTO moments VALUES(:id,:title,:topic,:preview,:passage,"
        ":source_title,:source_url,:license,:pack_version)", rows)
    db.commit()
    db.close()
    return path


def sha256(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="cap candidates per pack (for a quick test run)")
    ap.add_argument("--release-tag", default="discover-packs-v1", help="GitHub release tag for download URLs")
    ap.add_argument("--repo", default="Kamsiob/kam-ai")
    args = ap.parse_args()

    with open(os.path.join(HERE, "packs_config.json")) as f:
        config = json.load(f)
    exclude = set()
    with open(os.path.join(HERE, "exclude.txt")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                exclude.add(line)

    manifest = {"version": PACK_VERSION, "packs": []}
    counts = {}
    for pack in config["packs"]:
        n = build_pack(pack, exclude, config["target_per_pack"], config["min_words"], args.limit)
        counts[pack["id"]] = n
        path = os.path.join(OUT, f"{pack['id']}-v{PACK_VERSION}.kampack")
        manifest["packs"].append({
            "id": pack["id"],
            "name": pack["name"],
            "description": pack["description"],
            "moments": n,
            "sizeBytes": os.path.getsize(path),
            "version": PACK_VERSION,
            "fileName": f"{pack['id']}-v{PACK_VERSION}.kampack",
            "downloadUrl": f"https://github.com/{args.repo}/releases/download/{args.release_tag}/{pack['id']}-v{PACK_VERSION}.kampack",
            "sha256": sha256(path),
        })

    with open(os.path.join(OUT, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)

    print("\n== Manifest ==")
    print(json.dumps(manifest, indent=2))
    print("\n== Counts ==", counts)

    # Twenty sample cards from the first pack for a skim.
    first = config["packs"][0]
    db = sqlite3.connect(os.path.join(OUT, f"{first['id']}-v{PACK_VERSION}.kampack"))
    print(f"\n== 20 sample cards from {first['name']} ==")
    for title, preview in db.execute("SELECT title, preview FROM moments LIMIT 20"):
        print(f"\n--- {title} ---\n{preview[:280]}...")
    db.close()


if __name__ == "__main__":
    main()
