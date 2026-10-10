import importlib.util
import io
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest
import zlib

SCRIPT = Path(__file__).resolve().parents[1] / "export-v10-evidence.py"
spec = importlib.util.spec_from_file_location("evidence_export", SCRIPT)
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


def png():
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"\0\0\0\0")) + chunk(b"IEND", b"")


def archive(count=19, corrupt=False, unsafe=False, symlink=False):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as tar:
        for i in range(count):
            body = png()
            if corrupt and i == 0:
                body = body[:-1] + bytes([body[-1] ^ 1])
            item = tarfile.TarInfo(f"v10-evidence/screen-{i}.png")
            item.size = len(body)
            tar.addfile(item, io.BytesIO(body))
        if unsafe or symlink:
            item = tarfile.TarInfo("../escape.png" if unsafe else "v10-evidence/link.png")
            if symlink:
                item.type = tarfile.SYMTYPE
                item.linkname = "../../escape.png"
            tar.addfile(item, None)
    return buf.getvalue()


class EvidenceExportTest(unittest.TestCase):
    def export(self, payloads):
        task_tmp = tempfile.TemporaryDirectory()
        self.addCleanup(task_tmp.cleanup)
        output = Path(task_tmp.name) / "output"
        calls = []
        def fetch(path):
            calls.append(path)
            path.write_bytes(payloads[min(len(calls) - 1, len(payloads) - 1)])
        return output, calls, lambda: exporter.export_evidence(output, fetch=fetch, pause=lambda: None)

    def test_truncated_transfer_retries_and_validates_all_pngs(self):
        valid = archive()
        output, calls, run = self.export([valid[:540], valid])
        self.assertEqual(run(), 19)
        self.assertEqual(len(calls), 2)
        self.assertEqual(len(list(output.rglob("*.png"))), 19)

    def test_corrupt_png_is_rejected_after_three_attempts(self):
        output, calls, run = self.export([archive(corrupt=True)])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertEqual(len(calls), 3)
        self.assertFalse(output.exists())

    def test_partial_capture_set_is_never_published(self):
        output, calls, run = self.export([archive(count=18)])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertEqual(len(calls), 3)
        self.assertFalse(output.exists())

    def test_path_escape_is_rejected(self):
        output, _, run = self.export([archive(unsafe=True)])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertFalse((output.parent / "escape.png").exists())

    def test_symlinks_are_rejected(self):
        output, _, run = self.export([archive(symlink=True)])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertFalse(output.exists())

    def test_duplicate_canonical_filenames_are_rejected(self):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w") as tar:
            for i in range(19):
                body = png()
                name = "v10-evidence/same.png" if i == 0 else ("v10-evidence//same.png" if i == 1 else f"v10-evidence/{i}.png")
                item = tarfile.TarInfo(name)
                item.size = len(body)
                tar.addfile(item, io.BytesIO(body))
        output, _, run = self.export([buf.getvalue()])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertFalse(output.exists())

    def test_adb_read_errors_never_publish_payloads(self):
        import contextlib
        import subprocess
        output, _, _ = self.export([])
        calls = []
        def fetch(path):
            calls.append(path)
            raise subprocess.CalledProcessError(1, ["adb"], output="private-sentinel")
        log = io.StringIO()
        with contextlib.redirect_stdout(log), self.assertRaises(exporter.EvidenceExportError):
            exporter.export_evidence(output, fetch=fetch, pause=lambda: None)
        self.assertEqual(len(calls), 3)
        self.assertNotIn("private-sentinel", log.getvalue())
        self.assertFalse(output.exists())
    def test_windows_separator_cannot_escape_staging_directory(self):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w") as tar:
            for i in range(19):
                body = png()
                item = tarfile.TarInfo("v10-evidence/..\\escape.png" if i == 0 else f"v10-evidence/{i}.png")
                item.size = len(body)
                tar.addfile(item, io.BytesIO(body))
        output, _, run = self.export([buf.getvalue()])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertFalse(output.exists())

    def test_case_aliases_cannot_inflate_capture_count(self):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w") as tar:
            for i in range(19):
                body = png()
                name = "same.png" if i == 0 else ("Same.png" if i == 1 else f"{i}.png")
                item = tarfile.TarInfo("v10-evidence/" + name)
                item.size = len(body)
                tar.addfile(item, io.BytesIO(body))
        output, _, run = self.export([buf.getvalue()])
        with self.assertRaises(exporter.EvidenceExportError):
            run()
        self.assertFalse(output.exists())
    def test_valid_transfer_needs_only_one_attempt(self):
        output, calls, run = self.export([archive()])
        self.assertEqual(run(), 19)
        self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()