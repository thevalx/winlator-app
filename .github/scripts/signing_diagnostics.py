"""Report public signing metadata only; never upload keystores or credentials."""
from pathlib import Path
import hashlib
import io
import json
import os
import subprocess
import zipfile

lines = ["Public signing diagnostics", "cache-hit=" + os.environ.get("SIGNING_CACHE_HIT", "unknown")]
candidates = {Path.home() / ".android/debug.keystore", Path.home() / ".config/.android/debug.keystore"}
for name in ("ANDROID_USER_HOME", "ANDROID_PREFS_ROOT", "ANDROID_SDK_HOME", "TEST_TMPDIR", "XDG_CONFIG_HOME", "HOME"):
    value = os.environ.get(name)
    lines.append(name + "=" + (value or "(unset)"))
    if value:
        candidates.add(Path(value) / "debug.keystore")
        candidates.add(Path(value) / ".android/debug.keystore")
for path in sorted(candidates):
    if not path.is_file():
        continue
    public = subprocess.run(["keytool", "-exportcert", "-keystore", str(path), "-storepass", "android", "-alias", "androiddebugkey"], capture_output=True)
    digest = hashlib.sha256(public.stdout).hexdigest() if public.returncode == 0 else "public-certificate-unavailable"
    lines.append("keystore public certificate: " + str(path) + " SHA256=" + digest)
repository = os.environ["GITHUB_REPOSITORY"]
metadata = subprocess.run(["gh", "api", "repos/" + repository + "/actions/caches", "--paginate"], capture_output=True)
if metadata.returncode == 0:
    try:
        for cache in json.loads(metadata.stdout).get("actions_caches", []):
            lines.append("cache=" + json.dumps({key: cache.get(key) for key in ("key", "ref", "version", "size_in_bytes", "created_at", "last_accessed_at")}))
    except (ValueError, TypeError):
        lines.append("Cache metadata could not be decoded")
for run in (39290184100, 39324355583):
    logs = subprocess.run(["gh", "api", "repos/" + repository + "/actions/runs/" + str(run) + "/logs"], capture_output=True)
    if logs.returncode != 0:
        lines.append("Run " + str(run) + " logs unavailable")
        continue
    with zipfile.ZipFile(io.BytesIO(logs.stdout)) as archive:
        for name in archive.namelist():
            if "signing key" not in name.lower():
                continue
            for line in archive.read(name).decode("utf-8", errors="replace").splitlines():
                if any(phrase in line for phrase in ("Cache not found", "Cache restored", "Cache saved", "Cache Size", "Path Validation Error", "cache key")):
                    lines.append(str(run) + " " + name + ": " + line)
Path("SIGNING-DIAGNOSTICS.txt").write_text("\n".join(lines) + "\n")
