"""The LotusTok assistant: answers questions about the mod, and only those.

A conversation starts with a command in a chat (`ии-помощь`) and lasts while the person keeps
writing; it ends with `ии-стоп`, or after a quiet quarter of an hour. Inside a conversation
the assistant answers that person's messages in that chat and remembers the last few turns.
Nobody else's messages are read by it.

It knows what is in `knowledge.md` and nothing else, and it says plainly when it is not sure.

The gateway is OpenAI-compatible. `ai.json` beside this file holds its address and key,
written by hand on the server and never kept in the repository. The models are tried in
turn: when one fails, the next answers, so one dead model does not silence the bot.

Nothing about a failure reaches the people asking. They get "не получилось ответить";
the details go to the owner in a private message.
"""

import json
import os
import re
import threading
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "ai.json")
KNOWLEDGE = os.path.join(HERE, "knowledge.md")

#: tried in this order; the first one that answers wins
MODELS = [
    "deepseek-v4.1-flash-1017",
    "anthropic/claude-sonnet-5",
    "qwen3.6-27b",
    "openai/gpt-oss-120b",
]

#: how many questions one person may ask in this many seconds
EACH = 6
WINDOW = 600

#: a conversation ends after this long without a message from its person
IDLE = 15 * 60

#: how many earlier turns of a conversation go back to the model with each question
REMEMBER = 8

#: answers are kept short: a long one is a wall of text in a topic
MOST_CHARS = 1500

NOTE = "Ответ ИИ-помощника LotusTok. Он может ошибаться, проверяйте важное в FAQ."

RULES = """Ты — ИИ-помощник LotusTok в Telegram. Ты отвечаешь только на вопросы про мод LotusTok
(TikTok с настройками): функции, подтверждение аккаунта, значки, обновления, группу и бота.

Правила:
- Используй только базу знаний ниже. Не выдумывай функции, кнопки и ссылки.
- Если вопрос не про LotusTok, вежливо скажи, что помогаешь только с модом.
- Если не уверен или это может меняться, честно скажи об этом и посоветуй FAQ или форум.
- Не проси пароли, коды входа в TikTok и личные данные. Не обещай значки и не меняй аккаунты.
- Отвечай коротко и по-человечески, на языке вопроса (русский, украинский или английский).
- Обычным текстом, без Markdown: без звёздочек, решёток и таблиц. Без эмодзи.
"""

_lock = threading.Lock()
_asked = {}
_talks = {}


def _config():
    with open(CONFIG, encoding="utf-8") as handle:
        return json.load(handle)


def _knowledge():
    with open(KNOWLEDGE, encoding="utf-8") as handle:
        return handle.read()


def allowed(who):
    """Whether this person may ask now; counts the question if so."""
    now = time.time()
    with _lock:
        recent = [t for t in _asked.get(who, []) if now - t < WINDOW]
        if len(recent) >= EACH:
            _asked[who] = recent
            return False
        recent.append(now)
        _asked[who] = recent
        return True


# ------------------------------------------------------------- conversations

def start(key):
    """Open a conversation for this (chat, person). Starting again starts it fresh."""
    with _lock:
        _talks[key] = {"until": time.time() + IDLE, "turns": []}


def stop(key):
    with _lock:
        return _talks.pop(key, None) is not None


def talking(key):
    """Whether this (chat, person) is in a conversation; keeps it open while it is used."""
    with _lock:
        talk = _talks.get(key)
        if talk is None:
            return False
        if time.time() > talk["until"]:
            _talks.pop(key, None)
            return False
        talk["until"] = time.time() + IDLE
        return True


def _turns(key):
    with _lock:
        talk = _talks.get(key)
        return list(talk["turns"]) if talk else []


def _remember(key, question, answer):
    with _lock:
        talk = _talks.get(key)
        if talk is None:
            return
        talk["turns"].extend([
            {"role": "user", "content": question},
            {"role": "assistant", "content": answer},
        ])
        talk["turns"] = talk["turns"][-REMEMBER * 2:]


# ------------------------------------------------------------------ asking

def _ask_one(model, messages, config):
    body = json.dumps({
        "model": model,
        "messages": messages,
        "max_tokens": 700,
        "temperature": 0.3,
    }).encode("utf-8")
    request = urllib.request.Request(
        config["base"].rstrip("/") + "/chat/completions", data=body,
        headers={"Content-Type": "application/json",
                 "Authorization": "Bearer " + config["key"],
                 "User-Agent": "LotusTokBot"})
    with urllib.request.urlopen(request, timeout=60) as answer:
        data = json.loads(answer.read().decode("utf-8"))
    text = data["choices"][0]["message"]["content"]
    return (text or "").strip()


def ask(question, key=None):
    """(answer, problems). The answer is None when every model failed.

    `key` is the conversation the question belongs to, if there is one: the last turns of it
    go to the model, and the new turn is kept when it is answered.
    `problems` lists what went wrong, one line per model, for the owner to read.
    It never goes to the person asking.
    """
    problems = []
    try:
        config = _config()
    except Exception as trouble:
        return None, ["config: %s" % trouble]
    messages = [{"role": "system", "content": RULES + "\nБАЗА ЗНАНИЙ\n" + _knowledge()}]
    if key is not None:
        messages += _turns(key)
    messages.append({"role": "user", "content": question})
    for model in MODELS:
        try:
            text = _ask_one(model, messages, config)
            if text:
                if len(text) > MOST_CHARS:
                    text = text[:MOST_CHARS].rsplit(" ", 1)[0] + "…"
                if key is not None:
                    _remember(key, question, text)
                return text, problems
            problems.append("%s: empty answer" % model)
        except Exception as trouble:
            problems.append("%s: %s" % (model, _short(trouble)))
    return None, problems


def _short(trouble):
    """A problem in one line, with nothing that looks like a key in it."""
    text = str(trouble)
    text = re.sub(r"sk-[A-Za-z0-9_-]+", "sk-…", text)
    return text[:300]
