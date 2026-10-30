#!/usr/bin/env python3
"""Отправляет в Telegram события с notify: true, добавленные последним пушем.

Кладётся в репозиторий данных как .github/scripts/notify.py и запускается
из .github/workflows/notify.yml. Только stdlib.
"""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

IMPORTANCE_EMOJI = {"critical": "🔴", "high": "🟠", "medium": "🟡", "low": "⚪️"}
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
# Предохранитель: один запуск не должен превратиться в поток сообщений,
# даже если диапазон пуша определился неправильно.
MAX_MESSAGES = 15


def run(*args: str) -> str:
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def commit_exists(ref: str) -> bool:
    try:
        run("git", "cat-file", "-e", f"{ref}^{{commit}}")
        return True
    except subprocess.CalledProcessError:
        return False


def base_commit(after: str) -> str:
    """Начало диапазона пуша.

    Обычно это github.event.before. Если его нет (ручной запуск, force-push,
    первый пуш в ветку) — берём предыдущий коммит, а не пустое дерево:
    иначе уведомления ушли бы по всем событиям за всю историю.
    """
    before = os.environ.get("EVENT_BEFORE", "")
    if before and not re.fullmatch(r"0{40}", before) and commit_exists(before):
        return before
    if commit_exists(f"{after}~1"):
        print("Диапазон пуша неизвестен — сравниваю с предыдущим коммитом.")
        return f"{after}~1"
    return EMPTY_TREE


def added_lines(before: str, after: str) -> list[tuple[str, str]]:
    """Строки, добавленные в data/*/events.jsonl: пары (asset_id, строка)."""
    diff = run(
        "git", "diff", "--unified=0", "--no-color", before, after, "--", "data/*/events.jsonl"
    )

    result: list[tuple[str, str]] = []
    asset_id = ""
    for line in diff.splitlines():
        header = re.match(r"^\+\+\+ b/data/([^/]+)/events\.jsonl$", line)
        if header:
            asset_id = header.group(1)
            continue
        if line.startswith("+++") or line.startswith("---"):
            continue
        if line.startswith("+") and asset_id:
            result.append((asset_id, line[1:]))
    return result


def muted_assets() -> set[str]:
    """Заглушённые бумаги из Cloudflare Worker. Недоступен — считаем, что их нет."""
    url = os.environ.get("MUTES_URL", "").strip()
    secret = os.environ.get("MUTES_SECRET", "").strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not url or not secret or not chat_id:
        return set()

    request = urllib.request.Request(
        f"{url}?{urllib.parse.urlencode({'chat_id': chat_id})}",
        headers={"X-Mutes-Secret": secret},
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            payload = json.load(response)
        return {str(item).lower() for item in payload.get("muted", [])}
    except (urllib.error.URLError, ValueError, TimeoutError) as error:
        print(f"Список заглушённых бумаг не получен: {error}", file=sys.stderr)
        return set()


def escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def shorten(text: str, limit: int = 240) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def asset_titles() -> dict[str, str]:
    """id бумаги → как её называть в сообщении."""
    try:
        with open("config/watchlist.json", encoding="utf-8") as handle:
            watchlist = json.load(handle)
    except (OSError, ValueError):
        return {}

    titles: dict[str, str] = {}
    for section in watchlist.get("sections", []):
        for asset in section.get("assets", []):
            asset_id = str(asset.get("id") or asset.get("ticker") or "")
            if not asset_id:
                continue
            ticker = asset.get("ticker", asset_id)
            name = asset.get("short_name") or asset.get("name") or ticker
            titles[asset_id] = f"{ticker} · {name}" if ticker != name else str(ticker)
    return titles


def build_message(event: dict, asset_id: str, titles: dict[str, str]) -> str:
    emoji = IMPORTANCE_EMOJI.get(str(event.get("importance", "low")), "⚪️")
    title = escape(str(event.get("title", "Без заголовка")))
    asset = escape(titles.get(asset_id, asset_id))
    summary = escape(shorten(str(event.get("summary", ""))))

    lines = [f"{emoji} <b>{asset}</b>", title]
    if summary:
        lines.append(summary)
    return "\n".join(lines)


def deep_link(asset_id: str) -> str | None:
    link = os.environ.get("MINIAPP_LINK", "").strip().rstrip("/")
    if not link:
        return None
    return f"{link}?startapp={urllib.parse.quote(asset_id)}"


def send(token: str, chat_id: str, text: str, url: str | None) -> None:
    payload: dict[str, object] = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }
    if url:
        payload["reply_markup"] = {"inline_keyboard": [[{"text": "Открыть", "url": url}]]}

    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/sendMessage",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        body = json.load(response)
    if not body.get("ok"):
        raise RuntimeError(f"Telegram отказал: {body}")


def main() -> int:
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat_id:
        print("Нет TELEGRAM_BOT_TOKEN или TELEGRAM_CHAT_ID — уведомления пропущены.")
        return 0

    after = os.environ.get("EVENT_AFTER", "HEAD")
    before = base_commit(after)
    lines = added_lines(before, after)
    if not lines:
        print("Новых строк в events.jsonl нет.")
        return 0

    muted = muted_assets()
    titles = asset_titles()
    sent = 0
    seen: set[str] = set()

    for asset_id, raw in lines:
        text = raw.strip()
        if not text or text.startswith("#") or text.startswith("//"):
            continue
        try:
            event = json.loads(text)
        except ValueError as error:
            print(f"Строка пропущена ({asset_id}): {error}", file=sys.stderr)
            continue

        if not event.get("notify"):
            continue
        if asset_id.lower() in muted:
            print(f"{asset_id}: заглушена, пропускаю «{event.get('title')}»")
            continue

        # Один и тот же id мог прийти несколькими коммитами одного пуша.
        key = f"{asset_id}:{event.get('id') or event.get('title')}"
        if key in seen:
            continue
        seen.add(key)

        if sent >= MAX_MESSAGES:
            print(
                f"Достигнут предел в {MAX_MESSAGES} сообщений за запуск, "
                "остальные события пропущены.",
                file=sys.stderr,
            )
            break

        send(token, chat_id, build_message(event, asset_id, titles), deep_link(asset_id))
        sent += 1

    print(f"Отправлено сообщений: {sent}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
