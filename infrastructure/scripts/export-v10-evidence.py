#!/usr/bin/env python3
"""Export synthetic instrumentation PNGs; retry only failed ADB reads."""
from pathlib import Path, PurePosixPath
import os
import struct
import subprocess
import tarfile
import tempfile
import time
import zlib

MAX_ARCHIVE_BYTES = 128 * 1024 * 1024
MAX_PNG_BYTES = 32 * 1024 * 1024


class EvidenceExportError(RuntimeError):
    pass


def validate_png(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise EvidenceExportError("Invalid PNG signature")
    offset = 8
    image_data = []
    header = False
    while offset + 12 <= len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 12 + size
        if end > len(data):
            raise EvidenceExportError("Truncated PNG chunk")
        payload = data[offset + 8:offset + 8 + size]
        crc = struct.unpack(">I", data[end - 4:end])[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise EvidenceExportError("PNG CRC mismatch")
        if not header:
            if kind != b"IHDR" or size != 13:
                raise EvidenceExportError("Missing PNG header")
            width, height = struct.unpack(">II", payload[:8])
            if not (0 < width <= 16384 and 0 < height <= 16384):
                raise EvidenceExportError("Invalid PNG dimensions")
            header = True
        elif kind == b"IHDR":
            raise EvidenceExportError("Duplicate PNG header")
        if kind == b"IDAT":
            image_data.append(payload)
        offset = end
        if kind == b"IEND":
            if size or offset != len(data) or not image_data:
                raise EvidenceExportError("Invalid PNG end")
            decoder = zlib.decompressobj()
            decoded = decoder.decompress(b"".join(image_data), MAX_ARCHIVE_BYTES + 1)
            if not decoded or len(decoded) > MAX_ARCHIVE_BYTES or not decoder.eof or decoder.unused_data:
                raise EvidenceExportError("Invalid PNG image stream")
            return
    raise EvidenceExportError("Missing PNG end")


def validate_archive(path, stage):
    if path.stat().st_size > MAX_ARCHIVE_BYTES:
        raise EvidenceExportError("Oversized evidence archive")
    names = set()
    total = 0
    with tarfile.open(path) as archive:
        for member in archive:
            parts = PurePosixPath(member.name).parts
            if not parts or parts[0] != "v10-evidence" or ".." in parts:
                raise EvidenceExportError("Unsafe archive path")
            if member.isdir():
                continue
            if not member.isfile() or not member.name.endswith(".png") or len(parts) != 2:
                raise EvidenceExportError("Unexpected archive entry")
            if "\\" in parts[-1] or ":" in parts[-1] or parts[-1].casefold() in names or not 0 < member.size <= MAX_PNG_BYTES:
                raise EvidenceExportError("Invalid archive entry")
            total += member.size
            if total > MAX_ARCHIVE_BYTES:
                raise EvidenceExportError("Oversized evidence files")
            source = archive.extractfile(member)
            if source is None:
                raise EvidenceExportError("Missing archive file")
            with source:
                data = source.read()
            if len(data) != member.size:
                raise EvidenceExportError("Truncated archive file")
            validate_png(data)
            (stage / parts[-1]).write_bytes(data)
            names.add(parts[-1].casefold())
    if len(names) < 19:
        raise EvidenceExportError("Fewer than 19 complete captures")
    return len(names)


def fetch_adb(path):
    with path.open("wb") as stream:
        result = subprocess.run(
            ["adb", "exec-out", "run-as", "org.companerodeescuela", "tar", "-C", "files", "-cf", "-", "v10-evidence"],
            stdout=stream, stderr=subprocess.DEVNULL, timeout=60,
        )
    if result.returncode:
        raise EvidenceExportError("ADB evidence read failed")


def export_evidence(output, fetch=fetch_adb, pause=lambda: time.sleep(2)):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, 4):
        with tempfile.TemporaryDirectory(prefix="v10-evidence-read-", dir=output.parent) as temp:
            root = Path(temp)
            stage = root / "validated"
            stage.mkdir()
            archive = root / "evidence.tar"
            try:
                fetch(archive)
                count = validate_archive(archive, stage)
            except (EvidenceExportError, OSError, tarfile.TarError, subprocess.SubprocessError, zlib.error, struct.error) as error:
                # Never print ADB payloads, stderr or file contents.
                print(f"Evidence read {attempt}/3 rejected: {type(error).__name__}", flush=True)
                if attempt == 3:
                    raise EvidenceExportError("Evidence export failed after three reads") from None
                pause()
                continue
            target = output / "v10-evidence"
            target.mkdir(parents=True, exist_ok=True)
            for file in stage.iterdir():
                os.replace(file, target / file.name)
            print(f"Evidence read {attempt}/3: {count} complete PNGs validated", flush=True)
            return count
    raise EvidenceExportError("Evidence export did not complete")


if __name__ == "__main__":
    try:
        export_evidence(Path("build/v10-device-evidence"))
    except EvidenceExportError as error:
        raise SystemExit(str(error))