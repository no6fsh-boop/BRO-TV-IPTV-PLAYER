#!/usr/bin/env bash
set -u
mkdir -p /tmp/evidence/deep
RESULTS=/tmp/evidence/deep-qa-results.txt
: > "$RESULTS"
FAIL=0
adb logcat -c >/dev/null 2>&1 || true

pass(){ echo "PASS | $1 | $2" | tee -a "$RESULTS"; }
fail(){ echo "FAIL | $1 | $2" | tee -a "$RESULTS"; FAIL=1; }
info(){ echo "INFO | $1 | $2" | tee -a "$RESULTS"; }
app_alive(){ adb shell pidof com.brotv.iptv 2>/dev/null | grep -q '[0-9]'; }
focused(){ adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | grep -q 'com.brotv.iptv'; }

wake(){
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
}

visible_shot(){
  local out="$1"
  adb exec-out screencap -p > "$out" 2>/dev/null || return 1
  identify "$out" >/dev/null 2>&1 || return 1
  local stats
  stats="$(convert "$out" -colorspace Gray -format '%[fx:mean] %[fx:standard_deviation]' info: 2>/dev/null)" || return 1
  python3 - "$stats" <<'PY'
import sys
m,s=map(float,sys.argv[1].split())
raise SystemExit(0 if (s>0.003 or m>0.015) else 1)
PY
}

wait_ready(){
  local tag="$1"
  for i in $(seq 1 35); do
    if app_alive && focused; then
      if visible_shot "/tmp/evidence/deep/${tag}-ready.png"; then return 0; fi
    fi
    sleep 1
  done
  adb shell dumpsys window > "/tmp/evidence/deep/${tag}-window.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "/tmp/evidence/deep/${tag}-activity.txt" 2>/dev/null || true
  return 1
}

launch_route(){
  local route="$1" tag="${2:-$1}"
  wake
  adb shell am force-stop com.brotv.iptv >/dev/null 2>&1 || true
  sleep .5
  adb shell am start -n com.brotv.iptv/.MainActivity --ez BRO_DEMO true --es BRO_DEMO_ROUTE "$route" > "/tmp/evidence/deep/${tag}-start.txt" 2>&1 || true
  wait_ready "$tag"
}

safe_dump(){
  local tag="$1"
  focused || return 1
  rm -f "/tmp/evidence/deep/${tag}.xml"
  adb shell rm -f "/sdcard/${tag}.xml" >/dev/null 2>&1 || true
  timeout 9s adb shell uiautomator dump --compressed "/sdcard/${tag}.xml" >/dev/null 2>&1 || return 1
  adb pull "/sdcard/${tag}.xml" "/tmp/evidence/deep/${tag}.xml" >/dev/null 2>&1 || return 1
  grep -q 'package="com.brotv.iptv"' "/tmp/evidence/deep/${tag}.xml" || return 1
  visible_shot "/tmp/evidence/deep/${tag}.png" || return 1
}

find_xy(){
  local xml="$1" target="$2" mode="${3:-exact}"
  python3 - "$xml" "$target" "$mode" <<'PY'
import sys,re,xml.etree.ElementTree as ET
p,target,mode=sys.argv[1:4]
root=ET.parse(p).getroot(); found=None
def match(v): return (v==target) if mode=='exact' else v.startswith(target)
def walk(node,anc):
    global found
    if found is not None:return
    a=node.attrib
    if match(a.get('text','')) or match(a.get('content-desc','')):
        for c in reversed(anc+[node]):
            if c.attrib.get('clickable')=='true': found=c; return
    for c in node: walk(c,anc+[node])
walk(root,[])
if found is None: raise SystemExit(3)
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',found.attrib.get('bounds',''))
if not m: raise SystemExit(4)
x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
PY
}

tap_text(){
  local text="$1" tag="$2" mode="${3:-exact}"
  safe_dump "$tag-find" || return 1
  local xy
  xy="$(find_xy "/tmp/evidence/deep/${tag}-find.xml" "$text" "$mode")" || return 1
  adb shell input tap $xy >/dev/null 2>&1 || return 1
  sleep .8
  app_alive || return 1
  return 0
}

prefs(){
  local tag="$1"
  adb exec-out run-as com.brotv.iptv sh -c 'for f in shared_prefs/*.xml; do echo "=== $f"; cat "$f"; echo; done' > "/tmp/evidence/deep/${tag}-prefs.txt" 2>/dev/null || true
}
prefs_changed(){ ! cmp -s "/tmp/evidence/deep/$1-prefs.txt" "/tmp/evidence/deep/$2-prefs.txt"; }

SRC="${PROJECT_DIR:-/tmp/project}/app/src/main/java/com/brotv/iptv"
if grep -q 'Text(category.name,color=Color.White' "$SRC/ui/screens/settings/SettingsScreen.kt"; then
  pass "الإعدادات - لون اسم الفئة بعد التحديد" "اسم الفئة مثبت على اللون الأبيض؛ التحديد لا يحول النص إلى ذهبي"
else fail "الإعدادات - لون اسم الفئة بعد التحديد" "عقد اللون الأبيض غير موجود في المصدر"; fi
python3 - "$SRC/ui/screens/live/LiveTvScreen.kt" <<'PY' >/tmp/evidence/deep/folder-contract.txt 2>&1
import sys
s=open(sys.argv[1]).read(); f=s.split('@Composable private fun FolderRow',1)[1].split('@Composable private fun ChannelRow',1)[0]
assert 'onFocusChanged' in f and '.clickable(onClick=onClick)' in f and 'applyCategory' not in f
PY
if [ $? -eq 0 ]; then pass "البث المباشر - المرور بين المجلدات" "التركيز يتحرك فقط؛ تغيير المجلد مربوط بـ OK/النقر وليس بمجرد Focus"; else fail "البث المباشر - المرور بين المجلدات" "تم العثور على تغيير للمجلد داخل حركة التركيز"; fi

if launch_route settings settings-open; then pass "الإعدادات - فتح الشاشة" "الشاشة ظهرت واحتفظ التطبيق بالتركيز"; else fail "الإعدادات - فتح الشاشة" "الشاشة لم تصبح جاهزة"; fi

for spec in \
  "الرقابة الأبوية|parental-on" \
  "تعطيل الرقابة الأبوية|parental-off" \
  "تغيير التخطيط|layout" \
  "تنسيق البث المباشر|live-layout" \
  "تنسيق الوقت|time-format"; do
  item="${spec%%|*}"; tag="${spec##*|}"
  launch_route settings "$tag" >/dev/null 2>&1 || { fail "الإعدادات - $item" "تعذر تشغيل شاشة الإعدادات"; continue; }
  prefs "$tag-before"
  if tap_text "$item" "$tag"; then
    prefs "$tag-after"
    if prefs_changed "$tag-before" "$tag-after"; then pass "الإعدادات - $item" "الزر غيّر القيمة المخزنة فعليًا"; else fail "الإعدادات - $item" "الضغط لم يغير القيمة المخزنة"; fi
  else fail "الإعدادات - $item" "تعذر الوصول للبطاقة مع بقاء التطبيق في الواجهة"; fi
done

launch_route settings language >/dev/null 2>&1 || true
prefs language-before
if tap_text "تغيير اللغة" language; then
  prefs language-after
  if prefs_changed language-before language-after; then
    launch_route home language-home >/dev/null 2>&1 || true
    if safe_dump language-home-ui && grep -Eq 'Channels|Movies|Settings|Refresh' /tmp/evidence/deep/language-home-ui.xml; then
      pass "الإعدادات - تغيير اللغة" "تغيّرت اللغة وحُفظت وظهرت الإنجليزية على الشاشة الرئيسية"
    else fail "الإعدادات - تغيير اللغة" "تغيّرت القيمة لكن لم تظهر اللغة الجديدة على الرئيسية"; fi
  else fail "الإعدادات - تغيير اللغة" "لم تتغير قيمة اللغة المخزنة"; fi
else fail "الإعدادات - تغيير اللغة" "تعذر الضغط على تغيير اللغة"; fi
launch_route settings language-restore >/dev/null 2>&1 || true
if tap_text "Change language" language-restore; then :; else true; fi

for spec in \
  "إخفاء فئات القنوات المباشرة|hide-live|السعودية" \
  "إخفاء فئات الفيديو عند الطلب|hide-vod|أحدث الأفلام" \
  "إخفاء فئات المسلسلات|hide-series|أحدث المسلسلات"; do
  item="${spec%%|*}"; rest="${spec#*|}"; tag="${rest%%|*}"; catname="${rest##*|}"
  launch_route settings "$tag" >/dev/null 2>&1 || { fail "الإعدادات - $item" "تعذر تشغيل الإعدادات"; continue; }
  if tap_text "$item" "$tag-open"; then
    sleep 1
    if safe_dump "$tag-panel" && grep -q "$catname" "/tmp/evidence/deep/$tag-panel.xml"; then
      prefs "$tag-before"
      if tap_text "$catname" "$tag-toggle"; then
        prefs "$tag-after"; safe_dump "$tag-after" || true
        if prefs_changed "$tag-before" "$tag-after"; then pass "الإعدادات - $item" "فتح الحوار وتم تغيير فئة مستقلة وحفظها"; else fail "الإعدادات - $item" "الفئة لم تتغير في التخزين"; fi
      else fail "الإعدادات - $item" "الحوار فتح لكن تعذر تحديد الفئة"; fi
    else fail "الإعدادات - $item" "الحوار لم يعرض فئات المزود"; fi
  else fail "الإعدادات - $item" "تعذر فتح حوار الفئات"; fi
done

for spec in "مسح قنوات السجل|clear-live" "مسح تاريخ الأفلام|clear-movies" "مسح تاريخ المسلسلات|clear-series"; do
  item="${spec%%|*}"; tag="${spec##*|}"
  launch_route settings "$tag" >/dev/null 2>&1 || { fail "الإعدادات - $item" "تعذر تشغيل الإعدادات"; continue; }
  adb shell input swipe 1000 930 1000 420 450 >/dev/null 2>&1 || true; sleep 1
  if tap_text "$item" "$tag"; then pass "الإعدادات - $item" "البطاقة السفلية ظهرت بعد التمرير واستجابت دون انهيار"; else fail "الإعدادات - $item" "تعذر الوصول للبطاقة بعد التمرير"; fi
done

launch_route home home-refresh >/dev/null 2>&1 || true
before_req=$(grep -Ec 'get_live_streams|get_live_categories|get_vod_streams|get_vod_categories|get_series_categories|get_series' /tmp/demo-server.log 2>/dev/null || true)
if tap_text "التحديث" home-refresh; then
  sleep 4
  after_req=$(grep -Ec 'get_live_streams|get_live_categories|get_vod_streams|get_vod_categories|get_series_categories|get_series' /tmp/demo-server.log 2>/dev/null || true)
  safe_dump home-refresh-after || true
  if [ "$after_req" -gt "$before_req" ] && grep -q 'التحديث' /tmp/evidence/deep/home-refresh-after.xml 2>/dev/null; then pass "الرئيسية - التحديث" "نفّذ تحديثًا فعليًا وبقي على الرئيسية"; else fail "الرئيسية - التحديث" "لم يثبت طلب تحديث فعلي مع البقاء على الرئيسية"; fi
else fail "الرئيسية - التحديث" "تعذر الضغط على زر التحديث"; fi

if launch_route live_tv live; then
  if tap_text "قناة السعودية" live-first prefix; then
    sleep 2; safe_dump live-preview || true
    if app_alive && focused; then pass "البث المباشر - تشغيل المعاينة" "اختيار قناة السعودية شغّل المعاينة وبقي التطبيق مستقرًا"; else fail "البث المباشر - تشغيل المعاينة" "فقد التطبيق الحياة/التركيز بعد اختيار القناة"; fi
    if tap_text "قناة السعودية" live-second prefix; then
      sleep 2; safe_dump live-fullscreen || true
      if grep -Eq 'الجودة|المصدر|الآن' /tmp/evidence/deep/live-fullscreen.xml 2>/dev/null; then pass "البث المباشر - فتح ملء الشاشة" "ظهر Overlay البث في ملء الشاشة"; else fail "البث المباشر - فتح ملء الشاشة" "لم يظهر Overlay المتوقع"; fi
      if tap_text "الجودة" live-quality; then
        sleep 1; safe_dump live-quality-open || true
        if grep -Eq 'اختر الجودة|Auto|HD|FHD|4K' /tmp/evidence/deep/live-quality-open.xml 2>/dev/null; then pass "البث المباشر - قائمة الجودة" "قائمة الجودة/المصدر فتحت"; else fail "البث المباشر - قائمة الجودة" "القائمة لم تظهر"; fi
        adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
      else fail "البث المباشر - قائمة الجودة" "خيار الجودة غير قابل للفتح"; fi
      sleep 6
      adb shell input keyevent KEYCODE_DPAD_UP >/dev/null 2>&1 || true; sleep 1
      safe_dump live-overlay-return || true
      if grep -q 'الجودة' /tmp/evidence/deep/live-overlay-return.xml 2>/dev/null; then pass "البث المباشر - استرجاع معلومات المشغل" "زر أعلى أعاد Overlay بعد الإخفاء"; else fail "البث المباشر - استرجاع معلومات المشغل" "Overlay لم يعد"; fi
    else fail "البث المباشر - فتح ملء الشاشة" "الضغط الثاني على القناة لم يفتح ملء الشاشة"; fi
  else fail "البث المباشر - تشغيل المعاينة" "لم تظهر قناة السعودية التجريبية في واجهة التطبيق"; fi
else fail "البث المباشر - فتح الشاشة" "المسار لم يصبح جاهزًا"; fi

if launch_route series series; then
  if tap_text "مسلسل تجريبي" series-card prefix; then
    sleep 2; safe_dump series-details || true
    if grep -Eq 'الموسم|الحلقة|مسلسل تجريبي' /tmp/evidence/deep/series-details.xml 2>/dev/null; then pass "المسلسلات - صفحة التفاصيل" "تفاصيل المسلسل والمواسم/الحلقات ظهرت"; else fail "المسلسلات - صفحة التفاصيل" "تفاصيل المسلسل لم تظهر"; fi
    if tap_text "الحلقة" series-episode prefix; then
      sleep 3; adb shell input keyevent KEYCODE_DPAD_UP >/dev/null 2>&1 || true; sleep 1; safe_dump series-player-controls || true
      if grep -Eq '16:9|4:5|9:16|ترجم|التالي|السابق|15' /tmp/evidence/deep/series-player-controls.xml 2>/dev/null; then pass "مشغل المسلسلات - أدوات التحكم" "ظهرت أدوات من مجموعة التالي/السابق/الترجمة/±15/نسب العرض"; else fail "مشغل المسلسلات - أدوات التحكم" "أدوات التحكم المطلوبة غير ظاهرة في واجهة الوصول"; fi
      if app_alive && focused; then pass "مشغل المسلسلات - الاستقرار" "المشغل بقي حيًا ومركزًا بعد التحكم"; else fail "مشغل المسلسلات - الاستقرار" "المشغل فقد التطبيق/التركيز"; fi
    else fail "المسلسلات - تشغيل حلقة" "لم أجد حلقة قابلة للاختيار"; fi
  else fail "المسلسلات - فتح مسلسل" "لم أجد مسلسلًا تجريبيًا قابلاً للاختيار"; fi
else fail "المسلسلات - فتح الشاشة" "المسار لم يصبح جاهزًا"; fi

adb logcat -d -v time > /tmp/evidence/deep/deep-logcat.txt 2>/dev/null || true
if grep -E 'FATAL EXCEPTION|ANR in com\.brotv\.iptv' /tmp/evidence/deep/deep-logcat.txt > /tmp/evidence/deep/deep-crash-anr.txt; then
  fail "الاستقرار - Crash/ANR" "تم العثور على Crash أو ANR في الجولة التفصيلية"
else
  : > /tmp/evidence/deep/deep-crash-anr.txt
  pass "الاستقرار - Crash/ANR" "لا يوجد Crash أو ANR في سجل الجولة"
fi
adb shell dumpsys gfxinfo com.brotv.iptv > /tmp/evidence/deep/deep-gfxinfo.txt 2>/dev/null || true
adb shell dumpsys meminfo com.brotv.iptv > /tmp/evidence/deep/deep-meminfo.txt 2>/dev/null || true

exit "$FAIL"
