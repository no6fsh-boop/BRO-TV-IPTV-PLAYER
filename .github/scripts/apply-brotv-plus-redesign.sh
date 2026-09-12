#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
cat .github/redesign/part*.b64 | base64 -d | gunzip > /tmp/brotvplus-redesign.patch
base64 -d .github/redesign/fix1.b64 | gunzip > /tmp/brotvplus-redesign-fix1.patch
base64 -d .github/redesign/fix2.b64 | gunzip > /tmp/brotvplus-redesign-fix2.patch
cd "$PROJECT_DIR"
patch -p1 < /tmp/brotvplus-redesign.patch
patch -p1 < /tmp/brotvplus-redesign-fix1.patch
patch -p1 < /tmp/brotvplus-redesign-fix2.patch

echo 'BRO PLUS TV redesign patch + fix1 + fix2 applied.'
