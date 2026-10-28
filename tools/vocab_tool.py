#!/usr/bin/env python3
"""
Безопасное пополнение словарей LingvoIsland (только стандартная библиотека Python).

  python3 tools/vocab_tool.py validate
  python3 tools/vocab_tool.py add de new_words.json      # массив карточек или {"entries": [...]}
  python3 tools/vocab_tool.py add es - < new_words.json  # из stdin
  python3 tools/vocab_tool.py stats

Правила (см. CLAUDE.md): ID существующих карточек не меняются, записи не удаляются,
дубликаты (без учёта регистра/пробелов/Unicode-формы, с артиклем) не добавляются,
пустые поля не стирают заполненные, перед записью создаётся резервная копия.
"""
import datetime
import json
import os
import re
import shutil
import sys
import unicodedata
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOCAB_DIR = os.path.join(ROOT, "vocabulary")
BACKUP_DIR = os.path.join(VOCAB_DIR, ".backups")

LANGS = {
    "es": {"file": "spanish_vocabulary.json", "term": "spanish", "example": "example_es", "articles": {"el", "la", "los", "las"}},
    "de": {"file": "german_vocabulary.json", "term": "german", "example": "example_de", "articles": {"der", "die", "das"}},
}
REQUIRED_ORDER = ["id", "TERM", "russian", "article", "plural", "part_of_speech", "EXAMPLE", "example_ru",
                  "grammar_notes", "collocations", "verb_forms", "regional_usage", "level", "tags", "notes",
                  "source", "created_at", "updated_at"]
EDGE = re.compile(r"^[¿¡\"'«»„“”.,;:!?\s]+|[\"'«»„“”.,;:!?\s]+$")


def now_iso():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def clean(s):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", str(s or "")).replace(" ", " ")).strip()


def key(term, article=""):
    t, a = clean(term), clean(article).lower()
    if a and not t.lower().startswith(a + " "):
        t = f"{a} {t}"
    return re.sub(r"\s+", " ", EDGE.sub("", t.lower())).strip()


def path(lang):
    return os.path.join(VOCAB_DIR, LANGS[lang]["file"])


def load(lang):
    with open(path(lang), encoding="utf-8") as f:
        doc = json.load(f)
    if doc.get("language") != lang:
        raise SystemExit(f"{LANGS[lang]['file']}: language={doc.get('language')!r}, ожидался {lang!r}")
    return doc


def ordered(lang, e):
    spec = LANGS[lang]
    names = [spec["term"] if n == "TERM" else spec["example"] if n == "EXAMPLE" else n for n in REQUIRED_ORDER]
    out = {n: e[n] for n in names if n in e}
    out.update({k: v for k, v in e.items() if k not in out})
    return out


def validate_doc(lang, doc):
    spec, errors = LANGS[lang], []
    if doc.get("schema_version") != 1:
        errors.append("schema_version должен быть 1")
    entries = doc.get("entries")
    if not isinstance(entries, list):
        return errors + ["entries должен быть массивом"]
    ids, keys = set(), set()
    for i, e in enumerate(entries):
        label = f"#{i + 1} {e.get(spec['term'], '')!r}"
        if not clean(e.get("id")):
            errors.append(f"{label}: нет id")
        elif e["id"] in ids:
            errors.append(f"{label}: повтор id {e['id']}")
        ids.add(e.get("id"))
        if not clean(e.get(spec["term"])):
            errors.append(f"{label}: нет поля {spec['term']}")
        if not clean(e.get("russian")):
            errors.append(f"{label}: нет перевода russian")
        art = clean(e.get("article")).lower()
        if art and art not in spec["articles"]:
            errors.append(f"{label}: недопустимый артикль {art!r}")
        if lang == "de" and e.get("part_of_speech") == "noun" and not art:
            errors.append(f"{label}: у немецкого существительного нет артикля")
        k = key(e.get(spec["term"], ""), art)
        if k in keys:
            errors.append(f"{label}: дубликат выражения")
        keys.add(k)
    return errors


def backup(lang):
    os.makedirs(BACKUP_DIR, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    dst = os.path.join(BACKUP_DIR, f"{lang}-{stamp}.json")
    shutil.copy2(path(lang), dst)
    return dst


def save(lang, doc):
    errors = validate_doc(lang, doc)
    if errors:
        raise SystemExit("Сохранение отменено, ошибки:\n  " + "\n  ".join(errors))
    if not doc["entries"]:
        raise SystemExit("Сохранение отменено: словарь не может стать пустым.")
    tmp = path(lang) + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    os.replace(tmp, path(lang))


def add(lang, source):
    spec = LANGS[lang]
    raw = json.load(sys.stdin if source == "-" else open(source, encoding="utf-8"))
    incoming = raw.get("entries", []) if isinstance(raw, dict) else raw
    if not isinstance(incoming, list) or not incoming:
        raise SystemExit("Нет новых карточек — словарь не изменён.")
    doc = load(lang)
    entries = doc["entries"]
    by_id = {e["id"]: e for e in entries}
    by_key = {key(e[spec["term"]], e.get("article", "")): e for e in entries}
    added, updated, skipped = [], [], []
    ts = now_iso()
    for item in incoming:
        term = clean(item.get(spec["term"]) or item.get("term"))
        if not term or not clean(item.get("russian")):
            skipped.append(f"{term or item!r}: нет выражения или перевода")
            continue
        item = dict(item)
        item.pop("term", None)
        item[spec["term"]] = term
        art = clean(item.get("article")).lower()
        if lang == "de" and not art and item.get("part_of_speech") == "noun":
            first = term.split(" ")[0].lower()
            if first in spec["articles"] and " " in term:
                art = first
        item["article"] = art
        existing = by_id.get(item.get("id")) or by_key.get(key(term, art))
        if existing:
            changed = []
            for k, v in item.items():
                if k in ("id", "created_at", "updated_at", spec["term"]):
                    continue
                if k in ("tags", "collocations") and isinstance(v, list):
                    merged = list(existing.get(k) or [])
                    for x in v:
                        if all(clean(x).lower() != clean(y).lower() for y in merged):
                            merged.append(x)
                    if merged != (existing.get(k) or []):
                        existing[k] = merged; changed.append(k)
                    continue
                empty = v in ("", None, [], {})
                if empty or v == existing.get(k):
                    continue
                if k == "notes" and existing.get("notes"):
                    skipped.append(f"{term}: notes не перезаписаны (есть личная заметка)")
                    continue
                existing[k] = v; changed.append(k)
            if changed:
                existing["updated_at"] = ts
                updated.append(f"{existing['id']} {term}: {', '.join(changed)}")
            continue
        new_id = item.get("id") if item.get("id") and item["id"] not in by_id else f"{lang}-{uuid.uuid4().hex[:12]}"
        entry = {"id": new_id, spec["term"]: term, "russian": clean(item["russian"]), "article": art,
                 "plural": clean(item.get("plural")), "part_of_speech": clean(item.get("part_of_speech")),
                 spec["example"]: clean(item.get(spec["example"]) or item.get("example")),
                 "example_ru": clean(item.get("example_ru")), "grammar_notes": item.get("grammar_notes", ""),
                 "collocations": item.get("collocations", []), "level": clean(item.get("level")).upper(),
                 "tags": item.get("tags", []), "notes": item.get("notes", ""), "source": item.get("source") or "Claude"}
        for extra in ("verb_forms", "regional_usage"):
            if item.get(extra):
                entry[extra] = item[extra]
        if lang == "es" and not entry["plural"]:
            entry.pop("plural")
        entry["created_at"] = ts
        entry["updated_at"] = ts
        entry = ordered(lang, entry)
        entries.append(entry)
        by_id[new_id] = entry
        by_key[key(term, art)] = entry
        added.append(f"{new_id} {term}")
    if not added and not updated:
        print("Изменений нет.")
        for s in skipped:
            print("  пропущено:", s)
        return
    bk = backup(lang)
    doc["updated_at"] = ts
    save(lang, doc)
    print(f"Резервная копия: {os.path.relpath(bk, ROOT)}")
    print(f"Добавлено: {len(added)}")
    for a in added:
        print("  +", a)
    print(f"Изменено: {len(updated)}")
    for u in updated:
        print("  ~", u)
    for s in skipped:
        print("  пропущено:", s)


def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    cmd = sys.argv[1]
    if cmd == "validate":
        bad = False
        for lang in LANGS:
            errs = validate_doc(lang, load(lang))
            print(f"{LANGS[lang]['file']}: {'OK' if not errs else str(len(errs)) + ' ошибок'}")
            for e in errs:
                print("  ", e)
            bad |= bool(errs)
        sys.exit(1 if bad else 0)
    elif cmd == "add" and len(sys.argv) == 4 and sys.argv[2] in LANGS:
        add(sys.argv[2], sys.argv[3])
    elif cmd == "stats":
        for lang in LANGS:
            doc = load(lang)
            print(f"{LANGS[lang]['file']}: {len(doc['entries'])} карточек, обновлён {doc.get('updated_at')}")
    else:
        print(__doc__); sys.exit(2)


if __name__ == "__main__":
    main()
