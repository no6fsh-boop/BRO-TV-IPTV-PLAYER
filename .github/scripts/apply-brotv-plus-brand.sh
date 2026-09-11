#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="${PROJECT_DIR:-/tmp/project}"
RES="$PROJECT_DIR/app/src/main/res"
MANIFEST="$PROJECT_DIR/app/src/main/AndroidManifest.xml"
LOGO_B64="$GITHUB_WORKSPACE/.github/assets/brotv-plus/logo.b64"
mkdir -p "$RES/drawable-nodpi" "$RES/drawable"
base64 -d "$LOGO_B64" > /tmp/brotv-plus-logo.png
convert /tmp/brotv-plus-logo.png -resize 600x200 "$RES/drawable-nodpi/brotv_plus_logo.png"
convert -size 512x512 xc:'#05080e' /tmp/brotv-plus-logo.png -resize 440x147 -gravity center -composite "$RES/drawable-nodpi/brotv_plus_icon.png"
convert -size 640x360 xc:'#05080e' /tmp/brotv-plus-logo.png -resize 520x173 -gravity center -composite "$RES/drawable-nodpi/brotv_plus_banner.png"
cp "$RES/drawable-nodpi/brotv_plus_icon.png" /tmp/BRO-PLUS-TV-icon.png
cp "$RES/drawable-nodpi/brotv_plus_logo.png" /tmp/BRO-PLUS-TV-logo.png

python3 - <<'PY'
from pathlib import Path
import re, os
root=Path(os.environ.get('PROJECT_DIR','/tmp/project'))
manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text()
app=re.search(r'<application\b[^>]*>',s,re.S)
assert app, 'application tag not found'
tag=app.group(0)
for attr,val in [('android:icon','@drawable/brotv_plus_icon'),('android:banner','@drawable/brotv_plus_banner'),('android:label','BRO PLUS TV')]:
    if re.search(rf'\s{re.escape(attr)}="[^"]*"',tag):
        tag=re.sub(rf'(\s{re.escape(attr)}=")[^"]*(")',rf'\1{val}\2',tag)
    else:
        tag=tag[:-1]+f'\n        {attr}="{val}">'
s=s[:app.start()]+tag+s[app.end():]
manifest.write_text(s)

# Point Compose and XML callers directly at the PNG resource. Compose painterResource
# supports raster assets directly but rejects arbitrary layer-list drawable XML.
for p in (root/'app/src/main').rglob('*'):
    if p.suffix.lower() not in {'.kt','.xml','.java'}: continue
    try: t=p.read_text()
    except: continue
    nt=(t.replace('R.drawable.brotv_logo_gold','R.drawable.brotv_plus_logo')
          .replace('@drawable/brotv_logo_gold','@drawable/brotv_plus_logo')
          .replace('BROTV+','BRO PLUS TV')
          .replace('BROTV +','BRO PLUS TV'))
    if nt!=t: p.write_text(nt)
PY

# Replace likely legacy raster branding assets while preserving their file format.
while IFS= read -r -d '' f; do
  b="$(basename "$f" | tr '[:upper:]' '[:lower:]')"
  case "$b" in
    *banner*) src="$RES/drawable-nodpi/brotv_plus_banner.png" ;;
    *icon*|*launcher*) src="$RES/drawable-nodpi/brotv_plus_icon.png" ;;
    *) src="$RES/drawable-nodpi/brotv_plus_logo.png" ;;
  esac
  case "$f" in
    *brotv_plus_logo.png|*brotv_plus_icon.png|*brotv_plus_banner.png) continue ;;
  esac
  case "$b" in
    *logo*|*wordmark*|*brand*|*banner*|*launcher*) convert "$src" "$f" || true ;;
  esac
done < <(find "$RES" -type f \( -iname '*.png' -o -iname '*.webp' -o -iname '*.jpg' -o -iname '*.jpeg' \) -print0)

grep -Rqs 'R.drawable.brotv_plus_logo\|@drawable/brotv_plus_logo' "$PROJECT_DIR/app/src/main"
! grep -Rqs 'R.drawable.brotv_logo_gold\|@drawable/brotv_logo_gold' "$PROJECT_DIR/app/src/main" || { echo 'legacy logo reference remains'; exit 1; }

echo '=== BRAND FILES ==='
find "$RES" -type f | grep -Ei 'logo|brand|wordmark|banner|launcher|icon' | sort || true
echo '=== BRAND TEXT ==='
grep -RInE 'BROTV\+|BRO PLUS TV|BROTV' "$PROJECT_DIR/app/src/main" | head -200 || true
