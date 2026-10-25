from __future__ import annotations

from typing import List, Sequence, Tuple

from . import bloom_paths
from .axml import (
    ANDROID_NS,
    Attribute,
    Axml,
    Node,
    NO_ENTRY,
    RES_XML_END_ELEMENT,
    RES_XML_END_NAMESPACE,
    RES_XML_START_ELEMENT,
    RES_XML_START_NAMESPACE,
    StringPool,
    TYPE_REFERENCE,
    TYPE_STRING,
)
from .vector import ATTR_IDS, ATTR_NAMES, TYPE_DIMENSION, TYPE_FLOAT, dimension_dp, float_bits

KEY = "bloom"
LABEL = "Цветок"
PICTURE = "assets/bloom.png"

LAYERS = 0x34
BACKGROUND_ID = (LAYERS << 24) | (1 << 16)
FOREGROUND_ID = BACKGROUND_ID + 1
BACKGROUND_PATH = "res/margyt/bloom_bg.xml"
FOREGROUND_PATH = "res/margyt/bloom_fg.xml"

ICON = 0x35
ICON_ID = (ICON << 24) | (1 << 16)
ICON_PNG = "res/margyt/bloom.png"
ICON_XML = "res/margyt/bloom.xml"
SDK = 31

ACCENT1_100 = 0x0106003A
ACCENT1_200 = 0x0106003B
ACCENT1_700 = 0x01060040

ATTR_DRAWABLE = 0x01010199

SIZE = 108
GROUND = "M 0 0 L 108 0 L 108 108 L 0 108 Z"

def _vector(paths: Sequence[Tuple[int, str]]) -> bytes:

    strings = list(ATTR_NAMES)
    strings += [data for _colour, data in paths]
    android = len(strings)
    strings.append("android")
    uri = len(strings)
    strings.append(ANDROID_NS)
    path_name = len(strings)
    strings.append("path")
    vector_name = len(strings)
    strings.append("vector")

    axml = Axml(StringPool(strings, StringPool.UTF8, b"", []), list(ATTR_IDS), [])
    open_ns = Node(RES_XML_START_NAMESPACE, 1, NO_ENTRY)
    open_ns.prefix, open_ns.uri = android, uri
    vector = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
    vector.name = vector_name
    vector.attributes = [
        Attribute(uri, 0, NO_ENTRY, 8, 0, TYPE_DIMENSION, dimension_dp(SIZE)),
        Attribute(uri, 1, NO_ENTRY, 8, 0, TYPE_DIMENSION, dimension_dp(SIZE)),
        Attribute(uri, 2, NO_ENTRY, 8, 0, TYPE_FLOAT, float_bits(float(SIZE))),
        Attribute(uri, 3, NO_ENTRY, 8, 0, TYPE_FLOAT, float_bits(float(SIZE))),
    ]
    nodes: List[Node] = [open_ns, vector]
    for index, (colour, _data) in enumerate(paths):
        element = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
        element.name = path_name
        string_index = len(ATTR_NAMES) + index
        element.attributes = [
            Attribute(uri, 4, NO_ENTRY, 8, 0, TYPE_REFERENCE, colour),
            Attribute(uri, 5, string_index, 8, 0, TYPE_STRING, string_index),
        ]
        closing = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
        closing.name = path_name
        nodes += [element, closing]
    close_vector = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
    close_vector.name = vector_name
    close_ns = Node(RES_XML_END_NAMESPACE, 1, NO_ENTRY)
    close_ns.prefix, close_ns.uri = android, uri
    axml.nodes = nodes + [close_vector, close_ns]
    return axml.build()

def background() -> bytes:
    return _vector([(ACCENT1_100, GROUND), (ACCENT1_200, bloom_paths.BLOBS)])

def foreground() -> bytes:
    return _vector([(ACCENT1_700, bloom_paths.MARK)])

def adaptive() -> bytes:

    strings = ["drawable", "android", ANDROID_NS, "adaptive-icon", "background", "foreground", "monochrome"]
    drawable, android, uri, root_name = 0, 1, 2, 3
    axml = Axml(StringPool(strings, StringPool.UTF8, b"", []), [ATTR_DRAWABLE], [])
    open_ns = Node(RES_XML_START_NAMESPACE, 1, NO_ENTRY)
    open_ns.prefix, open_ns.uri = android, uri
    root = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
    root.name = root_name
    root.attributes = []
    nodes: List[Node] = [open_ns, root]
    for name, layer in ((4, BACKGROUND_ID), (5, FOREGROUND_ID), (6, FOREGROUND_ID)):
        element = Node(RES_XML_START_ELEMENT, 1, NO_ENTRY)
        element.name = name
        element.attributes = [Attribute(uri, drawable, NO_ENTRY, 8, 0, TYPE_REFERENCE, layer)]
        closing = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
        closing.name = name
        nodes += [element, closing]
    close_root = Node(RES_XML_END_ELEMENT, 1, NO_ENTRY)
    close_root.name = root_name
    close_ns = Node(RES_XML_END_NAMESPACE, 1, NO_ENTRY)
    close_ns.prefix, close_ns.uri = android, uri
    axml.nodes = nodes + [close_root, close_ns]
    return axml.build()
