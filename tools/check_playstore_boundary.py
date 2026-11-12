"""Read-only Play flavor regression check. Python standard library only.

Source checks are useful before a build, but do NOT prove the shipped artifact.
Supply the fresh Gradle audit export, AAB, R8 mapping and derived APKs for that.
This is a boundary check, not a Google Play policy approval or malware scanner.
"""

import argparse
import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
DENIED_PACKAGES = (
    "com.catsmoker.app.screens.spoofing.",
    "com.catsmoker.app.features.spoofdevice.",
    "com.startapp.", "com.truenet.", "de.robv.android.xposed.",
)
DENIED_CLASSES = (
    "com.catsmoker.app.screens.gaming.network.VpnFirewall",
    "com.catsmoker.app.screens.gaming.network.VpnFirewallService",
    "com.catsmoker.app.system.shell.NativeBridge",
    "com.catsmoker.app.system.config.SpoofConfigProvider",
    "com.catsmoker.app.screens.gaming.network.VpnControlsViewModel",
    "com.catsmoker.app.screens.spoofing.data.repository.SpoofRepository",
    "com.catsmoker.app.screens.spoofing.hooks.LSPosedConfig",
    "com.catsmoker.app.screens.spoofing.data.model.DeviceProfile",
    "com.catsmoker.app.screens.spoofing.data.model.DevicePreset",
    # Also reject pre-reorganization names when inspecting older artifacts.
    "com.catsmoker.app.shared.data.repository.SpoofRepository",
    "com.catsmoker.app.shared.data.model.LSPosedConfig",
    "com.catsmoker.app.shared.data.model.DeviceProfile",
    "com.catsmoker.app.shared.data.model.DevicePreset",
)
DENIED_RESOURCES = re.compile(
    r"^(?:spoof_|dash_spoof_|gt_net_vpn_|gt_vpn_|gt_vm_vpn_|gt_svc_vpn_|gt_net_which_|gt_net_what_2$|"
    r"sys_ads_(?:guilt_|title$|sub$)|sys_(?:section_updates$|updating$|check_updates$|checking_updates$|"
    r"prerelease$|auto_check$|update_|updates_|no_releases$|up_to_date$|no_suitable_release$|download(?:_failed)?$))"
)
PLAY_ONLY_RESOURCES = re.compile(r"^sys_(?:remove_ads_|ads_removed_|billing_|rate_app_)")
# Verified against the resolved Start.io 5.4.0 AAR, not guessed by its filename.
# A new SDK asset or changed bytes requires a fresh ownership review.
FULL_SDK_ASSETS = {
    "io_start_sdk_vast_endcard_smart.html": "f4cc7a038e64d4bd0f1cfd52683721d34f1ddd0135a62787e6ce27e00608b67e"
}
FULL_ABIS = frozenset(("arm64-v8a", "armeabi-v7a", "x86_64"))
FULL_INIT = {
    "native_init": "libcatsmoker_bridge.so",
    "xposed_init": "com.catsmoker.app.features.spoofdevice.root.LSPosedModule",
}
REQUIRED_FULL_CLASSES = (
    "com.catsmoker.app.features.spoofdevice.root.LSPosedModule",
    "com.catsmoker.app.system.shell.NativeBridge",
    "com.catsmoker.app.screens.gaming.network.VpnFirewallService",
    "com.catsmoker.app.screens.settings.FullSettingsViewModel",
    "com.catsmoker.app.screens.settings.SelfUpdater",
    "com.catsmoker.app.screens.spoofing.tools.MagiskModuleBuilder",
    "com.catsmoker.app.screens.spoofing.data.repository.SpoofRepository",
    "com.catsmoker.app.screens.spoofing.data.device.GraphicsApiDetector",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def denied_class(name):
    return name.startswith(DENIED_PACKAGES) or any(
        name == item or name.startswith(item + "$") for item in DENIED_CLASSES
    )


def source_symbols(file):
    text = file.read_text(encoding="utf-8")
    package = re.search(r"^package ([\w.]+)", text, re.M)
    if not package:
        return set()
    names = re.findall(
        r"^(?:(?:public|private|internal|data|sealed|enum|abstract|open|annotation|value) )*"
        r"(?:class|object|interface) (\w+)", text, re.M
    )
    # Kotlin top-level functions compile to a file facade, even without a class.
    if re.search(r"^(?:(?:public|private|internal|suspend|inline|tailrec) )*fun ", text, re.M):
        names.append(file.stem + "Kt")
    return {package[1] + "." + name for name in names}


def exclusive_classes(owner):
    """Compare symbols, not file paths: a flavor adapter can own extra classes."""
    result = set()
    base = ROOT / f"app/src/{owner}/java"
    other = "playstore" if owner == "full" else "full"
    for file in base.rglob("*.kt"):
        relative = file.relative_to(base)
        shared_symbols = set()
        for source in ("main", other):
            counterpart = ROOT / "app/src" / source / "java" / relative
            if counterpart.exists():
                shared_symbols.update(source_symbols(counterpart))
        result.update(source_symbols(file) - shared_symbols)
    return result


EXCLUSIVE_CLASSES = exclusive_classes("full")
PLAY_CLASSES = exclusive_classes("playstore")


def resource_names(owner):
    return {node.get("name") for file in (ROOT / f"app/src/{owner}/res").glob("values*/*.xml")
            for node in ET.parse(file).getroot() if node.get("name")}


FULL_RESOURCES = resource_names("full") - resource_names("main") - resource_names("playstore")
PLAY_RESOURCES = resource_names("playstore") - resource_names("main") - resource_names("full")


def prohibited_class(name, variant):
    if variant == "playstore":
        return denied_class(name) or name.split("$", 1)[0] in EXCLUSIVE_CLASSES
    # Advertising ID is a separate shared transitive library (Analytics/Start.io),
    # not the Play AdMob rendering SDK. Do not ban it by an overlapping namespace.
    if name.startswith("com.google.android.gms.ads.identifier."):
        return False
    return (name.startswith(("com.android.billingclient.", "com.google.android.gms.ads.",
                             "com.google.ads.", "com.google.android.ump.",
                             "com.google.android.gms.internal.ads.",
                             "com.google.android.gms.internal.play_billing.",
                             "com.google.android.gms.internal.consent_sdk."))
            or name.split("$", 1)[0] in PLAY_CLASSES)


def sources():
    banned = (
        "import com.catsmoker.app.screens.spoofing.",
        "import android.net.VpnService", "import com.startapp.",
        "import de.robv.android.xposed.", "import com.catsmoker.app.features.spoofdevice.",
        "Environment.isExternalStorageManager(", "ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION",
        "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", "DexClassLoader(",
        "InMemoryDexClassLoader(",
        "SelfUpdater", "FullSettingsViewModel",
    )
    for source_set in ("main", "playstore"):
        base = ROOT / "app/src" / source_set
        for file in base.rglob("*"):
            if file.suffix in (".kt", ".java", ".c", ".cpp"):
                text = file.read_text(encoding="utf-8")
                tokens = banned + (("RemoveAdsRepository", "RemoveAdsPolicy") if source_set == "main" else ())
                for token in tokens:
                    require(token not in text, f"{file.relative_to(ROOT)} contains {token}")
            if file.suffix == ".xml" and file.parent.name.startswith("values"):
                for node in ET.parse(file).getroot():
                    require(not DENIED_RESOURCES.match(node.get("name", "")),
                             f"Full-only resource in {file.relative_to(ROOT)}: {node.get('name')}")
                    require(source_set != "main" or not PLAY_ONLY_RESOURCES.match(node.get("name", "")),
                            f"Play-only resource returned to main: {file.relative_to(ROOT)}: {node.get('name')}")
    for file in (ROOT / "app/src/full/res").glob("values*/*.xml"):
        for node in ET.parse(file).getroot():
            require(not PLAY_ONLY_RESOURCES.match(node.get("name", "")), f"Play-only resource in Full: {file}")
    require(not list((ROOT / "app/src/main/jni").glob("*.c")), "App native C source returned to main")
    require((ROOT / "app/src/full/jni/catsmoker_bridge.c").is_file(), "Missing full native bridge")
    selection = (ROOT / "app/src/main/jni/Android.mk").read_text()
    require("ifeq ($(CATSMOKER_FULL),1)" in selection, "Native flavor selection changed; review required")
    for file in (ROOT / "app/src/full/java").rglob("*.kt"):
        text = file.read_text(encoding="utf-8")
        require(not any(token in text for token in (
            "import com.android.billingclient.", "import com.google.android.gms.ads.",
            "import com.google.android.ump.", "RemoveAdsRepository", "RemoveAdsPolicy"
        )), f"Play implementation referenced in Full source: {file}")
    print("PASS source/API/resource boundary (not an artifact proof)")


def manifest(file, variant="playstore"):
    root = ET.parse(file).getroot()
    manifest_root(root, variant)
    print(f"PASS final {variant} manifest: {file}")


def manifest_root(root, variant):
    require(root.get("package") == "com.catsmoker.app", "Unexpected application identity")
    denied = {"android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.REQUEST_INSTALL_PACKAGES",
              "android.permission.BIND_VPN_SERVICE", "android.net.VpnService"}
    app = root.find("application")
    require(app is not None, "Missing application")
    require(app.get(ANDROID + "debuggable", "false") == "false", "Release is debuggable")
    for node in root.iter():
        name = node.get(ANDROID + "name", "")
        require(not prohibited_class(name, variant), f"Forbidden manifest class: {name}")
        if variant == "full":
            continue
        require(name not in denied and not denied_class(name), f"Forbidden manifest entry: {name}")
        permission = node.get(ANDROID + "permission", "")
        require(permission not in denied, f"Forbidden component permission: {permission}")
        require(not name.startswith("xposed"), f"Xposed metadata: {name}")
        require(node.get(ANDROID + "authorities") != "com.catsmoker.app.configprovider", "Spoof provider")
    names = {n.get(ANDROID + "name") for n in root.iter()}
    admob = "com.google.android.gms.ads.APPLICATION_ID" in names
    if variant == "playstore":
        require(admob, "Missing Play AdMob metadata")
    else:
        require(not admob and "com.android.vending.BILLING" not in names, "Play ads/billing in Full manifest")
        for name in ("android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.REQUEST_INSTALL_PACKAGES",
                     "android.net.VpnService", "xposedmodule"):
            require(name in names, f"Full functionality missing from manifest: {name}")
    for tag in ("service", "provider", "receiver", "activity"):
        entries = [n.get(ANDROID + "name") for n in app.findall(tag)]
        require(len(entries) == len(set(entries)), f"Duplicate {tag} registration")


def dependencies(file, variant="playstore", compile_only=False):
    text = file.read_text(encoding="utf-8").lower()
    forbidden = (("com.startapp", "com.truenet", "de.robv.android.xposed", "xposed:api")
                 if variant == "playstore" else
                 ("play-services-ads:", "play-services-ads-lite:", "com.android.billingclient", "user-messaging-platform:"))
    for token in forbidden:
        require(token not in text, f"Full-only resolved dependency: {token}")
    if variant == "full":
        require(not re.search(r"com\.google\.android\.gms:play-services-ads(?!-identifier:)[^\s]*", text),
                "Play ads implementation in Full resolved graph")
    if variant == "playstore":
        require("play-services-ads:" in text and "billing:" in text, "Missing Play ads/billing graph")
    else:
        require("com.startapp:inapp-sdk:" in text, "Missing Full Start.io dependency")
        require(("de.robv.android.xposed:api:" in text) == compile_only,
                "Xposed must be Full compile-only, never runtime")
    print(f"PASS resolved release dependency graph: {file}")


def dex_tables(data):
    """Read ALL DEX type references and defined classes (not a binary text grep)."""
    require(data[:4] == b"dex\x0a", "Unsupported DEX format")
    u32 = lambda at: struct.unpack_from("<I", data, at)[0]
    strings = []
    for index in range(u32(56)):
        offset = u32(u32(60) + index * 4)
        while data[offset] & 128:  # Skip ULEB128 UTF-16 length.
            offset += 1
        offset += 1
        end = data.index(0, offset)
        strings.append(data[offset:end].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    defined = {types[u32(u32(100) + i * 32)] for i in range(u32(96))}
    return types, defined


def elf_exports(data):
    """Read defined dynamic symbols from the packaged 32/64-bit little-endian SO."""
    require(data[:4] == b"\x7fELF" and data[5] == 1, "Unsupported native ELF")
    wide = data[4] == 2
    require(data[4] in (1, 2), "Unsupported ELF class")
    number = lambda at, size: int.from_bytes(data[at:at + size], "little")
    table = number(40 if wide else 32, 8 if wide else 4)
    stride = number(58 if wide else 46, 2)
    count = number(60 if wide else 48, 2)
    symbols = set()
    for index in range(count):
        section = table + index * stride
        if number(section + 4, 4) != 11:  # SHT_DYNSYM
            continue
        offset = number(section + (24 if wide else 16), 8 if wide else 4)
        size = number(section + (32 if wide else 20), 8 if wide else 4)
        link = number(section + (40 if wide else 24), 4)
        entry_size = number(section + (56 if wide else 36), 8 if wide else 4)
        require(entry_size > 0, "Missing dynamic symbol stride")
        strings_section = table + link * stride
        strings_offset = number(strings_section + (24 if wide else 16), 8 if wide else 4)
        for at in range(offset, offset + size, entry_size):
            name = number(at, 4)
            defined = number(at + (6 if wide else 14), 2)
            if name and defined:
                start = strings_offset + name
                symbols.add(data[start:data.index(0, start)].decode("utf-8"))
    return symbols


def asset_hashes(variant):
    result = {}
    for source in ("main", variant):
        root = ROOT / f"app/src/{source}/assets"
        result.update({file.relative_to(root).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest()
                       for file in root.rglob("*") if file.is_file()})
    if variant == "full":
        result.update(FULL_SDK_ASSETS)
    return result


def full_native_contract(names, read, expected_abis=None):
    """Check loader declarations and each supported ABI, not just one SO leaf."""
    bridge_abis = {match[1] for name in names
                   if (match := re.fullmatch(r"(?:base/)?lib/([^/]+)/libcatsmoker_bridge\.so", name))}
    require(bridge_abis and bridge_abis <= FULL_ABIS, "Missing or unexpected Full bridge ABI")
    if expected_abis is not None:
        require(bridge_abis == set(expected_abis),
                f"Full bridge ABI coverage differs: expected {sorted(expected_abis)}, got {sorted(bridge_abis)}")
    for leaf, expected in FULL_INIT.items():
        paths = [name for name in names if name in ("assets/" + leaf, "base/assets/" + leaf)]
        require(len(paths) == 1, f"Missing or ambiguous Full loader declaration: {leaf}")
        require(read(paths[0]).decode("utf-8").strip() == expected,
                f"Full loader declaration points at the wrong entry point: {leaf}")


def archive(file, variant="playstore"):
    count = definitions = references = 0
    with ZipFile(file) as archive_file:
        for name in archive_file.namelist():
            leaf = name.rsplit("/", 1)[-1]
            require(variant == "full" or leaf not in ("xposed_init", "native_init", "libcatsmoker_bridge.so", "ic_cat_sad.png"),
                    f"Full-only packaged entry: {name}")
            if re.search(r"(?:^|/)classes\d*\.dex$", name):
                count += 1
                types, defined = dex_tables(archive_file.read(name))
                definitions += len(defined)
                references += len(types)
                require(not any(n.startswith("Lde/robv/android/xposed/") for n in defined),
                        "Compile-only Xposed API definitions packaged in release")
                for descriptor in types:
                    cls = descriptor.lstrip("[").removeprefix("L").removesuffix(";").replace("/", ".")
                    require(not prohibited_class(cls, variant),
                            f"Forbidden DEX type in {name}: {cls}")
                    require(variant == "full" or cls not in ("android.net.VpnService", "android.net.VpnService$Builder"),
                            f"VPN establishment API in {name}: {cls}")
        require(count > 0, f"No DEX found: {file}")
        natives = [n for n in archive_file.namelist() if n.endswith(".so")]
        allowed_native = {"libandroidx.graphics.path.so", "libdatastore_shared_counter.so"}
        if variant == "full":
            allowed_native.add("libcatsmoker_bridge.so")
        for name in natives:
            require(name.rsplit("/", 1)[-1] in allowed_native, f"Unclassified native library: {name}")
            if name.endswith("/libcatsmoker_bridge.so"):
                required = {"native_init"}
                for source in (ROOT / "app/src/full/jni").glob("*.c"):
                    required.update(re.findall(r"\b(Java_com_catsmoker_app_\w+)\s*\(", source.read_text()))
                exports = elf_exports(archive_file.read(name))
                require(required <= exports, f"Full native entry points missing from {name}: {required - exports}")
                print(f"PASS Full native exports: {len(required)} entry points; {name}")
        assets = [n for n in archive_file.namelist() if "/assets/" in n or n.startswith("assets/")]
        if variant == "full":
            leaves = {n.rsplit("/", 1)[-1] for n in archive_file.namelist()}
            # R8 may rename drawable ZIP paths; artwork is checked by its resource
            # entry and referenced payload by the artifact inspector instead.
            for name in ("xposed_init", "native_init", "libcatsmoker_bridge.so"):
                require(name in leaves, f"Full payload missing: {name}")
            full_native_contract(archive_file.namelist(), archive_file.read)
        expected_assets = asset_hashes(variant)
        for name in assets:
            relative = name.split("assets/", 1)[1]
            data = archive_file.read(name)
            if relative in ("dexopt/baseline.prof", "dexopt/baseline.profm"):
                require(data.startswith(b"prm\0" if relative.endswith(".profm") else b"pro\0"),
                        f"Invalid ART profile: {name}")
            else:
                require(relative in expected_assets, f"Unclassified packaged asset: {name}")
                require(hashlib.sha256(data).hexdigest() == expected_assets[relative],
                        f"Packaged asset does not match reviewed source/SDK: {name}")
        print(f"Native entries: {natives}")
        print(f"Asset entries: {assets}")
    with file.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    print(f"PASS {file}: {count} DEX, {definitions} class definitions, {references} type references; SHA256 {digest}")


def mapping(file, variant="playstore"):
    classes = 0
    originals = set()
    with file.open(encoding="utf-8") as stream:
        for raw in stream:
            line = raw.rstrip("\r\n")
            if line and not line[0].isspace() and " -> " in line and line.endswith(":"):
                original = line.split(" -> ", 1)[0]
                require(not prohibited_class(original, variant),
                        f"Full-only original class in R8 mapping: {original}")
                classes += 1
                originals.add(original)
    require(classes > 0, "Empty or invalid R8 mapping")
    if variant == "full":
        for required in REQUIRED_FULL_CLASSES:
            require(required in originals, f"Full implementation missing from R8 inventory: {required}")
    print(f"PASS R8 original-name inventory: {classes} classes; {file}")


def resources(file, variant="playstore"):
    text = file.read_text(encoding="utf-8-sig")
    require(variant == "full" or "drawable/ic_cat_sad" not in text,
            "Full-only artwork packaged in Play resource table")
    if variant == "full":
        require("drawable/ic_cat_sad" in text, "Full artwork resource missing")
    names = re.findall(r"\bstring/([A-Za-z0-9_]+)", text)
    require(bool(names), "No named string resources in bundletool resource dump")
    for name in names:
        forbidden = ((DENIED_RESOURCES.match(name) or name in FULL_RESOURCES)
                     if variant == "playstore" else (PLAY_ONLY_RESOURCES.match(name) or name in PLAY_RESOURCES))
        require(not forbidden, f"Full-only resource packaged: {name}" if variant == "playstore"
                else f"Play-only resource packaged: {name}")
    require("gt_net_ds_title" in names, "Shared Data Saver resource missing")
    print(f"PASS final resource table: {len(names)} string entries; {file}")


def source_inputs(file, variant):
    other = "full" if variant == "playstore" else "playstore"
    directories = []
    for line in file.read_text(encoding="utf-8").splitlines():
        kind, raw = line.split("\t", 1)
        normalized = raw.replace("\\", "/").lower()
        require(not re.search(rf"/(?:src|generated/[^/]+)/(?:{other})(?:/|release/|debug/)", normalized)
                and f"{other}release" not in normalized and f"{other}debug" not in normalized,
                f"Opposite distribution source input: {raw}")
        directories.append(normalized)
        if kind in ("java", "kotlin") and Path(raw).exists():
            for source in Path(raw).rglob("*"):
                if source.suffix in (".kt", ".java"):
                    text = source.read_text(encoding="utf-8")
                    for symbol in EXCLUSIVE_CLASSES if variant == "playstore" else PLAY_CLASSES:
                        require(symbol not in text, f"Opposite distribution reference in selected/generated source: {source}: {symbol}")
    require(any(f"/src/{variant}/" in path for path in directories), "Missing selected flavor sources")
    require(any("/src/main/" in path for path in directories), "Missing shared sources")
    print(f"PASS AGP selected/generated source directories: {len(directories)}; {file}")


def matching_mapping(archive_file, mapping_file):
    if archive_file.suffix == ".apk":
        map_id, required = None, {}
        with mapping_file.open(encoding="utf-8") as stream:
            for line in stream:
                if line.startswith("# pg_map_id: "):
                    map_id = line.split(": ", 1)[1].strip()
                elif line and not line[0].isspace() and " -> " in line and line.rstrip().endswith(":"):
                    original, target = line.strip()[:-1].split(" -> ", 1)
                    if original in REQUIRED_FULL_CLASSES:
                        required[original] = "L" + target.replace(".", "/") + ";"
        definitions, marker_ids = set(), set()
        with ZipFile(archive_file) as zipped:
            for name in zipped.namelist():
                if re.fullmatch(r"classes\d*\.dex", name):
                    data = zipped.read(name)
                    definitions.update(dex_tables(data)[1])
                    for marker in re.findall(rb'~~R8(\{[^\x00]*\})', data):
                        info = json.loads(marker)
                        if info.get("backend") == "dex":
                            marker_ids.add(info.get("pg-map-id"))
        require(map_id is not None and map_id in marker_ids,
                "Stale/mismatched R8 mapping: APK DEX map ID differs")
        for original, descriptor in required.items():
            require(descriptor in definitions, f"Required Full class not defined in packaged DEX: {original}")
        print("PASS APK DEX R8 map ID and required mapped class definitions")
        return
    with ZipFile(archive_file) as zipped:
        name = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"
        require(name in zipped.namelist(), "AAB has no embedded R8 mapping")
        actual = hashlib.sha256(zipped.read(name)).digest()
    with mapping_file.open("rb") as stream:
        require(actual == hashlib.file_digest(stream, "sha256").digest(), "Stale/mismatched R8 mapping for this AAB")
    print("PASS AAB embedded mapping matches external original-name inventory")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=("full", "playstore"), default="playstore")
    parser.add_argument("--manifest", action="append", type=Path, default=[])
    parser.add_argument("--dependencies", type=Path)
    parser.add_argument("--archive", action="append", type=Path, default=[])
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--resources", type=Path, help="bundletool dump resources output")
    parser.add_argument("--source-inputs", type=Path)
    parser.add_argument("--compile-dependencies", type=Path)
    parser.add_argument("--require-artifacts", action="store_true")
    args = parser.parse_args()
    complete = (args.manifest and args.dependencies and args.archive and args.mapping and args.resources
                and args.source_inputs and args.compile_dependencies)
    require(not args.require_artifacts or complete, "All artifact inputs required; missing verification evidence")
    sources()
    for file in args.manifest:
        manifest(file, args.variant)
    if args.dependencies:
        dependencies(args.dependencies, args.variant)
    if args.compile_dependencies:
        dependencies(args.compile_dependencies, args.variant, compile_only=True)
    if args.source_inputs:
        source_inputs(args.source_inputs, args.variant)
    for file in args.archive:
        archive(file, args.variant)
        if args.mapping:
            matching_mapping(file, args.mapping)
    if args.mapping:
        mapping(args.mapping, args.variant)
    if args.resources:
        resources(args.resources, args.variant)
    if not complete:
        print("LIMIT: incomplete artifact evidence; source-only/partial checks cannot certify an upload.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError, struct.error) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
