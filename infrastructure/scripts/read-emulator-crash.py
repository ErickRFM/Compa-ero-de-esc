#!/usr/bin/env python3
"""Emit only native exception metadata; never publish raw minidump memory."""
from pathlib import Path
import struct


def describe_dump(path: Path) -> None:
    data = path.read_bytes()
    if len(data) < 32 or data[:4] != b"MDMP":
        return
    count, directory = struct.unpack_from("<II", data, 8)
    streams = {}
    for index in range(min(count, 128)):
        kind, size, offset = struct.unpack_from("<III", data, directory + index * 12)
        if offset + size <= len(data):
            streams[kind] = (size, offset)
    if 6 not in streams:
        print(f"{path.name}: no exception stream")
        return
    size, offset = streams[6]
    if size < 32:
        return
    thread = struct.unpack_from("<I", data, offset)[0]
    code, flags = struct.unpack_from("<II", data, offset + 8)
    address = struct.unpack_from("<Q", data, offset + 24)[0]
    print(f"{path.name}: thread={thread} exception=0x{code:08x} flags=0x{flags:08x} address=0x{address:x}")
    if 4 not in streams:
        return
    size, offset = streams[4]
    modules = struct.unpack_from("<I", data, offset)[0]
    for index in range(min(modules, (size - 4) // 108)):
        entry = offset + 4 + index * 108
        base, length = struct.unpack_from("<QI", data, entry)
        if not base <= address < base + length:
            continue
        name_offset = struct.unpack_from("<I", data, entry + 20)[0]
        name_size = struct.unpack_from("<I", data, name_offset)[0]
        if name_size > 4096 or name_offset + 4 + name_size > len(data):
            continue
        name = data[name_offset + 4:name_offset + 4 + name_size].decode("utf-16-le", errors="replace")
        # Only a module basename and relative address, never paths or memory.
        name = name.replace("\\", "/").rsplit("/", 1)[-1]
        print(f"fault_module={name} offset=0x{address - base:x}")


if __name__ == "__main__":
    dumps = list(Path("/tmp/android-runner").glob("emu-crash-*.db/**/*.dmp"))
    print(f"Native emulator dumps found: {len(dumps)}")
    for path in dumps:
        try:
            describe_dump(path)
        except (OSError, ValueError, struct.error) as error:
            print(f"Unable to parse {path.name}: {type(error).__name__}")
