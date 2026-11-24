#!/usr/bin/env python3
"""Read-only APK audit. Does not modify or re-sign the input APK."""
import hashlib
import json
import os
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

apk = Path(sys.argv[1] if len(sys.argv) > 1 else "CarnageWars.apk")
if not apk.is_file():
    raise SystemExit(f"APK not found: {apk}")

sha = hashlib.sha256()
with apk.open("rb") as f:
    for chunk in iter(lambda: f.read(1024 * 1024), b""):
        sha.update(chunk)

try:
    zf = zipfile.ZipFile(apk)
except zipfile.BadZipFile as exc:
    raise SystemExit(f"Invalid APK/ZIP: {exc}")

infos = zf.infolist()
bad_entry = zf.testzip()
abi_sizes = defaultdict(int)
largest = sorted(infos, key=lambda x: x.file_size, reverse=True)[:20]
for info in infos:
    if info.filename.startswith("lib/"):
        parts = info.filename.split("/")
        if len(parts) >= 3:
            abi_sizes[parts[1]] += info.file_size

def read_text(name):
    try:
        return zf.read(name).decode("utf-8", "replace")
    except KeyError:
        return ""

boot = read_text("assets/bin/Data/boot.config")
addressables = read_text("assets/aa/settings.json")
manifest = read_text("AndroidManifest.xml")

report = {
    "file": apk.name,
    "size_bytes": apk.stat().st_size,
    "size_mib": round(apk.stat().st_size / (1024 * 1024), 2),
    "sha256": sha.hexdigest(),
    "zip_entries": len(infos),
    "zip_crc": "PASS" if bad_entry is None else f"FAIL: {bad_entry}",
    "abis_uncompressed_bytes": dict(sorted(abi_sizes.items())),
    "largest_entries": [
        {"path": x.filename, "uncompressed_bytes": x.file_size, "compressed_bytes": x.compress_size}
        for x in largest
    ],
    "unity_boot_config": boot.splitlines(),
    "addressables_settings_excerpt": [
        line.strip() for line in addressables.splitlines()
        if "m_maxConcurrentWebRequests" in line or "m_CatalogRequestsTimeout" in line
    ],
    "manifest_binary_present": bool(manifest) and not manifest.lstrip().startswith("<?xml"),
}
Path("carnage-wars-3.6/audit-report.json").write_text(
    json.dumps(report, indent=2), encoding="utf-8"
)

lines = [
    "# Carnage Wars APK audit report",
    "",
    f"- File: `{report['file']}`",
    f"- Size: {report['size_mib']} MiB ({report['size_bytes']} bytes)",
    f"- SHA-256: `{report['sha256']}`",
    f"- ZIP entries: {report['zip_entries']}",
    f"- ZIP CRC: **{report['zip_crc']}**",
    "",
    "## Native ABI payload (uncompressed)",
]
for abi, size in report["abis_uncompressed_bytes"].items():
    lines.append(f"- {abi}: {size / (1024 * 1024):.2f} MiB")
lines += ["", "## Largest APK entries"]
for item in report["largest_entries"]:
    lines.append(f"- `{item['path']}`: {item['uncompressed_bytes'] / (1024 * 1024):.2f} MiB")
lines += ["", "## Unity boot config", "```ini", *report["unity_boot_config"], "```", ""]
lines += [
    "## Interpretation",
    "",
    "This is a read-only structural audit. It does not prove FPS, CPU, GPU, RAM, heat,",
    "multiplayer, anti-cheat, or installation behavior. It does not modify the APK.",
    "Do not remove assets or ABIs based only on file size.",
]
Path("carnage-wars-3.6/audit-report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(json.dumps({k: report[k] for k in ("size_mib", "sha256", "zip_entries", "zip_crc", "abis_uncompressed_bytes")}, indent=2))
