#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
echo decode-main
cat .github/redesign/part*.b64 | base64 -d | gunzip > /tmp/brotvplus-redesign.patch
echo decode-fix1
base64 -d .github/redesign/fix1.b64 | gunzip > /tmp/brotvplus-redesign-fix1.patch
echo decode-fix2
base64 -d .github/redesign/fix2.b64 | gunzip > /tmp/brotvplus-redesign-fix2.patch
echo decode-fix3
base64 -d .github/redesign/fix3.b64 | gunzip > /tmp/brotvplus-redesign-fix3.patch
echo decode-fix4
base64 -d .github/redesign/fix4.b64 | gunzip > /tmp/brotvplus-redesign-fix4.patch
echo decode-fix5
base64 -d .github/redesign/fix5.b64 | gunzip > /tmp/brotvplus-redesign-fix5.patch
echo decode-fix6
cat .github/redesign/fix6-part*.b64 | base64 -d | gunzip > /tmp/brotvplus-redesign-fix6.patch
cd "$PROJECT_DIR"
patch -p1 < /tmp/brotvplus-redesign.patch
patch -p1 < /tmp/brotvplus-redesign-fix1.patch
patch -p1 < /tmp/brotvplus-redesign-fix2.patch
patch -p1 < /tmp/brotvplus-redesign-fix3.patch
patch -p1 < /tmp/brotvplus-redesign-fix4.patch
patch -p1 < /tmp/brotvplus-redesign-fix5.patch
patch -p1 < /tmp/brotvplus-redesign-fix6.patch

echo 'BRO PLUS TV redesign patch + fix1 + fix2 + fix3 + fix4 + fix5 + fix6 applied.'
