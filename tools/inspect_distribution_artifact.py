"""Inspect existing release artifacts without rebuilding, installing or publishing.

Uses official bundletool/aapt2 plus the standard-library boundary guard. Output
is local evidence, not policy approval. APK sets must be derived from the supplied
AAB: DEX/assets/native hashes and coverage are compared, and converted manifests
and resource tables are checked structurally (not claimed byte-identical).
"""

import argparse
import hashlib
import json
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from pathlib import PurePosixPath
from zipfile import ZipFile

import check_playstore_boundary as check


def binary_manifest(data):
    """Decode Android's binary XML chunks, including typed attribute values."""
    u16 = lambda at: struct.unpack_from("<H", data, at)[0]
    u32 = lambda at: struct.unpack_from("<I", data, at)[0]
    check.require(u16(0) == 3, "Expected binary Android XML")
    strings, stack, root = [], [], None
    offset = u16(2)
    while offset < len(data):
        kind, header, size = u16(offset), u16(offset + 2), u32(offset + 4)
        check.require(header >= 8 and size >= header and offset + size <= len(data), "Malformed XML chunk")
        if kind == 1:
            count, flags, start = u32(offset + 8), u32(offset + 16), u32(offset + 20)
            for index in range(count):
                at = offset + start + u32(offset + header + index * 4)
                if flags & 0x100:
                    for _ in range(2):
                        length = data[at]
                        at += 1
                        if length & 128:
                            length = ((length & 127) << 8) | data[at]
                            at += 1
                    strings.append(data[at:at + length].decode("utf-8"))
                else:
                    length = u16(at)
                    at += 2
                    if length & 0x8000:
                        length = ((length & 0x7fff) << 16) | u16(at)
                        at += 2
                    strings.append(data[at:at + length * 2].decode("utf-16le"))
        elif kind == 0x102:
            node = ET.Element(strings[u32(offset + 20)])
            start, stride, count = u16(offset + 24), u16(offset + 26), u16(offset + 28)
            for index in range(count):
                at = offset + 16 + start + index * stride
                ns, name, raw = u32(at), u32(at + 4), u32(at + 8)
                kind_value, value = data[at + 15], u32(at + 16)
                key = ("{" + strings[ns] + "}" if ns != 0xffffffff else "") + strings[name]
                if raw != 0xffffffff:
                    text = strings[raw]
                elif kind_value == 3:
                    text = strings[value]
                elif kind_value == 0x12:
                    text = "true" if value else "false"
                elif kind_value in (1, 2):
                    text = f"@0x{value:08x}"
                else:
                    text = str(value)
                node.set(key, text)
            if stack:
                stack[-1].append(node)
            else:
                root = node
            stack.append(node)
        elif kind == 0x103:
            stack.pop()
        offset += size
    check.require(root is not None and not stack, "Incomplete binary XML")
    return root


def run(command, output):
    result = subprocess.run([str(part) for part in command], capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    output.write_text(result.stdout, encoding="utf-8")
    output.with_suffix(output.suffix + ".stderr").write_text(result.stderr, encoding="utf-8")
    check.require(result.returncode == 0, f"Tool failed ({result.returncode}): {command}; {result.stderr[:1000]}")


def digest(file):
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def proto_fields(data):
    """Decode protobuf wire fields without installing a protobuf runtime."""
    at = 0

    def varint():
        nonlocal at
        value = 0
        for shift in range(0, 70, 7):
            check.require(at < len(data), "Truncated APK-set protobuf")
            byte = data[at]
            at += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                return value
        raise ValueError("Oversized APK-set protobuf varint")

    result = {}
    while at < len(data):
        key = varint()
        field, wire = key >> 3, key & 7
        check.require(field > 0, "Invalid APK-set protobuf field")
        if wire == 0:
            value = varint()
        elif wire in (1, 2, 5):
            size = varint() if wire == 2 else (8 if wire == 1 else 4)
            check.require(at + size <= len(data), "Truncated APK-set protobuf field")
            value, at = data[at:at + size], at + size
        else:
            raise ValueError(f"Unsupported APK-set protobuf wire type: {wire}")
        result.setdefault(field, []).append((wire, value))
    return result


def messages(fields, number):
    values = fields.get(number, [])
    check.require(all(wire == 2 for wire, _ in values), "Unexpected APK-set protobuf field type")
    return [value for _, value in values]


def apk_set_inventory(zipped):
    """Read the actual bundletool 1.18.3 schema, not a ZIP filename heuristic.

    https://github.com/google/bundletool/blob/1.18.3/src/main/proto/commands.proto
    This app has one base module; new feature/asset modules require review.
    """
    entries = zipped.namelist()
    check.require(len(entries) == len(set(entries)), "Duplicate APK-set ZIP entry")
    check.require("toc.pb" in entries, "APK set has no table of contents")
    toc = proto_fields(zipped.read("toc.pb"))
    check.require(messages(toc, 4) == [b"com.catsmoker.app"], "Unexpected APK-set application identity")
    check.require(not messages(toc, 3), "Unreviewed APK-set asset modules")
    groups, kinds, all_paths = [], set(), []
    for variant in messages(toc, 1):
        paths = []
        for apk_set in messages(proto_fields(variant), 2):
            fields = proto_fields(apk_set)
            metadata = messages(fields, 1)
            check.require(len(metadata) == 1 and messages(proto_fields(metadata[0]), 1) == [b"base"],
                          "Unreviewed APK-set feature module")
            for description in messages(fields, 2):
                fields = proto_fields(description)
                names = messages(fields, 2)
                check.require(len(names) == 1, "APK description has no unique path")
                name = names[0].decode("utf-8")
                path = PurePosixPath(name)
                check.require(not path.is_absolute() and ".." not in path.parts and "\\" not in name
                              and ":" not in name and name.endswith(".apk"), "Unsafe APK-set path")
                check.require(bool(messages(fields, 3)) != bool(messages(fields, 4)),
                              "Unreviewed or ambiguous APK-set delivery mode")
                kinds.add("split" if messages(fields, 3) else "standalone")
                paths.append(name)
        check.require(paths and len(paths) == len(set(paths)), "Empty or duplicate APK-set variant")
        groups.append(paths)
        all_paths.extend(paths)
    actual = {name for name in entries if name.endswith(".apk")}
    check.require(groups and len(all_paths) == len(set(all_paths)), "Empty or duplicate APK-set inventory")
    check.require(set(all_paths) == actual, "APK-set ZIP differs from its complete table of contents")
    return groups, kinds


def full_output_abis(metadata_file, apk, root):
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    check.require(metadata.get("applicationId") == "com.catsmoker.app"
                  and metadata.get("variantName") == "fullRelease", "Unexpected Full output metadata")
    elements = metadata.get("elements", [])
    matches = [row for row in elements if row.get("outputFile") == apk.name]
    check.require(len(matches) == 1, "Full APK is not a unique Gradle output")
    supported = {item["value"] for row in elements for item in row.get("filters", [])
                 if item.get("filterType") == "ABI"}
    check.require(supported == check.FULL_ABIS, "Full output ABI set changed; review required")
    row = matches[0]
    for key in ("versionCode", "versionName"):
        check.require(str(row.get(key)) == root.get(check.ANDROID + key), "Full APK/metadata version differs")
    filters = row.get("filters", [])
    if row.get("type") == "UNIVERSAL":
        check.require(not filters, "Universal Full APK has unexpected filters")
        return supported
    check.require(row.get("type") == "ONE_OF_MANY" and len(filters) == 1
                  and filters[0].get("filterType") == "ABI", "Unexpected Full APK targeting")
    return {filters[0]["value"]}


def derived_variant_coverage(groups, by_path, bundle_payload, profiles):
    for group in groups:
        check.require(sum(by_path[name][1] for name in group) == 1,
                      "Derived APK variant has no unique code-bearing base")
        covered = set().union(*(by_path[name][0] for name in group))
        check.require(set(bundle_payload) - covered <= profiles,
                      "Derived APK variant omits bundle code/assets/native payload")


def derived_version(root, expected, has_code):
    for key in (("versionCode", "versionName") if has_code else ("versionCode",)):
        check.require(root.get(check.ANDROID + key) == expected.get(check.ANDROID + key),
                      f"Derived APK {key} differs from supplied bundle")


def inspect_apk(apk, variant, out, aapt2, code_expected=True, output_metadata=None, expected_manifest=None):
    with ZipFile(apk) as zipped:
        check.require(zipped.testzip() is None, f"Corrupt APK: {apk}")
        root = binary_manifest(zipped.read("AndroidManifest.xml"))
        check.require(root.get("package") == "com.catsmoker.app", "Unexpected split application identity")
        if variant == "full":
            metadata = output_metadata or apk.parent / "output-metadata.json"
            check.full_native_contract(zipped.namelist(), zipped.read, full_output_abis(metadata, apk, root))
        xml = out / (apk.stem + "-manifest.xml")
        ET.ElementTree(root).write(xml, encoding="utf-8", xml_declaration=True)
        # Configuration splits have no application-level registrations or AdMob
        # metadata. Inspect all their attributes, but do not require base content.
        has_code = any(re.fullmatch(r"classes\d*\.dex", n) for n in zipped.namelist())
        has_resources = "resources.arsc" in zipped.namelist()
        if expected_manifest is not None:
            derived_version(root, expected_manifest, has_code)
        if has_code:
            check.manifest(xml, variant)
        else:
            for node in root.iter():
                for value in node.attrib.values():
                    check.require(not check.prohibited_class(value, variant), f"Opposite flavor in split manifest: {value}")
                    if variant == "playstore":
                        check.require(value not in ("android.permission.MANAGE_EXTERNAL_STORAGE",
                            "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.BIND_VPN_SERVICE",
                            "android.net.VpnService"), f"Forbidden split manifest entry: {value}")
            check.require(not code_expected, f"Expected code-bearing APK: {apk}")
        for name in zipped.namelist():
            if variant == "playstore":
                check.require(name.rsplit("/", 1)[-1] not in (
                    "xposed_init", "native_init", "libcatsmoker_bridge.so", "ic_cat_sad.png"),
                    f"Full-only split payload: {name}")
        payload = {name: hashlib.sha256(zipped.read(name)).hexdigest()
                   for name in zipped.namelist() if name.endswith((".dex", ".so")) or name.startswith("assets/")}
    if has_code:
        check.archive(apk, variant)
        check.require(has_resources, f"Code-bearing app APK has no resource table: {apk}")
    if not has_resources:
        # ABI-only configuration APKs contain a manifest and SOs, not a table.
        # Their manifest and payload hashes are still checked above/by the caller.
        return payload, has_code
    resources = out / (apk.stem + "-resources.txt")
    run([aapt2, "dump", "resources", apk], resources)
    text = resources.read_text(encoding="utf-8")
    if variant == "playstore":
        check.require("drawable/ic_cat_sad" not in text, "Full artwork in Play APK")
    elif has_code:
        # Resource shrinking renames ZIP entries (e.g. res/9o.png). Resolve the
        # actual table reference rather than assuming the source filename ships.
        artwork = re.search(r"\bdrawable/ic_cat_sad\s*\n\s*\([^\n]*\) \(file\) (\S+)", text)
        check.require(artwork is not None, "Full artwork resource payload missing")
        with ZipFile(apk) as zipped:
            check.require(artwork[1] in zipped.namelist(), "Full artwork references absent file")
            check.require(zipped.read(artwork[1]).startswith(b"\x89PNG\r\n\x1a\n"),
                          "Full artwork is not a packaged PNG")
    names = re.findall(r"\bstring/([A-Za-z0-9_]+)", text)
    for name in names:
        check.require(not ((check.DENIED_RESOURCES.match(name) or name in check.FULL_RESOURCES)
                           if variant == "playstore" else (check.PLAY_ONLY_RESOURCES.match(name) or name in check.PLAY_RESOURCES)),
                      f"Opposite distribution resource in APK: {name}")
    if has_code:
        check.resources(resources, variant)
    return payload, has_code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=("full", "playstore"), required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--audit-directory", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--bundletool", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--apks", type=Path, action="append", default=[])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--output-metadata", type=Path, help="Full Gradle APK metadata (defaults to the APK directory)")
    args = parser.parse_args()
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    check.sources()
    audit = args.audit_directory
    check.manifest(audit / "AndroidManifest.xml", args.variant)
    check.dependencies(audit / "dependencies.txt", args.variant)
    check.dependencies(audit / "compile-dependencies.txt", args.variant, compile_only=True)
    check.source_inputs(audit / "sources.txt", args.variant)
    check.mapping(args.mapping, args.variant)
    check.matching_mapping(args.artifact, args.mapping)
    inspected = []
    inspected_sets = []
    if args.artifact.suffix == ".aab":
        check.require(args.variant == "playstore", "Full release packaging uses APKs, not a bundle")
        check.require(args.java and args.bundletool, "AAB inspection needs Java and official bundletool")
        command = [args.java, "-jar", args.bundletool]
        for operation, name in ((["validate"], "validate.txt"), (["dump", "manifest"], "AndroidManifest.xml"),
                                (["dump", "resources", "--values"], "resources.txt"), (["dump", "config"], "config.txt")):
            run([*command, *operation, "--bundle=" + str(args.artifact)], out / name)
        check.manifest(out / "AndroidManifest.xml", args.variant)
        expected_manifest = ET.parse(out / "AndroidManifest.xml").getroot()
        check.resources(out / "resources.txt", args.variant)
        check.archive(args.artifact, args.variant)
        with ZipFile(args.artifact) as zipped:
            modules = {name.split("/", 1)[0] for name in zipped.namelist() if name.endswith("/manifest/AndroidManifest.xml")}
            check.require(modules == {"base"}, "Unreviewed bundle feature modules")
            bundle_payload = {(name.removeprefix("base/dex/") if name.startswith("base/dex/") else name.removeprefix("base/")):
                              hashlib.sha256(zipped.read(name)).hexdigest()
                              for name in zipped.namelist() if name.endswith((".dex", ".so")) or name.startswith("base/assets/")}
            # Bundletool relocates/transcodes these non-executable ART profiles
            # for the target Android version; their bytes need not equal the AAB.
            profiles = {"assets/dexopt/" + name.rsplit("/", 1)[-1]
                        for name in zipped.namelist()
                        if name.startswith("BUNDLE-METADATA/com.android.tools.build.profiles/")
                        and name.rsplit("/", 1)[-1] in ("baseline.prof", "baseline.profm")}
        check.require(bool(args.apks), "AAB verified but derived APK sets were not supplied")
        inspected_modes = set()
        for number, apks in enumerate(args.apks):
            with ZipFile(apks) as zipped:
                groups, modes = apk_set_inventory(zipped)
                inspected_modes.update(modes)
                inspected_sets.append({"path": str(apks.resolve()), "sha256": digest(apks),
                                       "toc_sha256": hashlib.sha256(zipped.read("toc.pb")).hexdigest(),
                                       "modes": sorted(modes), "variants": groups})
                by_path = {}
                for entry in (name for group in groups for name in group):
                    target = out / f"set{number}-{entry.replace('/', '-') }"
                    target.write_bytes(zipped.read(entry))
                    payload, has_code = inspect_apk(target, args.variant, out, args.aapt2, code_expected=False,
                                                   expected_manifest=expected_manifest)
                    for name, sha in payload.items():
                        if name in profiles:
                            with ZipFile(target) as apk_zip:
                                expected_magic = b"prm\0" if name.endswith(".profm") else b"pro\0"
                                check.require(apk_zip.read(name).startswith(expected_magic), f"Invalid derived ART profile: {name}")
                            continue
                        check.require(bundle_payload.get(name) == sha, f"APK payload does not match supplied AAB: {target}: {name}")
                    inspected.append({"apk": str(target), "sha256": digest(target), "has_code": has_code})
                    by_path[entry] = (set(payload) - profiles, has_code)
                derived_variant_coverage(groups, by_path, bundle_payload, profiles)
        check.require(inspected_modes == {"standalone", "split"},
                      "Both universal and complete split APK sets are required")
        check.require(any(row["has_code"] for row in inspected), "No code-bearing derived APK inspected")
    else:
        inspect_apk(args.artifact, args.variant, out, args.aapt2, output_metadata=args.output_metadata)
        inspected.append({"apk": str(args.artifact), "sha256": digest(args.artifact), "has_code": True})
    evidence = {"variant": args.variant, "artifact": str(args.artifact.resolve()), "sha256": digest(args.artifact),
                "mapping_sha256": digest(args.mapping), "audit_inputs": {
                    name: digest(audit / name) for name in ("AndroidManifest.xml", "dependencies.txt",
                        "compile-dependencies.txt", "sources.txt", "artifacts.txt")}, "apks": inspected,
                "apk_sets": inspected_sets,
                "inspection_tools": {path.name: digest(path) for path in (
                    Path(__file__), Path(check.__file__))}}
    if args.variant == "full":
        evidence["output_metadata_sha256"] = digest(args.output_metadata or args.artifact.parent / "output-metadata.json")
    (out / "evidence.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    print(f"PASS {args.variant}: {len(inspected)} APKs inspected; artifact SHA256 {evidence['sha256']}")
    print("LIMIT: static boundary evidence, not device behavior, SDK runtime traffic or Play policy approval")


if __name__ == "__main__":
    main()
