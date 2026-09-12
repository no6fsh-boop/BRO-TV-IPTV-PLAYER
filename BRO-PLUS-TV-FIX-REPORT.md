# BRO PLUS TV — Fix Report V2

## ما تم إصلاحه

1. **تسجيل M3U على خيط الواجهة**
   - `IptvRepository.authenticate()` أصبح ينفذ التحقق كاملًا داخل `Dispatchers.IO`.
   - يمنع `NetworkOnMainThreadException` وتجميد واجهة Android TV أثناء جلب قائمة M3U.

2. **استهلاك الذاكرة في M3U / XMLTV**
   - M3U أصبح يُقرأ ويُحلل مباشرة من `ResponseBody.charStream()` بدل تحويل الملف كاملًا إلى `String` ثم قائمة أسطر.
   - XMLTV أصبح يُحلل من stream بدل تحميل النص كاملًا أولًا.

3. **تشغيل QR مرتين / تعارض المنفذ 8988**
   - تغيير وضع Xtream/M3U لم يعد يشغّل الخادم مباشرة من مكانين مختلفين.
   - `LaunchedEffect(viewModel.mode)` أصبح المالك الوحيد لإعادة تشغيل جلسة QR.
   - `QrPairingManager.startSession/stopSession` أصبحا متسلسلين بـ `@Synchronized`.
   - أخطاء ربط المنفذ أصبحت تُلتقط وتُعرض بدل أن تتحول إلى استثناء غير معالج.
   - أضيف generation id لمنع جلسة QR قديمة من إرسال بيانات بعد إنشاء جلسة أحدث.

4. **إعادة المحاولة بعد فشل بيانات QR**
   - الخادم لا يُغلق فور استلام النموذج قبل نتيجة التحقق من مزود IPTV.
   - إذا كانت بيانات المزود غير صحيحة، ينشئ التطبيق QR جديدًا تلقائيًا للمحاولة التالية.
   - POST الخاص بالإقران أصبح synchronized ويتحقق من الحقول المطلوبة قبل استهلاك التوكن.

5. **Demo في إصدار الإنتاج**
   - `QaConfig.INCLUDE_DEMO_CONTENT` أصبح مربوطًا بـ `BuildConfig.DEBUG` بدل `true` دائمًا.

6. **خطأ بناء اكتشف أثناء التحقق**
   - تبسيط دالة تغيير الوضع إلى `setMode(LoginMode)` كان سيتعارض مع setter المولّد للخاصية `mode` على JVM.
   - تم تغيير اسم الدالة إلى `selectMode()` وإعادة اختبار ترجمة `LoginViewModel` بنجاح.

## الملفات المعدلة

- `app/src/main/java/com/brotv/iptv/data/remote/IptvRepository.kt`
- `app/src/main/java/com/brotv/iptv/data/pairing/QrPairingManager.kt`
- `app/src/main/java/com/brotv/iptv/data/pairing/PairingServer.kt`
- `app/src/main/java/com/brotv/iptv/ui/screens/login/LoginViewModel.kt`
- `app/src/main/java/com/brotv/iptv/ui/screens/login/LoginScreen.kt`

## نتائج التحقق

- Pure Kotlin tests: **6/6 PASS**
- Static regression checks: **14/14 PASS**
- Pairing files Kotlin stub compile: **PASS**
- LoginViewModel Kotlin stub compile: **PASS**
- IptvRepository Kotlin stub compile: **PASS**

## حدود التحقق

الحزمة المرفوعة لا تحتوي ملفات Gradle/Gradle Wrapper، والبيئة الحالية لا تحتوي Android SDK/محاكي، لذلك لم يتم إنتاج APK ولم يتم تشغيل التطبيق على جهاز Android TV فعلي. التحقق هنا يغطي منطق السورس والترجمة الجزئية الموجهة للأجزاء المعدلة.

`android:usesCleartextTraffic="true"` بقي مفعّلًا لأن التطبيق يدعم مزودي IPTV الذين يعملون عبر HTTP، وكذلك خادم QR المحلي. هذا قرار توافق وليس خطأ بناء؛ قبل النشر العام يفضّل عرض تحذير للمستخدم عند استخدام HTTP وتشجيع HTTPS حين يكون متاحًا.
