import copy
from contextlib import redirect_stdout
import hashlib
from io import StringIO
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from zipfile import ZipFile

import ci_release as release
import restore_fuji_assets as restore
from verify_personal_fuji_apk import DIRECTORY, EXPECTED_FILES, REVIEWED_ZIP_SHA256, validate_assets


def assets():
    data = {name: b'LUT_3D_SIZE 33\n0 0 0\n' for name in EXPECTED_FILES}
    data['sources.json'] = json.dumps({'package_sha256': REVIEWED_ZIP_SHA256, 'files': [
        {'file': name, 'sha256': hashlib.sha256(content).hexdigest()} for name, content in data.items()
    ]}).encode()
    return data


def metadata():
    return {'versionName': '0.7.9', 'versionCode': 42, 'apkSize': 1234,
            'apkSha256': 'a' * 64, 'apkUrl': release.apk_url('0.7.9')}


def published(info, draft=False):
    return {'tag_name': 'v0.7.9', 'draft': draft, 'prerelease': False, 'assets': [
        {'name': release.filename('0.7.9'), 'state': 'uploaded', 'size': info['apkSize'],
         'digest': 'sha256:' + info['apkSha256'], 'browser_download_url': info['apkUrl']}]}


class FujiRestoreTest(unittest.TestCase):
    def test_missing_tampered_or_invalid_grid_is_rejected(self):
        valid = assets()
        validate_assets(valid)
        name = sorted(EXPECTED_FILES)[0]
        missing = dict(valid)
        del missing[name]
        bad = dict(valid, **{name: b'changed'})
        for source in [missing, bad]:
            with self.subTest(source=source is missing), self.assertRaises(ValueError):
                validate_assets(source)

    def test_checksum_is_checked_before_extracting(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'bad.apk'
            apk.write_bytes(b'not the baseline')
            destination = root / 'assets'
            with self.assertRaises(ValueError):
                restore.restore(apk, destination)
            self.assertFalse(destination.exists())

    def test_verified_pack_restores_without_overwriting_local_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'baseline.apk'
            with ZipFile(apk, 'w') as archive:
                for name, data in assets().items():
                    archive.writestr(DIRECTORY + name, data)
            with patch.object(restore, 'BASELINE_SHA256', hashlib.sha256(apk.read_bytes()).hexdigest()):
                destination = root / 'assets'
                restore.restore(apk, destination)
                self.assertEqual(11, len(list(destination.iterdir())))
                restore.restore(apk, destination)
                target = destination / sorted(EXPECTED_FILES)[0]
                target.write_bytes(b'user changes')
                with self.assertRaises(ValueError):
                    restore.restore(apk, destination)
                self.assertEqual(b'user changes', target.read_bytes())


class ReleaseValidationTest(unittest.TestCase):
    def test_version_requires_one_valid_pair(self):
        self.assertEqual(('0.7.9', 42), release.version('versionName = "0.7.9"\nversionCode = 42'))
        for text in ['versionName = "bad"\nversionCode = 42', 'versionName = "0.7.9"\nversionCode = 0',
                     'versionName = "0.7.9"\nversionName = "0.7.8"\nversionCode = 42']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.version(text)

    def test_one_uploaded_apk_with_exact_digest_size_and_url_is_required(self):
        info = metadata()
        valid = published(info)
        release.validate_asset(valid, info, True)
        cases = []
        for field, value in [('digest', 'sha256:' + 'b' * 64), ('size', 42), ('state', 'new'),
                             ('name', 'debug.apk'), ('browser_download_url', 'https://example.com/app.apk')]:
            bad = copy.deepcopy(valid)
            bad['assets'][0][field] = value
            cases.append(bad)
        duplicate = copy.deepcopy(valid)
        duplicate['assets'] *= 2
        cases.extend([duplicate, dict(valid, assets=[]), dict(valid, draft=True), dict(valid, prerelease=True)])
        for bad in cases:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                release.validate_asset(bad, info, True)

    def test_draft_upload_url_is_only_accepted_before_publication(self):
        info = metadata()
        draft = published(info, True)
        draft['assets'][0]['browser_download_url'] = 'https://github.com/waltwait/Grain/releases/download/untagged-x/app.apk'
        release.validate_asset(draft, info, False)
        draft['draft'] = False
        with self.assertRaises(ValueError):
            release.validate_asset(draft, info, True)

    def test_feed_cannot_downgrade_or_replace_an_existing_code(self):
        current = metadata()
        release.require_increasing(current, dict(current))
        release.require_increasing(current, dict(current, versionCode=41))
        for bad in [dict(current, versionCode=41), dict(current, apkSha256='b' * 64)]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                release.require_increasing(bad, current)

    def test_publication_is_not_available_outside_main_actions(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(ValueError):
            release.publish()

    def test_bad_asset_never_updates_the_feed(self):
        info = metadata()
        bad = published(info)
        bad['assets'][0]['digest'] = 'sha256:' + 'b' * 64
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'latest.json').write_text(json.dumps(info))
            (root / 'feed.json').write_text(json.dumps(dict(info, versionCode=41)))
            with patch.object(release, 'WORK', root), patch.object(release, 'ROOT', root), \
                 patch.object(release, 'FEED', Path('feed.json')), patch.object(release, 'release_for', return_value=bad), \
                 patch.object(release, 'update_feed') as update, patch.dict(os.environ, {
                    'GITHUB_ACTIONS': 'true', 'GITHUB_REPOSITORY': release.REPO, 'GITHUB_REF': 'refs/heads/main'}):
                with self.assertRaises(ValueError):
                    with redirect_stdout(StringIO()):
                        release.publish()
                update.assert_not_called()

    def test_success_and_recovery_update_feed_after_public_apk_is_verified(self):
        info = metadata()
        for resume in [False, True]:
            with self.subTest(resume=resume), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'latest.json').write_text(json.dumps(info))
                (root / 'feed.json').write_text(json.dumps(dict(info, versionCode=41)))
                responses = [published(info), published(info)] if resume else [None, published(info, True), published(info)]
                order = []
                request = MagicMock()
                request.__enter__.return_value.status = 200
                with patch.object(release, 'WORK', root), patch.object(release, 'ROOT', root), \
                     patch.object(release, 'FEED', Path('feed.json')), patch.object(release, 'release_for', side_effect=responses), \
                     patch.object(release, 'require_tag_source'), patch.object(release, 'api', return_value={'tag_name': 'v0.7.9'}), \
                     patch.object(release, 'run', side_effect=lambda args: order.append(args)), \
                     patch.object(release.urllib.request, 'urlopen', return_value=request) as head, \
                     patch.object(release, 'update_feed', side_effect=lambda value: self.assertTrue(head.called)), \
                     patch.dict(os.environ, {'GITHUB_ACTIONS': 'true', 'GITHUB_REPOSITORY': release.REPO,
                                            'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': 'source'}):
                    with redirect_stdout(StringIO()):
                        release.publish()
                if resume:
                    self.assertFalse(order)
                else:
                    self.assertEqual(['create', 'edit'], [args[2] for args in order if args[:2] == ['gh', 'release']])


if __name__ == '__main__':
    unittest.main()
