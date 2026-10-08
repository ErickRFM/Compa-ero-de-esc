"""Visual comparison of user references and genuine ADB captures, not a fidelity score."""
import json
import pathlib
import shutil
from PIL import Image, ImageChops, ImageDraw, ImageEnhance

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'docs/audits/v8-evidence'
TEMP = pathlib.Path.home() / 'AppData/Local/Temp'
REFERENCES = {
    'login': '75501982-72da-4cae-ba7f-7225a03d176d',
    'home': 'a719f3ec-6f0a-49b4-b590-b31846c636aa',
    'schedule': 'a419c411-c172-40d6-a93b-63b4155d4065',
    'channel': 'f0fb87a3-ab60-450c-ae7e-fd7a195f2de1',
    'attendance': '0edeeddd-969d-434f-99ab-efcc56ee7513',
}
CAPTURES = {
    'login': 'after/login/initial-390.png',
    'home': 'after/home/final-390.png',
    'schedule': 'after/schedule/final-week-390.png',
    'channel': 'after/channel/final-notices-390.png',
    'attendance': 'after/attendance/final-no-session-390.png',
}
metadata = {'method': 'Crop phone frame; normalize both full screen images to 390x874. Overlay alpha=.5. RGB absolute difference amplified 3x. No similarity percentage; live data and system bars unmasked.', 'screens': {}}
for screen, identifier in REFERENCES.items():
    original = TEMP / ('codex-clipboard-' + identifier + '.png')
    target = EVIDENCE / 'references' / screen
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(original, target / 'original.png')
    reference = Image.open(original).convert('RGB')
    # Fixed interior rectangle independently inspected against all five user images.
    reference = reference.crop((40, 30, 824, 1788)).resize((390, 874), Image.Resampling.LANCZOS)
    capture_path = EVIDENCE / CAPTURES[screen]
    if not capture_path.exists():
        metadata['screens'][screen] = {'status': 'NO VALIDADO: final capture absent'}
        continue
    android = Image.open(capture_path).convert('RGB').resize((390, 874), Image.Resampling.LANCZOS)
    overlay = Image.blend(reference, android, .5)
    diff = ImageEnhance.Brightness(ImageChops.difference(reference, android)).enhance(3)
    out = EVIDENCE / 'comparisons' / screen
    out.mkdir(parents=True, exist_ok=True)
    for label, bitmap in [('reference', reference), ('android', android), ('overlay', overlay), ('difference', diff)]:
        bitmap.save(out / (label + '.png'))
    panel = Image.new('RGB', (390*4, 904), '#09090c')
    draw = ImageDraw.Draw(panel)
    for i, (label, bitmap) in enumerate([('REFERENCE', reference), ('ANDROID QA', android), ('OVERLAY 50%', overlay), ('RGB DIFFERENCE x3', diff)]):
        draw.text((i*390+12, 8), label, fill='white')
        panel.paste(bitmap, (i*390, 30))
    panel.save(out / 'comparison.png')
    metadata['screens'][screen] = {'capture': CAPTURES[screen], 'reference_crop': [40,30,824,1788], 'normalized': [390,874], 'status': 'Compared manually; not a pass percentage'}
(EVIDENCE / 'comparisons/method.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
print(json.dumps(metadata, indent=2))
