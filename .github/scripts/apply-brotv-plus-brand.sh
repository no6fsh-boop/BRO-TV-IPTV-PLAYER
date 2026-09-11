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

# Force the legacy drawable name used by the Compose UI to render the approved BRO PLUS TV logo.
cat > "$RES/drawable/brotv_logo_gold.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/brotv_plus_logo" />
</layer-list>
XML

# Keep the legacy TV banner drawable name aligned too, in case any TV surface references it directly.
cat > "$RES/drawable/tv_banner.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/brotv_plus_banner" />
</layer-list>
XML

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

# Rename visible brand text everywhere without touching package ids.
for p in (root/'app/src/main').rglob('*'):
    if p.suffix.lower() not in {'.kt','.xml','.java'}: continue
    try: t=p.read_text()
    except: continue
    nt=t.replace('BROTV+','BRO PLUS TV').replace('BROTV +','BRO PLUS TV')
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

grep -q '@drawable/brotv_plus_logo' "$RES/drawable/brotv_logo_gold.xml"
grep -q '@drawable/brotv_plus_banner' "$RES/drawable/tv_banner.xml"

echo '=== BRAND FILES ==='
find "$RES" -type f | grep -Ei 'logo|brand|wordmark|banner|launcher|icon' | sort || true
echo '=== BRAND TEXT ==='
grep -RInE 'BROTV\+|BRO PLUS TV|BROTV' "$PROJECT_DIR/app/src/main" | head -200 || true
