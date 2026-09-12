#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
cat .github/redesign/part*.b64 | base64 -d | gunzip > /tmp/brotvplus-redesign.patch
cd "$PROJECT_DIR"
patch -p1 < /tmp/brotvplus-redesign.patch

echo 'BRO PLUS TV redesign patch applied.'
