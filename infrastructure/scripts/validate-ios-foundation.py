#!/usr/bin/env python3
"""Validate the iOS config locally; Xcode/Native execution still requires macOS."""
from pathlib import Path
import argparse
import plistlib
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--schema", type=Path, help="Official Codemagic JSON schema downloaded separately")
args = parser.parse_args()
project = (root / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
definitions = set(re.findall(r"^\s*([A-F0-9]{24}) = \{", project, re.M))
references = set(re.findall(r"\b[A-F0-9]{24}\b", project))
assert references <= definitions, f"Undefined PBX references: {references - definitions}"
assert "embedAndSignAppleFrameworkForXcode" in project
assert "ENABLE_USER_SCRIPT_SANDBOXING = NO" in project
for filename, allowed in (("Info.plist", False), ("Info-Debug.plist", True)):
    with (root / "iosApp/iosApp" / filename).open("rb") as file:
        info = plistlib.load(file)
    assert info["CFBundleDisplayName"] == "Compañero de Clase"
    ats = info["NSAppTransportSecurity"]
    assert ats["NSAllowsLocalNetworking"] == allowed
    assert not ats.get("NSAllowsArbitraryLoads", False)
scheme = ET.parse(root / "iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme")
assert scheme.findall(".//TestableReference")
for swift in re.findall(r"path = (\w+\.swift);", project):
    assert list((root / "iosApp").rglob(swift)), f"Missing Swift source {swift}"
if args.schema:
    import json
    import jsonschema
    import yaml
    config = yaml.safe_load((root / "codemagic.yaml").read_text(encoding="utf-8"))
    with args.schema.open(encoding="utf-8") as file:
        schema = json.load(file)
    jsonschema.validate(config, schema)
    workflow = config["workflows"]["ios-simulator"]
    assert workflow["instance_type"] == "mac_mini_m2"
    assert "ios_signing" not in workflow.get("environment", {})
    assert "publishing" not in workflow
    print("Codemagic official schema: PASS")
print("iOS static config: PASS (not an Xcode compilation)")
