#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
python3 - <<'PY'
from pathlib import Path
import base64, gzip, glob, re

def clean_decode(paths, out):
    s=''.join(Path(p).read_text() for p in paths)
    s=''.join(re.findall(r'[A-Za-z0-9+/=]', s))
    raw=base64.b64decode(s, validate=False)
    data=gzip.decompress(raw)
    Path(out).write_bytes(data)
    print(out, len(data))

clean_decode(sorted(glob.glob('.github/redesign/part*.b64')), '/tmp/brotvplus-redesign.patch')
for n in range(1,6):
    clean_decode([f'.github/redesign/fix{n}.b64'], f'/tmp/brotvplus-redesign-fix{n}.patch')
clean_decode(sorted(glob.glob('.github/redesign/fix6-part*.b64')), '/tmp/brotvplus-redesign-fix6.patch')
PY
cd "$PROJECT_DIR"
patch -p1 < /tmp/brotvplus-redesign.patch
patch -p1 < /tmp/brotvplus-redesign-fix1.patch
patch -p1 < /tmp/brotvplus-redesign-fix2.patch
patch -p1 < /tmp/brotvplus-redesign-fix3.patch
patch -p1 < /tmp/brotvplus-redesign-fix4.patch
patch -p1 < /tmp/brotvplus-redesign-fix5.patch
patch -p1 < /tmp/brotvplus-redesign-fix6.patch

cd "$GITHUB_WORKSPACE"
chmod +x .github/scripts/apply-brotv-fix7.sh .github/scripts/apply-brotv-fix8.sh
.github/scripts/apply-brotv-fix7.sh
.github/scripts/apply-brotv-fix8.sh

echo 'BRO PLUS TV redesign patch + fix1 + fix2 + fix3 + fix4 + fix5 + fix6 + fix7 + fix8 applied.'