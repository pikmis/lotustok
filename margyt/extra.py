"""Changes made on top of the patched dex files, each one on its own.

The rules in dexpatch.py are all applied to every dex file, and their answer
is kept under a name made from the whole of dexpatch.py. So a change to any
rule there starts every dex file again -- about two and a half hours.

A change made here is applied after that, to the one dex file that holds the
class it is about, and kept under a name made from that dex file and this
module only. Changing a rule here costs one dex file, and nothing else.

Each rule is (marker, name, function):

    marker    a class descriptor, e.g. "Lcom/example/Thing;". Only dex files
              that define it are taken apart at all.
    name      what the rule is called, for the build's report.
    function  takes the directory the dex was taken apart into, and returns
              a dict of counts, the way dexpatch's rules do. It must find its
              own file inside the directory, the way dexpatch's rules do: by
              the class declaration on the first line, not by the file name,
              since baksmali on Windows renames names that collide by case.

An empty RULES list costs nothing.
"""

import hashlib
import os
import shutil
import tempfile
from typing import Callable, Dict, List, Tuple

from . import dexpatch

Rule = Tuple[str, str, Callable[[str], Dict[str, int]]]

def _hashtag_limit(tree: str) -> Dict[str, int]:
    path = dexpatch._class_file(tree, "Lw3j/e;")
    if path is None:
        return {"hashtag limit": 0}
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    old = ('const-string v3, "studio_publish_hashtag_max_limit"')
    start = text.find(old)
    if start < 0:
        return {"hashtag limit": 0}
    call = "invoke-virtual {v4, v1, v0, v3, v2}, LX/09tK;->LJIIJJI(IILjava/lang/String;Z)I"
    at = text.find(call, start)
    moved = text.find("move-result v0", at)
    if at < 0 or moved < 0 or moved - at > 200:
        return {"hashtag limit": 0}
    end = moved + len("move-result v0")
    text = (text[:end] + "\n\n    invoke-static {v0}, Lcat/narezany/margyt/Hashtags;->limit(I)I\n\n    move-result v0"
            + text[end:])
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)
    return {"hashtag limit": 1}


#: the rules, in the order they run on a dex file
RULES: List[Rule] = [
    ("Lw3j/e;", "hashtag limit", _hashtag_limit),
]


def _key(dex: bytes, smali) -> str:
    here = open(os.path.abspath(__file__), "rb").read()
    parts = [dex, here, str(smali.api).encode(), dexpatch.dex_format(dex).encode()]
    digest = hashlib.sha256()
    for part in parts:
        digest.update(hashlib.sha256(part).digest())
    stamp = os.path.getmtime(smali.jar) if os.path.exists(smali.jar) else 0
    digest.update(str(stamp).encode())
    return digest.hexdigest()


def apply(names: List[str], read: Callable[[str], bytes], write: Callable[[str, bytes], None],
          smali, workspace: str, say: Callable[[str], None]) -> Dict[str, int]:
    """Run the rules over the dex files named, writing back those that change."""
    counts: Dict[str, int] = {}
    if not RULES:
        return counts
    cache = os.path.join(workspace, "extra")
    os.makedirs(cache, exist_ok=True)
    for name in names:
        dex = read(name)
        hits = [rule for rule in RULES if rule[0].encode() in dex and dexpatch.defines(dex, rule[0])]
        if not hits:
            continue
        key = _key(dex, smali)
        kept = os.path.join(cache, key + ".dex")
        if os.path.exists(kept):
            with open(kept, "rb") as handle:
                write(name, handle.read())
            say("%s: %s (kept)" % (name, ", ".join(rule[1] for rule in hits)))
            continue
        scratch = tempfile.mkdtemp(prefix="extra-", dir=cache)
        try:
            dex_in = os.path.join(scratch, "in.dex")
            with open(dex_in, "wb") as handle:
                handle.write(dex)
            tree = os.path.join(scratch, "smali")
            smali.disassemble(dex_in, tree)
            done: Dict[str, int] = {}
            for marker, rule_name, rule in hits:
                for label, value in rule(tree).items():
                    done[label] = done.get(label, 0) + value
            for label, value in done.items():
                counts[label] = counts.get(label, 0) + value
            if not any(done.values()):
                continue
            out = os.path.join(scratch, "out.dex")
            smali.assemble(tree, out)
            with open(out, "rb") as handle:
                patched = handle.read()
            part = kept + ".part"
            with open(part, "wb") as handle:
                handle.write(patched)
            os.replace(part, kept)
            write(name, patched)
            say("%s: %s" % (name, ", ".join(rule[1] for rule in hits)))
        finally:
            shutil.rmtree(scratch, ignore_errors=True)
    return counts
