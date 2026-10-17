"""Release mirroring contract against a disposable GitHub service model."""

import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import sync_module_repo as sync


APK = b"original signed APK bytes\x00\xff"
DIGEST = "sha256:" + hashlib.sha256(APK).hexdigest()
TAG = "7-1.6"
NAME = "oplus-keymap-anywhere.apk"


class GitHub:
    def __init__(self):
        self.source = {"tag_name": TAG, "draft": False, "prerelease": False,
                       "body": "中文更新说明\n", "assets": [self.asset(1, DIGEST)]}
        self.releases = []
        self.binaries = {}

    @staticmethod
    def asset(identifier, digest, state="uploaded"):
        return {"id": identifier, "name": NAME, "digest": digest, "state": state}

    def run(self, command, **options):
        if command[:2] == ["gh", "api"]:
            path = command[2]
            method = command[command.index("--method") + 1]
            data = json.loads(options["input"]) if options.get("input") else None
            if path.startswith(f"repos/{sync.SOURCE}/releases/"):
                response = self.source
            elif path == f"repos/{sync.TARGET}/releases" and method == "POST":
                response = {**data, "id": len(self.releases) + 10, "assets": []}
                self.releases.append(response)
            elif "?per_page=" in path:
                response = self.releases
            elif "/releases/tags/" in path:
                # GitHub's REST tag endpoint cannot see drafts.
                response = next((item for item in self.releases
                                 if item["tag_name"] == TAG and not item["draft"]), None)
                if response is None:
                    return subprocess.CompletedProcess(command, 1, '{"status":"404"}', "not found")
            elif "/releases/assets/" in path and method == "DELETE":
                identifier = int(path.rsplit("/", 1)[1])
                for release in self.releases:
                    release["assets"] = [asset for asset in release["assets"]
                                         if asset["id"] != identifier]
                self.binaries.pop(identifier, None)
                response = None
            elif "/releases/" in path:
                identifier = int(path.rsplit("/", 1)[1])
                response = next(item for item in self.releases if item["id"] == identifier)
                if method == "PATCH":
                    if data.get("draft") is False:
                        assert response["assets"]
                        assert all(self.binaries[item["id"]] == APK for item in response["assets"])
                    response.update(data)
            else:
                raise AssertionError(f"Unexpected API route: {path}")
            return subprocess.CompletedProcess(command, 0, json.dumps(response), "")

        if command[:3] == ["gh", "release", "download"]:
            (Path(command[command.index("--dir") + 1]) / NAME).write_bytes(APK)
        elif command[:3] == ["gh", "release", "upload"]:
            release = next(item for item in self.releases if item["tag_name"] == command[3])
            data = Path(command[4]).read_bytes()
            identifier = max(self.binaries, default=100) + 1
            release["assets"].append(self.asset(identifier, "sha256:" + hashlib.sha256(data).hexdigest()))
            self.binaries[identifier] = data
        else:
            raise AssertionError(f"Unexpected command: {command}")
        return subprocess.CompletedProcess(command, 0)


class ReleaseSyncTest(unittest.TestCase):
    def setUp(self):
        self.github = GitHub()
        self.patcher = patch.object(sync.subprocess, "run", side_effect=self.github.run)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def mirror(self):
        scratch = Path(__file__).resolve().parents[2] / ".amp" / "in"
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            sync.sync_release(TAG, Path(directory))

    def test_publish_original_apk_and_repeat_without_changes(self):
        self.mirror()
        release = self.github.releases[0]
        self.assertEqual(len(self.github.releases), 1)
        self.assertFalse(release["draft"])
        self.assertEqual((release["name"], release["tag_name"], release["body"]),
                         ("1.6", TAG, "中文更新说明\n"))
        self.assertEqual(list(self.github.binaries.values()), [APK])
        saved = copy.deepcopy(release)
        self.mirror()
        self.assertEqual(self.github.releases, [saved])
        self.assertEqual(list(self.github.binaries.values()), [APK])
        self.github.source["body"] = "更正后的中文说明\n"
        self.mirror()
        self.assertEqual(self.github.releases[0]["body"], "更正后的中文说明\n")
        self.assertEqual(self.github.releases[0]["assets"], saved["assets"])
        self.assertEqual(list(self.github.binaries.values()), [APK])

    def test_resume_draft_after_interrupted_asset_upload(self):
        draft = {"id": 42, "tag_name": TAG, "draft": True, "prerelease": False,
                 "name": "1.6", "body": "", "assets": [GitHub.asset(51, None, "starter")]}
        self.github.releases.append(draft)
        self.mirror()
        self.assertEqual(len(self.github.releases), 1)
        self.assertEqual(self.github.releases[0]["id"], 42)
        self.assertFalse(self.github.releases[0]["draft"])
        self.assertEqual(list(self.github.binaries.values()), [APK])

    def test_refuse_to_replace_a_published_apk(self):
        release = {"id": 42, "tag_name": TAG, "draft": False, "prerelease": False,
                   "name": "1.6", "body": "保留已发布内容", "assets": [GitHub.asset(51, "sha256:different")]}
        self.github.releases.append(release)
        self.github.binaries[51] = b"another APK"
        saved = copy.deepcopy(release)
        with self.assertRaisesRegex(RuntimeError, "不会覆盖"):
            self.mirror()
        self.assertEqual(self.github.releases, [saved])
        self.assertEqual(self.github.binaries, {51: b"another APK"})


if __name__ == "__main__":
    unittest.main()
