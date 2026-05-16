from __future__ import annotations

import argparse
import json
import lzma
import re
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path


DEFAULT_SUITE = "trixie"
DEFAULT_PACKAGES_URL = f"https://deb.debian.org/debian/dists/{DEFAULT_SUITE}/main/binary-arm64/Packages.xz"
DEFAULT_TARGETS = ["gimp"]
DEPENDENCY_FIELDS = ["Pre-Depends", "Depends", "Recommends"]


@dataclass(frozen=True)
class PackageRecord:
    name: str
    version: str
    architecture: str
    filename: str
    size: int
    sha256: str
    fields: dict[str, str]

    @property
    def provides(self) -> list[str]:
        return [normalize_package_name(item) for item in split_relation_list(self.fields.get("Provides", ""))]


def normalize_package_name(value: str) -> str:
    value = value.strip()
    value = value.split(" ", 1)[0]
    value = value.split("(", 1)[0]
    value = value.split(":", 1)[0]
    return value.strip()


def split_relation_list(value: str) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


def split_alternatives(value: str) -> list[str]:
    return [normalize_package_name(part) for part in value.split("|") if normalize_package_name(part)]


def parse_packages_index(text: str) -> dict[str, PackageRecord]:
    records: dict[str, PackageRecord] = {}
    for paragraph in text.split("\n\n"):
        fields: dict[str, str] = {}
        current: str | None = None
        for raw_line in paragraph.splitlines():
            if not raw_line:
                continue
            if raw_line[0].isspace() and current is not None:
                fields[current] += "\n" + raw_line[1:]
                continue
            if ":" not in raw_line:
                continue
            key, value = raw_line.split(":", 1)
            fields[key] = value.strip()
            current = key
        name = fields.get("Package")
        filename = fields.get("Filename")
        if not name or not filename:
            continue
        records[name] = PackageRecord(
            name=name,
            version=fields.get("Version", ""),
            architecture=fields.get("Architecture", ""),
            filename=filename,
            size=int(fields.get("Size", "0") or "0"),
            sha256=fields.get("SHA256", ""),
            fields=fields,
        )
    return records


def load_packages(url: str) -> dict[str, PackageRecord]:
    with urllib.request.urlopen(url, timeout=60) as response:
        payload = response.read()
    return parse_packages_index(lzma.decompress(payload).decode("utf-8", "replace"))


def build_provider_map(records: dict[str, PackageRecord]) -> dict[str, list[str]]:
    providers: dict[str, list[str]] = {}
    for record in records.values():
        for provided in record.provides:
            providers.setdefault(provided, []).append(record.name)
    for provided in providers:
        providers[provided].sort()
    return providers


def choose_dependency(
    relation: str,
    records: dict[str, PackageRecord],
    providers: dict[str, list[str]],
) -> str | None:
    alternatives = split_alternatives(relation)
    for candidate in alternatives:
        if candidate in records:
            return candidate
    for candidate in alternatives:
        provider_names = providers.get(candidate, [])
        if provider_names:
            return provider_names[0]
    return None


def resolve_closure(
    targets: list[str],
    records: dict[str, PackageRecord],
    include_fields: list[str],
) -> tuple[list[str], list[str]]:
    providers = build_provider_map(records)
    resolved: set[str] = set()
    missing: set[str] = set()
    queue = list(targets)
    while queue:
        name = queue.pop(0)
        if name in resolved:
            continue
        if name not in records:
            replacement = providers.get(name, [None])[0]
            if replacement is None:
                missing.add(name)
                continue
            name = replacement
            if name in resolved:
                continue
        resolved.add(name)
        record = records[name]
        for field in include_fields:
            for relation in split_relation_list(record.fields.get(field, "")):
                dependency = choose_dependency(relation, records, providers)
                if dependency is None:
                    missing.add(relation)
                elif dependency not in resolved:
                    queue.append(dependency)
    return sorted(resolved), sorted(missing)


def emit_lock(
    records: dict[str, PackageRecord],
    packages: list[str],
    missing: list[str],
    url: str,
    include_fields: list[str],
    suite: str,
) -> dict[str, object]:
    package_records = [records[name] for name in packages]
    total_download = sum(record.size for record in package_records)
    return {
        "name": "plib-gimp-demo-bundle",
        "suite": suite,
        "architecture": "arm64",
        "source_index": url,
        "targets": DEFAULT_TARGETS,
        "dependency_fields": include_fields,
        "package_count": len(package_records),
        "download_size_bytes": total_download,
        "download_size_mib": round(total_download / 1024 / 1024, 2),
        "missing_relations": missing,
        "launch_profile": {
            "program": "/usr/bin/gimp",
            "argv": ["gimp", "--new-instance", "--no-data", "--no-fonts"],
            "env": {
                "GDK_BACKEND": "wayland",
                "WAYLAND_DISPLAY": "alr-gimp-0",
                "XDG_RUNTIME_DIR": "/usr/share/alr-smoke/alr-wayland-runtime",
                "NO_AT_BRIDGE": "1",
            },
            "presentation": "Android Surface via Plib Wayland/AHardwareBuffer bridge",
        },
        "packages": [
            {
                "package": record.name,
                "version": record.version,
                "architecture": record.architecture,
                "filename": record.filename,
                "size": record.size,
                "sha256": record.sha256,
                "depends": record.fields.get("Depends", ""),
                "pre_depends": record.fields.get("Pre-Depends", ""),
                "recommends": record.fields.get("Recommends", ""),
            }
            for record in package_records
        ],
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Resolve the Debian arm64 package closure for the Plib GIMP demo bundle.")
    parser.add_argument("--packages-url", default=DEFAULT_PACKAGES_URL)
    parser.add_argument("--suite", default=DEFAULT_SUITE)
    parser.add_argument("--output", type=Path, default=Path("rootfs/gimp-demo-bundle.lock.json"))
    parser.add_argument("--include-recommends", action="store_true")
    args = parser.parse_args(argv)

    include_fields = ["Pre-Depends", "Depends"]
    if args.include_recommends:
        include_fields.append("Recommends")
    records = load_packages(args.packages_url)
    packages, missing = resolve_closure(DEFAULT_TARGETS, records, include_fields)
    lock = emit_lock(records, packages, missing, args.packages_url, include_fields, args.suite)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(lock, indent=2, sort_keys=True) + "\n")
    print(f"resolved {len(packages)} packages, {lock['download_size_mib']} MiB, missing={len(missing)}")
    return 0 if not missing else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
