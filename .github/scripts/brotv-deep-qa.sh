#!/usr/bin/env bash
set -u
mkdir -p /tmp/evidence/deep
RESULTS=/tmp/evidence/deep-qa-results.txt
: > "$RESULTS"
FAIL=0

pass(){ echo "PASS | $1 | $2" | tee -a "$RESULTS"; }
fail(){ echo "FAIL | $1 | $2" | tee -a "$RESULTS"; FAIL=1; }
info(){ echo "INFO | $1 | $2" | tee -a "$RESULTS"; }
app_alive(){ adb shell pidof com.brotv.iptv 2>/dev/null | grep -q '[0-9]'; }

wake(){
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
}

launch_route(){
  local route="$1"
  wake
  adb shell am force-stop com.brotv.iptv >/dev/null 2>&1 || true
  adb shell am start -W -n com.brotv.iptv/.MainActivity --ez BRO_DEMO true --es BRO_DEMO_ROUTE "$route" > "/tmp/evidence/deep/${route}-start.txt" 2>&1 || true
  for i in $(seq 1 40); do
    if adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | grep -q 'com.brotv.iptv'; then sleep 2; return 0; fi
    sleep 1
  done
  return 1
}

dump_ui(){
  local tag="$1"
  adb shell uiautomator dump "/sdcard/${tag}.xml" >/dev/null 2>&1 || true
  adb pull "/sdcard/${tag}.xml" "/tmp/evidence/deep/${tag}.xml" >/dev/null 2>&1 || true
  adb exec-out screencap -p > "/tmp/evidence/deep/${tag}.png" 2>/dev/null || true
}

prefs(){
  local tag="$1"
  adb exec-out run-as com.brotv.iptv sh -c 'for f in shared_prefs/*.xml; do echo "=== $f"; cat "$f"; echo; done' > "/tmp/evidence/deep/${tag}-prefs.txt" 2>/dev/null || true
}

# Tap the nearest clickable ancestor of an exact text/content-desc node.
tap_text(){
  local text="$1" tag="$2"
  dump_ui "$tag-find"
  local xy
  xy="$(python3 - "/tmp/evidence/deep/${tag}-find.xml" "$text" <<'PY'
import sys,re,xml.etree.ElementTree as ET
p,target=sys.argv[1:3]
try: root=ET.parse(p).getroot()
except Exception: raise SystemExit(2)
found=None
def walk(node, ancestors):
    global found
    if found: return
    a=node.attrib
    if a.get('text')==target or a.get('content-desc')==target:
        for cand in reversed(ancestors+[node]):
            if cand.attrib.get('clickable')=='true':
                found=cand; return
    for c in node: walk(c, ancestors+[node])
walk(root,[])
if found is None: raise SystemExit(3)
b=found.attrib.get('bounds','')
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b)
if not m: raise SystemExit(4)
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
)" || return 1
  adb shell input tap $xy >/dev/null 2>&1 || return 1
  sleep 1.5
  return 0
}

tap_prefix(){
  local prefix="$1" tag="$2"
  dump_ui "$tag-find"
  local xy
  xy="$(python3 - "/tmp/evidence/deep/${tag}-find.xml" "$prefix" <<'PY'
import sys,re,xml.etree.ElementTree as ET
p,prefix=sys.argv[1:3]
try: root=ET.parse(p).getroot()
except Exception: raise SystemExit(2)
found=None
def walk(node, ancestors):
    global found
    if found: return
    a=node.attrib; t=a.get('text',''); d=a.get('content-desc','')
    if t.startswith(prefix) or d.startswith(prefix):
        for cand in reversed(ancestors+[node]):
            if cand.attrib.get('clickable')=='true': found=cand; return
    for c in node: walk(c,ancestors+[node])
walk(root,[])
if found is None: raise SystemExit(3)
b=found.attrib.get('bounds',''); m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',b)
if not m: raise SystemExit(4)
x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
PY
)" || return 1
  adb shell input tap $xy >/dev/null 2>&1 || return 1
  sleep 2
  return 0
}

ui_has(){
  local tag="$1" pattern="$2"
  dump_ui "$tag"
  grep -Eq "$pattern" "/tmp/evidence/deep/${tag}.xml"
}

prefs_changed(){
  local a="$1" b="$2"
  ! cmp -s "/tmp/evidence/deep/${a}-prefs.txt" "/tmp/evidence/deep/${b}-prefs.txt"
}

# SETTINGS: deterministic functional checks using actual cards and private prefs in debug build.
if launch_route settings; then
  pass "الإعدادات - فتح الشاشة" "الشاشة فتحت ونافذة التطبيق استلمت التركيز"
else
  fail "الإعدادات - فتح الشاشة" "لم تحصل نافذة التطبيق على التركيز"
fi

prefs settings-base
if tap_text "الرقابة الأبوية" settings-parental-enable; then
  prefs settings-parental-on
  if prefs_changed settings-base settings-parental-on; then pass "الإعدادات - تفعيل الرقابة الأبوية" "تغيّرت حالة التخزين"; else fail "الإعدادات - تفعيل الرقابة الأبوية" "الزر استجاب لكن لم تتغير الإعدادات المخزنة"; fi
else fail "الإعدادات - تفعيل الرقابة الأبوية" "تعذر الوصول للزر"; fi

if tap_text "تعطيل الرقابة الأبوية" settings-parental-disable; then
  prefs settings-parental-off
  if prefs_changed settings-parental-on settings-parental-off; then pass "الإعدادات - تعطيل الرقابة الأبوية" "تغيّرت حالة التخزين"; else fail "الإعدادات - تعطيل الرقابة الأبوية" "لم تتغير الحالة بعد التعطيل"; fi
else fail "الإعدادات - تعطيل الرقابة الأبوية" "تعذر الوصول للزر"; fi

prefs settings-language-before
if tap_text "تغيير اللغة" settings-language; then
  sleep 1; dump_ui settings-language-open; prefs settings-language-after
  if prefs_changed settings-language-before settings-language-after || grep -Eq 'English|الإنجليزية|العربية' /tmp/evidence/deep/settings-language-open.xml; then
    pass "الإعدادات - تغيير اللغة" "ظهر اختيار لغة أو تغيّرت حالة اللغة"
  else
    fail "الإعدادات - تغيير اللغة" "لم يظهر اختيار لغة ولم تتغير حالة التخزين"
  fi
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true; sleep 1
else fail "الإعدادات - تغيير اللغة" "تعذر الوصول للزر"; fi

for item in "إخفاء فئات القنوات المباشرة" "إخفاء فئات الفيديو عند الطلب" "إخفاء فئات المسلسلات"; do
  launch_route settings >/dev/null 2>&1 || true
  dump_ui hide-before
  if tap_text "$item" hide-open; then
    dump_ui hide-after
    if ! cmp -s /tmp/evidence/deep/hide-before.xml /tmp/evidence/deep/hide-after.xml && app_alive; then pass "الإعدادات - $item" "فتح شاشة/حوار التحكم بالفئات"; else fail "الإعدادات - $item" "لم يتغير المحتوى بعد الضغط"; fi
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true; sleep 1
  else fail "الإعدادات - $item" "تعذر الوصول للزر"; fi
done

for spec in "تغيير التخطيط|layout" "تنسيق البث المباشر|live-layout" "تنسيق الوقت|time-format"; do
  item="${spec%%|*}"; tag="${spec##*|}"
  launch_route settings >/dev/null 2>&1 || true
  prefs "$tag-before"
  if tap_text "$item" "$tag"; then
    prefs "$tag-after"
    if prefs_changed "$tag-before" "$tag-after"; then pass "الإعدادات - $item" "تم تغيير الإعداد وتخزينه"; else fail "الإعدادات - $item" "لم تتغير قيمة الإعداد المخزنة"; fi
  else fail "الإعدادات - $item" "تعذر الوصول للزر"; fi
done

# Reach lower cards by D-pad; validates they are TV-remote reachable, not just touch-clickable.
launch_route settings >/dev/null 2>&1 || true
for i in $(seq 1 8); do adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null 2>&1 || true; sleep .25; done
dump_ui settings-lower-cards
for item in "مسح قنوات السجل" "مسح تاريخ الأفلام" "مسح تاريخ المسلسلات"; do
  if grep -q "$item" /tmp/evidence/deep/settings-lower-cards.xml; then
    if tap_text "$item" "clear-$(echo "$item" | md5sum | cut -c1-6)" && app_alive; then pass "الإعدادات - $item" "الزر قابل للوصول بالريموت ويعمل دون انهيار"; else fail "الإعدادات - $item" "ظهر الزر لكن فشل الضغط أو خرج التطبيق"; fi
  else
    fail "الإعدادات - $item" "لم يظهر بعد التنقل لأسفل بالريموت"
  fi
done

# HOME explicit refresh: must cause fresh provider requests, not simply navigate away.
launch_route home >/dev/null 2>&1 || true
before_req=$(grep -Ec 'get_live_streams|get_live_categories|get_vod_streams|get_series' /tmp/demo-server.log 2>/dev/null || true)
if tap_text "التحديث" home-refresh; then
  sleep 4
  after_req=$(grep -Ec 'get_live_streams|get_live_categories|get_vod_streams|get_series' /tmp/demo-server.log 2>/dev/null || true)
  dump_ui home-refresh-after
  if [ "$after_req" -gt "$before_req" ]; then pass "الرئيسية - التحديث" "أرسل طلب تحديث فعلي لمزود البيانات"; else fail "الرئيسية - التحديث" "لم يظهر طلب تحديث جديد لمزود البيانات"; fi
else fail "الرئيسية - التحديث" "تعذر الوصول لزر التحديث"; fi

# LIVE TV preview/fullscreen/overlay/quality.
if launch_route live_tv; then
  if tap_prefix "قناة تجريبية" live-first-channel; then
    dump_ui live-preview
    if app_alive; then pass "البث المباشر - تشغيل المعاينة" "اختيار قناة تجريبية أبقى التطبيق حياً وبدأ المعاينة"; else fail "البث المباشر - تشغيل المعاينة" "التطبيق خرج بعد اختيار القناة"; fi
    if tap_prefix "قناة تجريبية" live-second-channel; then
      sleep 2; dump_ui live-fullscreen
      if grep -Eq 'الجودة|القناة|البرنامج|مصدر' /tmp/evidence/deep/live-fullscreen.xml; then pass "البث المباشر - فتح ملء الشاشة" "ظهر Overlay الخاص بالمشغل"; else fail "البث المباشر - فتح ملء الشاشة" "لم أجد معلومات Overlay بعد الضغط الثاني على القناة"; fi
      if grep -q 'الجودة' /tmp/evidence/deep/live-fullscreen.xml && tap_text "الجودة" live-quality; then
        dump_ui live-quality-open
        if ! cmp -s /tmp/evidence/deep/live-fullscreen.xml /tmp/evidence/deep/live-quality-open.xml; then pass "البث المباشر - قائمة الجودة" "القائمة فتحت وتغيرت واجهة المشغل"; else fail "البث المباشر - قائمة الجودة" "لم تتغير الواجهة بعد اختيار الجودة"; fi
        adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null 2>&1 || true; adb shell input keyevent KEYCODE_ENTER >/dev/null 2>&1 || true; sleep 1
      else fail "البث المباشر - قائمة الجودة" "خيار الجودة غير ظاهر/غير قابل للفتح"; fi
      sleep 6; dump_ui live-overlay-hidden
      adb shell input keyevent KEYCODE_DPAD_UP >/dev/null 2>&1 || true; sleep 1; dump_ui live-overlay-return
      if grep -q 'الجودة' /tmp/evidence/deep/live-overlay-return.xml; then pass "البث المباشر - استرجاع معلومات المشغل" "زر أعلى أعاد Overlay"; else fail "البث المباشر - استرجاع معلومات المشغل" "Overlay لم يعد بزر أعلى"; fi
    else fail "البث المباشر - فتح ملء الشاشة" "تعذر تنفيذ الضغط الثاني على القناة"; fi
  else fail "البث المباشر - تشغيل المعاينة" "لم أجد قناة تجريبية قابلة للاختيار"; fi
else fail "البث المباشر - فتح الشاشة" "فشل تشغيل المسار"; fi

# SERIES: open a real demo series and episode, then verify requested control families are exposed.
if launch_route series; then
  if tap_prefix "مسلسل تجريبي" series-card; then
    sleep 2; dump_ui series-details
    if grep -Eq 'الموسم|الحلقة|مسلسل تجريبي' /tmp/evidence/deep/series-details.xml; then pass "المسلسلات - صفحة التفاصيل" "تم فتح تفاصيل مسلسل Demo"; else fail "المسلسلات - صفحة التفاصيل" "لم تظهر تفاصيل/حلقات"; fi
    if tap_prefix "الحلقة" series-episode; then
      sleep 3; adb shell input keyevent KEYCODE_DPAD_UP >/dev/null 2>&1 || true; sleep 1; dump_ui series-player-controls
      if grep -Eq '16:9|4:5|9:16|ترجم|التالي|السابق|15' /tmp/evidence/deep/series-player-controls.xml; then pass "مشغل المسلسلات - ظهور أدوات التحكم" "ظهرت عناصر من أدوات التحكم المطلوبة"; else fail "مشغل المسلسلات - ظهور أدوات التحكم" "لم تظهر تسميات أدوات التحكم المطلوبة في واجهة الوصول"; fi
      if app_alive; then pass "مشغل المسلسلات - الاستقرار" "المشغل بقي حياً بعد الدخول والتحكم"; else fail "مشغل المسلسلات - الاستقرار" "التطبيق خرج داخل المشغل"; fi
    else fail "المسلسلات - تشغيل حلقة" "لم أجد حلقة قابلة للاختيار"; fi
  else fail "المسلسلات - فتح مسلسل" "لم أجد مسلسل Demo قابلاً للاختيار"; fi
else fail "المسلسلات - فتح الشاشة" "فشل تشغيل المسار"; fi

# Final crash/ANR and performance evidence.
adb logcat -d -v time > /tmp/evidence/deep/deep-logcat.txt 2>/dev/null || true
if grep -E 'FATAL EXCEPTION|ANR in com\.brotv\.iptv' /tmp/evidence/deep/deep-logcat.txt > /tmp/evidence/deep/deep-crash-anr.txt; then
  fail "الاستقرار - Crash/ANR" "تم العثور على Crash أو ANR في الاختبارات التفصيلية"
else
  : > /tmp/evidence/deep/deep-crash-anr.txt
  pass "الاستقرار - Crash/ANR" "لا يوجد Crash أو ANR في سجل الاختبار التفصيلي"
fi
adb shell dumpsys gfxinfo com.brotv.iptv > /tmp/evidence/deep/deep-gfxinfo.txt 2>/dev/null || true
adb shell dumpsys meminfo com.brotv.iptv > /tmp/evidence/deep/deep-meminfo.txt 2>/dev/null || true

exit "$FAIL"
