"""Focused negative tests for the boundary guard; not release-readiness evidence."""

import struct
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

import check_playstore_boundary as check
import inspect_distribution_artifact as inspect


def proto(**fields):
    """Small length-delimited fixture encoder; keys are protobuf field numbers."""
    def varint(value):
        result = bytearray()
        while value > 127:
            result.append((value & 127) | 128)
            value >>= 7
        return bytes(result) + bytes([value])

    return b"".join(varint((int(number) << 3) | 2) + varint(len(value)) + value
                    for number, values in fields.items() for value in values)


def apk_toc(paths, package=b"com.catsmoker.app"):
    descriptions = [proto(**{"2": [name.encode()], "3": [b"\x08\x01"]}) for name in paths]
    apk_set = proto(**{"1": [proto(**{"1": [b"base"]})], "2": descriptions})
    variant = proto(**{"2": [apk_set]})
    return proto(**{"1": [variant], "4": [package]})


class BoundaryGuardTest(unittest.TestCase):
    def test_obfuscated_full_class_is_rejected_by_original_name(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "mapping.txt"
            file.write_text("com.catsmoker.app.features.spoofdevice.root.LSPosedModule -> a.b:\n")
            with self.assertRaisesRegex(ValueError, "Full-only original class"):
                check.mapping(file)

    def test_shared_root_and_vpn_detection_are_not_class_bans(self):
        for name in ("com.topjohnwu.superuser.Shell", "rikka.shizuku.Shizuku",
                     "android.net.NetworkCapabilities",
                     "com.catsmoker.app.screens.gaming.network.BackgroundDataRestrictor"):
            self.assertFalse(check.denied_class(name), name)

    def test_full_vpn_resource_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "resources.txt"
            file.write_text("string/gt_net_ds_title\nstring/gt_net_vpn_title\n")
            with self.assertRaisesRegex(ValueError, "Full-only resource"):
                check.resources(file)

    def test_spoof_resource_is_rejected(self):
        self.assertIsNotNone(check.DENIED_RESOURCES.match("spoof_home_subtitle"))

    def test_extracted_settings_resources_cannot_be_reclassified_as_shared(self):
        for name in ("dash_spoof_title", "sys_update_prompt", "sys_ads_guilt_keep", "sys_download"):
            self.assertIsNotNone(check.DENIED_RESOURCES.match(name))
        for name in ("sys_remove_ads_title", "sys_rate_app_title", "sys_billing_error"):
            self.assertIsNotNone(check.PLAY_ONLY_RESOURCES.match(name))

    def test_vpn_detection_caption_is_allowed(self):
        self.assertIsNone(check.DENIED_RESOURCES.match("gt_dns_vpn_note"))

    def test_manifest_rejects_reintroduced_permission(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "AndroidManifest.xml"
            file.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.catsmoker.app">
                <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
                <application/></manifest>''')
            with self.assertRaisesRegex(ValueError, "Forbidden manifest entry"):
                check.manifest(file)

    def test_dex_reader_includes_undefined_platform_type_references(self):
        # Minimal table fixture: one defined app class and an external VPN API.
        strings = [b"Lexample/App;", b"Landroid/net/VpnService;"]
        data = bytearray(112 + 8 + 8 + 32)
        data[:8] = b"dex\n035\0"
        for at, value in ((56, 2), (60, 112), (64, 2), (68, 120), (96, 1), (100, 128)):
            struct.pack_into("<I", data, at, value)
        for i, value in enumerate(strings):
            struct.pack_into("<I", data, 112 + i * 4, len(data))
            struct.pack_into("<I", data, 120 + i * 4, i)
            data.extend(bytes([len(value)]) + value + b"\0")
        types, defined = check.dex_tables(bytes(data))
        self.assertIn("Landroid/net/VpnService;", types)
        self.assertNotIn("Landroid/net/VpnService;", defined)
        self.assertEqual({"Lexample/App;"}, defined)

    def test_vpn_binding_permission_on_generic_service_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "AndroidManifest.xml"
            file.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.catsmoker.app"><application>
                <service android:name="example.Service"
                    android:permission="android.permission.BIND_VPN_SERVICE"/>
                </application></manifest>''')
            with self.assertRaisesRegex(ValueError, "Forbidden component permission"):
                check.manifest(file)

    def test_top_level_full_only_functions_have_a_facade_boundary(self):
        self.assertIn("com.catsmoker.app.screens.settings.UpdateNotesPreviewKt", check.EXCLUSIVE_CLASSES)

    def test_same_path_adapter_does_not_exempt_play_only_classes(self):
        self.assertIn("com.catsmoker.app.screens.settings.PlayAdsSettingsViewModel", check.PLAY_CLASSES)

    def test_relocated_spoof_data_keeps_new_and_historical_boundaries(self):
        for name in (
            "com.catsmoker.app.screens.spoofing.data.repository.SpoofRepository",
            "com.catsmoker.app.screens.spoofing.hooks.LSPosedConfig",
            "com.catsmoker.app.screens.spoofing.data.model.DeviceProfile",
            "com.catsmoker.app.screens.spoofing.data.model.DevicePreset",
            "com.catsmoker.app.shared.data.repository.SpoofRepository",
            "com.catsmoker.app.shared.data.model.LSPosedConfig",
            "com.catsmoker.app.shared.data.model.DeviceProfile",
            "com.catsmoker.app.shared.data.model.DevicePreset",
        ):
            self.assertTrue(check.denied_class(name), name)
            self.assertTrue(check.denied_class(name + "$Nested"), name)
        for name in check.REQUIRED_FULL_CLASSES:
            self.assertIn(name, check.EXCLUSIVE_CLASSES)

    def test_full_rejects_play_billing_original_names(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "mapping.txt"
            file.write_text("com.android.billingclient.api.BillingClient -> a.b:\n")
            with self.assertRaisesRegex(ValueError, "original class"):
                check.mapping(file, "full")

    def test_shared_advertising_id_is_not_play_admob(self):
        self.assertFalse(check.prohibited_class("com.google.android.gms.ads.identifier.AdvertisingIdClient", "full"))
        self.assertTrue(check.prohibited_class("com.google.android.gms.ads.MobileAds", "full"))

    def test_full_also_rejects_sdk_internal_implementation_namespaces(self):
        for name in ("com.google.android.gms.internal.ads.zza",
                     "com.google.android.gms.internal.play_billing.zzaj",
                     "com.google.android.gms.internal.consent_sdk.zzb"):
            self.assertTrue(check.prohibited_class(name, "full"), name)
        self.assertFalse(check.prohibited_class("com.google.android.gms.internal.ads_identifier.zzf", "full"))

    def test_full_cannot_resolve_only_the_admob_api_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "dependencies.txt"
            file.write_text("com.startapp:inapp-sdk:5.4.0\ncom.google.android.gms:play-services-ads-api:25.5.0\n")
            with self.assertRaisesRegex(ValueError, "Play ads implementation in Full"):
                check.dependencies(file, "full")

    def test_startio_sdk_asset_is_full_only_and_pinned(self):
        name = "io_start_sdk_vast_endcard_smart.html"
        self.assertNotIn(name, check.asset_hashes("playstore"))
        self.assertEqual(check.FULL_SDK_ASSETS[name], check.asset_hashes("full")[name])
        self.assertEqual(len(check.asset_hashes("full")[name]), 64)

    def test_binary_manifest_decodes_utf8_utf16_and_typed_boolean(self):
        strings = ["manifest", "package", "com.catsmoker.app", check.ANDROID[1:-1], "debuggable"]
        for utf8 in (True, False):
            with self.subTest(utf8=utf8):
                values = bytearray()
                offsets = []
                for value in strings:
                    offsets.append(len(values))
                    encoded = value.encode("utf-8" if utf8 else "utf-16le")
                    values.extend((bytes([len(value), len(encoded)]) if utf8 else struct.pack("<H", len(value)))
                                  + encoded + (b"\0" if utf8 else b"\0\0"))
                values.extend(b"\0" * (-len(values) % 4))
                start = 28 + len(strings) * 4
                pool = struct.pack("<HHIIIIII", 1, 28, start + len(values), len(strings), 0,
                                   0x100 if utf8 else 0, start, 0)
                pool += struct.pack("<" + "I" * len(offsets), *offsets) + values
                node = struct.pack("<HHIII", 0x102, 16, 76, 0, 0xffffffff)
                node += struct.pack("<IIHHHHHH", 0xffffffff, 0, 20, 20, 2, 0, 0, 0)
                node += struct.pack("<IIIHBBI", 0xffffffff, 1, 2, 8, 0, 3, 2)
                node += struct.pack("<IIIHBBI", 3, 4, 0xffffffff, 8, 0, 0x12, 0)
                end = struct.pack("<HHIIIII", 0x103, 16, 24, 0, 0xffffffff, 0xffffffff, 0)
                data = struct.pack("<HHI", 3, 8, 8 + len(pool) + len(node) + len(end)) + pool + node + end
                root = inspect.binary_manifest(data)
                self.assertEqual(root.get("package"), "com.catsmoker.app")
                self.assertEqual(root.get(check.ANDROID + "debuggable"), "false")

    def test_binary_manifest_rejects_zero_length_chunk(self):
        with self.assertRaisesRegex(ValueError, "Malformed XML chunk"):
            inspect.binary_manifest(struct.pack("<HHIHHI", 3, 8, 16, 0, 0, 0))

    def test_native_exports_support_both_elf_classes(self):
        for wide in (False, True):
            with self.subTest(wide=wide):
                data = bytearray(640)
                data[:6] = b"\x7fELF" + bytes([2 if wide else 1, 1])
                size, stride, entry = (8, 64, 24) if wide else (4, 40, 16)
                def put(at, value, width):
                    data[at:at + width] = value.to_bytes(width, "little")
                put(40 if wide else 32, 128, size)
                put(58 if wide else 46, stride, 2)
                put(60 if wide else 48, 3, 2)
                strings, symbols = 128 + stride, 128 + 2 * stride
                put(strings + (24 if wide else 16), 400, size)
                put(symbols + 4, 11, 4)
                put(symbols + (24 if wide else 16), 512, size)
                put(symbols + (32 if wide else 20), entry * 2, size)
                put(symbols + (40 if wide else 24), 1, 4)
                put(symbols + (56 if wide else 36), entry, size)
                data[400:413] = b"\0native_init\0"
                put(512 + entry, 1, 4)
                put(512 + entry + (6 if wide else 14), 1, 2)
                self.assertEqual({"native_init"}, check.elf_exports(bytes(data)))

    def test_mismatched_mapping_cannot_certify_another_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "app.aab"
            mapping = Path(directory) / "mapping.txt"
            with ZipFile(bundle, "w") as zipped:
                zipped.writestr("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map", "fresh")
            mapping.write_text("stale")
            with self.assertRaisesRegex(ValueError, "Stale/mismatched"):
                check.matching_mapping(bundle, mapping)

    def test_mismatched_mapping_cannot_certify_an_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            mapping = Path(directory) / "mapping.txt"
            with ZipFile(apk, "w") as zipped:
                zipped.writestr("classes.dex", b"dex\n035\0" + b"\0" * 104)
            mapping.write_text("# pg_map_id: stale\n")
            with self.assertRaisesRegex(ValueError, "Stale/mismatched R8 mapping"):
                check.matching_mapping(apk, mapping)

    def test_reverse_resource_boundary_rejects_play_purchase_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "resources.txt"
            file.write_text("drawable/ic_cat_sad\nstring/gt_net_ds_title\nstring/sys_remove_ads_title\n")
            with self.assertRaisesRegex(ValueError, "Play-only resource"):
                check.resources(file, "full")

    def test_renamed_full_artwork_is_still_rejected_by_resource_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "resources.txt"
            file.write_text("resource 0x7f0800ab drawable/ic_cat_sad\n"
                            "  (nodpi) (file) res/9o.png type=PNG\nstring/gt_net_ds_title\n")
            with self.assertRaisesRegex(ValueError, "Full-only artwork"):
                check.resources(file)

    def test_full_artwork_cannot_be_removed_without_failing_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "resources.txt"
            file.write_text("string/gt_net_ds_title\n")
            with self.assertRaisesRegex(ValueError, "Full artwork resource missing"):
                check.resources(file, "full")

    def test_exported_source_directory_cannot_select_opposite_flavor(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "sources.txt"
            file.write_text("kotlin\tD:/app/src/full/java\n")
            with self.assertRaisesRegex(ValueError, "Opposite distribution source"):
                check.source_inputs(file, "playstore")

    def test_full_bridge_must_cover_every_expected_abi(self):
        payload = {"assets/" + name: text.encode() for name, text in check.FULL_INIT.items()}
        payload.update({f"lib/{abi}/libcatsmoker_bridge.so": b"" for abi in check.FULL_ABIS})
        check.full_native_contract(payload, payload.__getitem__, check.FULL_ABIS)
        del payload["lib/arm64-v8a/libcatsmoker_bridge.so"]
        with self.assertRaisesRegex(ValueError, "ABI coverage differs"):
            check.full_native_contract(payload, payload.__getitem__, check.FULL_ABIS)

    def test_full_loader_entry_point_cannot_be_silently_changed(self):
        for name in check.FULL_INIT:
            with self.subTest(name=name):
                payload = {"assets/" + key: value.encode() for key, value in check.FULL_INIT.items()}
                payload["lib/arm64-v8a/libcatsmoker_bridge.so"] = b""
                payload["assets/" + name] = b"wrong.entry.point"
                with self.assertRaisesRegex(ValueError, "wrong entry point"):
                    check.full_native_contract(payload, payload.__getitem__)

    def test_full_metadata_defines_universal_and_single_abi_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "output-metadata.json"
            rows = [{"type": "UNIVERSAL", "filters": [], "outputFile": "universal.apk"}]
            rows += [{"type": "ONE_OF_MANY", "filters": [{"filterType": "ABI", "value": abi}],
                      "outputFile": abi + ".apk"} for abi in sorted(check.FULL_ABIS)]
            for row in rows:
                row.update(versionCode=13, versionName="2.0.7")
            file.write_text(json.dumps({"applicationId": "com.catsmoker.app", "variantName": "fullRelease", "elements": rows}))
            root = check.ET.Element("manifest", {check.ANDROID + "versionCode": "13", check.ANDROID + "versionName": "2.0.7"})
            self.assertEqual(check.FULL_ABIS, inspect.full_output_abis(file, Path("universal.apk"), root))
            self.assertEqual({"arm64-v8a"}, inspect.full_output_abis(file, Path("arm64-v8a.apk"), root))
            root.set(check.ANDROID + "versionCode", "12")
            with self.assertRaisesRegex(ValueError, "version differs"):
                inspect.full_output_abis(file, Path("universal.apk"), root)

    def test_complete_apk_set_inventory_uses_toc_not_filenames(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "app.apks"
            paths = ["splits/base-master.apk", "splits/base-arm64_v8a.apk"]
            with ZipFile(file, "w") as zipped:
                zipped.writestr("toc.pb", apk_toc(paths))
                for path in paths:
                    zipped.writestr(path, b"fixture")
            with ZipFile(file) as zipped:
                groups, modes = inspect.apk_set_inventory(zipped)
            self.assertEqual([paths], groups)
            self.assertEqual({"split"}, modes)

    def test_deleted_configuration_apk_is_not_a_complete_set(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "app.apks"
            with ZipFile(file, "w") as zipped:
                zipped.writestr("toc.pb", apk_toc(["base.apk", "config.apk"]))
                zipped.writestr("base.apk", b"fixture")
            with ZipFile(file) as zipped, self.assertRaisesRegex(ValueError, "complete table of contents"):
                inspect.apk_set_inventory(zipped)

    def test_unlisted_apk_is_not_a_complete_set(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "app.apks"
            with ZipFile(file, "w") as zipped:
                zipped.writestr("toc.pb", apk_toc(["base.apk"]))
                zipped.writestr("base.apk", b"fixture")
                zipped.writestr("unlisted.apk", b"fixture")
            with ZipFile(file) as zipped, self.assertRaisesRegex(ValueError, "complete table of contents"):
                inspect.apk_set_inventory(zipped)

    def test_wrong_apk_set_package_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "app.apks"
            with ZipFile(file, "w") as zipped:
                zipped.writestr("toc.pb", apk_toc(["base.apk"], package=b"other.app"))
                zipped.writestr("base.apk", b"fixture")
            with ZipFile(file) as zipped, self.assertRaisesRegex(ValueError, "application identity"):
                inspect.apk_set_inventory(zipped)

    def test_apk_set_path_cannot_escape_inspection_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "app.apks"
            with ZipFile(file, "w") as zipped:
                zipped.writestr("toc.pb", apk_toc(["../outside.apk"]))
                zipped.writestr("../outside.apk", b"fixture")
            with ZipFile(file) as zipped, self.assertRaisesRegex(ValueError, "Unsafe APK-set path"):
                inspect.apk_set_inventory(zipped)

    def test_truncated_apk_set_protobuf_is_rejected(self):
        for data in (b"\x0a\x80", b"\x0a\x05x", b"\x00"):
            with self.subTest(data=data), self.assertRaises(ValueError):
                inspect.proto_fields(data)

    def test_derived_variant_cannot_omit_a_bundle_native_payload(self):
        groups = [["base.apk", "abi.apk"]]
        by_path = {"base.apk": ({"classes.dex"}, True), "abi.apk": (set(), False)}
        with self.assertRaisesRegex(ValueError, "omits bundle"):
            inspect.derived_variant_coverage(groups, by_path, {"classes.dex", "lib/arm64-v8a/sdk.so"}, set())

    def test_each_derived_variant_needs_its_own_code_base(self):
        groups = [["base.apk"], ["config.apk"]]
        by_path = {"base.apk": ({"classes.dex"}, True), "config.apk": (set(), False)}
        with self.assertRaisesRegex(ValueError, "unique code-bearing base"):
            inspect.derived_variant_coverage(groups, by_path, {"classes.dex"}, set())

    def test_derived_configuration_apk_must_match_bundle_version(self):
        expected = check.ET.Element("manifest", {check.ANDROID + "versionCode": "13", check.ANDROID + "versionName": "2.0.7"})
        config = check.ET.Element("manifest", {check.ANDROID + "versionCode": "13"})
        inspect.derived_version(config, expected, has_code=False)
        config.set(check.ANDROID + "versionCode", "12")
        with self.assertRaisesRegex(ValueError, "versionCode differs"):
            inspect.derived_version(config, expected, has_code=False)


if __name__ == "__main__":
    unittest.main()
