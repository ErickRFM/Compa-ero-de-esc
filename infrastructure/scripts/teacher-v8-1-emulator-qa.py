"""Capture real emulator evidence. No session injection, synthetic UI or API writes."""
import argparse
import datetime
import json
import pathlib
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

helper = runpy.run_path(str(pathlib.Path(__file__).with_name('v8-emulator-qa.py')))
adb, tree = helper['adb'], helper['tree']
root = pathlib.Path(__file__).resolve().parents[2]


def tap(label, index):
    ui = tree()
    parents = {child: parent for parent in ui.iter() for child in parent}
    matches = [n for n in ui.iter('node') if n.get('enabled') != 'false' and
               any(value.startswith(label) for value in [n.get('text', ''), n.get('content-desc', '')]) and
               n.get('bounds') != '[0,0][0,0]']
    if not matches:
        raise RuntimeError('Control not visible: ' + label)
    target = matches[index]
    original = target
    while target.get('clickable') != 'true' and target in parents:
        target = parents[target]
    # DocumentsUI list items can handle touch without declaring clickable.
    # Fall back to the matching node's freshly observed bounds, never the root.
    if target.get('clickable') != 'true':
        target = original
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', target.get('bounds')))
    if x2 <= x1 or y2 <= y1:
        raise RuntimeError('Control has no visible bounds')
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name, expected):
    ui = tree()
    nodes = list(ui.iter('node'))
    if not any(n.get('package') == 'org.companerodeescuela' for n in nodes):
        raise RuntimeError('Application is not visible')
    if expected and not any(n.get('text', '').startswith(expected) for n in nodes):
        raise RuntimeError('Expected screen heading not visible: ' + expected)
    target = root / 'docs/audits/v8-evidence/teacher-v8-1' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    adb('shell', 'screencap', '-p', '/sdcard/teacher-v81.png')
    adb('pull', '/sdcard/teacher-v81.png', str(target.with_suffix('.png')))
    target.with_suffix('.xml').write_text(ET.tostring(ui, encoding='unicode'), encoding='utf-8')
    metadata = {key: adb('shell', *args).strip() for key, args in {
        'size': ['wm', 'size'], 'density': ['wm', 'density'],
        'fontScale': ['settings', 'get', 'system', 'font_scale'],
    }.items()}
    metadata.update({
        'capturedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'sourceSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
        'expectedHeading': expected,
        'environment': 'localhost QA; authenticated development account; real app UI',
    })
    target.with_suffix('.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(target.with_suffix('.png'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['tap', 'capture', 'resize', 'dump'])
    parser.add_argument('value', nargs='?')
    parser.add_argument('--index', type=int, default=-1)
    parser.add_argument('--expected', default='')
    parser.add_argument('--width', type=int, default=390)
    parser.add_argument('--height', type=int, default=844)
    parser.add_argument('--font', type=float, default=1.0)
    args = parser.parse_args()
    if args.action == 'tap':
        tap(args.value, args.index)
    elif args.action == 'capture':
        capture(args.value, args.expected)
    elif args.action == 'resize':
        adb('shell', 'wm', 'density', '320')
        adb('shell', 'wm', 'size', f'{args.width * 2}x{args.height * 2}')
        adb('shell', 'settings', 'put', 'system', 'font_scale', str(args.font))
    else:
        for node in tree().iter('node'):
            if node.get('password') == 'true':
                continue
            if node.get('text') or node.get('content-desc') or node.get('class', '').endswith('EditText'):
                print(json.dumps({key: node.get(key) for key in ['class', 'text', 'content-desc', 'bounds', 'clickable', 'enabled']}, ensure_ascii=True))
