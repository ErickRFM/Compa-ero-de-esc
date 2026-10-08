"""ADB evidence helper; never injects a session or modifies application storage."""
import argparse
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/audits/v8-evidence'
ADB = pathlib.Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'


def adb(*args):
    return subprocess.check_output([str(ADB), '-s', 'emulator-5554', *args], text=True, encoding='utf-8', errors='replace', timeout=20)


def tree():
    for _ in range(3):
        remote = '/sdcard/v8-ui-' + uuid.uuid4().hex + '.xml'
        try:
            result = adb('shell', 'uiautomator', 'dump', remote)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            continue
        if 'dumped to:' in result:
            content = adb('shell', 'cat', remote)
            adb('shell', 'rm', remote)
            return ET.fromstring(content)
    raise RuntimeError('UI did not stabilize; no stale hierarchy will be used')


def tap(label):
    ui = tree()
    nodes = list(ui.iter('node'))
    candidates = [n for n in nodes if any(v.strip().startswith(label) for v in (n.get('text', ''), n.get('content-desc', ''))) and n.get('bounds') != '[0,0][0,0]']
    if not candidates:
        raise RuntimeError(f'Control not visible: {label}')
    node = next((n for n in candidates if n.get('clickable') == 'true'), candidates[-1])
    parents = {child: parent for parent in ui.iter() for child in parent}
    candidate = node
    while candidate.get('clickable') != 'true' and candidate in parents:
        candidate = parents[candidate]
    if candidate.get('clickable') == 'true':
        node = candidate
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    if x2 <= x1 or y2 <= y1:
        raise RuntimeError(f'Control has no visible bounds: {label}')
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def capture(name):
    target = EVIDENCE / name
    target.parent.mkdir(parents=True, exist_ok=True)
    ui = tree()
    if not any(n.get('package') == 'org.companerodeescuela' for n in ui.iter('node')):
        raise RuntimeError('Application is not visible; refusing to label another screen as app evidence')
    adb('shell', 'screencap', '-p', '/sdcard/v8-screen.png')
    adb('pull', '/sdcard/v8-screen.png', str(target.with_suffix('.png')))
    target.with_suffix('.xml').write_text(ET.tostring(ui, encoding='unicode'), encoding='utf-8')
    print(target.with_suffix('.png'))


def open_document(label):
    # DocumentsUI grid items expose keyboard focus even when clickable=false.
    for _ in range(20):
        ui = tree()
        for node in ui.iter('node'):
            if node.get('focused') == 'true' and any(child.get('text') == label or child.get('content-desc', '').startswith(label + ',') for child in node.iter('node')):
                adb('shell', 'input', 'keyevent', '66')
                return
        adb('shell', 'input', 'keyevent', '61')
    raise RuntimeError('Requested QA document did not receive keyboard focus')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['dump', 'tap', 'capture', 'open-document'])
    parser.add_argument('value', nargs='?')
    args = parser.parse_args()
    if args.action == 'tap':
        tap(args.value)
    elif args.action == 'capture':
        capture(args.value)
    elif args.action == 'open-document':
        open_document(args.value)
    else:
        for n in tree().iter('node'):
            if n.get('text') or n.get('content-desc') or n.get('class', '').endswith('EditText'):
                print(n.get('text') or n.get('content-desc') or 'INPUT', n.get('bounds'), 'clickable='+n.get('clickable', ''))
