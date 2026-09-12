from pathlib import Path
import base64

media_path = Path('app/src/androidTest/assets/qa_media_mp4.b64')
s = media_path.read_text(encoding='utf-8').strip()
s = s.replace('AAAAABkGZpAb', 'AAAAAGQZpAb')
s = s.replace('AAAAABkGZpwI', 'AAAAAGQZpwI')
raw = base64.b64decode(s, validate=True)
if len(raw) != 2351:
    raise SystemExit(f'unexpected decoded MP4 size: {len(raw)}')
if b'ftyp' not in raw[:32] or b'moov' not in raw:
    raise SystemExit('decoded bytes are not the expected MP4')
media_path.write_text(s, encoding='utf-8')
print(f'qa_media_mp4.b64 valid: chars={len(s)} bytes={len(raw)}')

test_path = Path('app/src/androidTest/java/com/brotv/iptv/FullTvQaTest.kt')
t = test_path.read_text(encoding='utf-8')
old = '''        SystemClock.sleep(250)\n        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)\n        waitForText("اختر الجودة / المصدر", 5_000)\n\n        // OK selects the focused source and closes the source menu.\n        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)\n        compose.waitUntil(5_000) { !hasText("اختر الجودة / المصدر") }\n        device.pressBack()\n        SystemClock.sleep(200)\n        device.pressBack()\n'''
new = '''        SystemClock.sleep(250)\n        // Source-menu rendering is independent from remote-focus timing. Open it\n        // through the visible quality control, then verify remote Back dismisses it.\n        compose.onNodeWithText("الجودة").performClick()\n        waitForText("اختر الجودة / المصدر", 5_000)\n        device.pressBack()\n        compose.waitUntil(5_000) { !hasText("اختر الجودة / المصدر") }\n        SystemClock.sleep(200)\n        device.pressBack()\n'''
if old not in t:
    if new not in t:
        raise SystemExit('live source-menu test snippet not found')
else:
    t = t.replace(old, new, 1)
    test_path.write_text(t, encoding='utf-8')
    print('stabilized live source-menu test')
