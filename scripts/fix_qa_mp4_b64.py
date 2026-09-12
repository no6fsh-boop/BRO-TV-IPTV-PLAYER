from pathlib import Path
import base64

path = Path('app/src/androidTest/assets/qa_media_mp4.b64')
s = path.read_text(encoding='utf-8').strip()
s = s.replace('AAAAABkGZpAb', 'AAAAAGQZpAb')
s = s.replace('AAAAABkGZpwI', 'AAAAAGQZpwI')
raw = base64.b64decode(s, validate=True)
if len(raw) != 2351:
    raise SystemExit(f'unexpected decoded MP4 size: {len(raw)}')
if b'ftyp' not in raw[:32] or b'moov' not in raw:
    raise SystemExit('decoded bytes are not the expected MP4')
path.write_text(s, encoding='utf-8')
print(f'qa_media_mp4.b64 repaired: chars={len(s)} bytes={len(raw)}')
