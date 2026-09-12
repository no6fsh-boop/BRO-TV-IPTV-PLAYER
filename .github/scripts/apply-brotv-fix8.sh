#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
python3 - <<'PY'
from pathlib import Path
import os
root=Path(os.environ['PROJECT_DIR'])

p=root/'app/src/main/java/com/brotv/iptv/data/local/AppPreferences.kt'
s=p.read_text()
old='''    var parentalControlEnabled: Boolean\n        get() = prefs.getBoolean("parental_enabled", false)\n        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()\n\n'''
new='''    var parentalControlEnabled: Boolean\n        get() = prefs.getBoolean("parental_enabled", false)\n        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()\n\n    var uiLanguage: String\n        get() = prefs.getString("ui_language", "ar") ?: "ar"\n        set(value) = prefs.edit().putString("ui_language", if (value == "en") "en" else "ar").apply()\n\n'''
if s.count(old)!=1: raise SystemExit('AppPreferences language anchor mismatch')
p.write_text(s.replace(old,new))
print('patched AppPreferences language')

p=root/'app/src/main/java/com/brotv/iptv/ui/screens/settings/SettingsScreen.kt'
s=p.read_text()
s=s.replace('''    var categoryVersion by remember{mutableIntStateOf(0)}\n\n    fun openCategories''','''    var categoryVersion by remember{mutableIntStateOf(0)}\n    var uiLanguage by remember { mutableStateOf(preferences.uiLanguage) }\n    val en = uiLanguage == "en"\n    fun tr(ar:String,enText:String)=if(en) enText else ar\n\n    fun openCategories''')
old='''    val cards=listOf(\n        SettingCardData("文","تغيير اللغة"){message="لغة الواجهة الحالية: العربية"},\n        SettingCardData("🔓","تعطيل الرقابة الأبوية"){preferences.parentalControlEnabled=false;message="تم تعطيل الرقابة الأبوية"},\n        SettingCardData("🔒","الرقابة الأبوية"){preferences.parentalControlEnabled=true;message="تم تفعيل الرقابة الأبوية"},\n        SettingCardData("◉̸","إخفاء فئات المسلسلات"){openCategories(CategoryKind.SERIES)},\n        SettingCardData("◉̸","إخفاء فئات الفيديو عند الطلب"){openCategories(CategoryKind.VOD)},\n        SettingCardData("◉̸","إخفاء فئات القنوات المباشرة"){openCategories(CategoryKind.LIVE)},\n        SettingCardData("◷","تنسيق الوقت"){preferences.use24HourClock=!preferences.use24HourClock;message=if(preferences.use24HourClock)"تم اعتماد نظام 24 ساعة" else "تم اعتماد نظام 12 ساعة"},\n        SettingCardData("▣","تنسيق البث المباشر"){preferences.liveLayoutMode=(preferences.liveLayoutMode+1)%3;message="تنسيق البث المباشر: ${preferences.liveLayoutMode+1}"},\n        SettingCardData("▦","تغيير التخطيط"){preferences.compactLibraryLayout=!preferences.compactLibraryLayout;message=if(preferences.compactLibraryLayout)"تم اعتماد التخطيط المضغوط" else "تم اعتماد التخطيط القياسي"},\n        SettingCardData("⌫","مسح تاريخ المسلسلات"){preferences.clearSeriesHistory();message="تم مسح تاريخ المسلسلات"},\n        SettingCardData("⌫","مسح تاريخ الأفلام"){preferences.clearMovieHistory();message="تم مسح تاريخ الأفلام"},\n        SettingCardData("⌫","مسح قنوات السجل"){preferences.clearLiveHistory();message="تم مسح سجل القنوات"},\n    )\n'''
new='''    val cards=listOf(\n        SettingCardData("文",tr("تغيير اللغة","Change language")){uiLanguage=if(uiLanguage=="en")"ar" else "en";preferences.uiLanguage=uiLanguage;message=if(uiLanguage=="en")"Interface language: English" else "لغة الواجهة: العربية"},\n        SettingCardData("🔓",tr("تعطيل الرقابة الأبوية","Disable parental control")){preferences.parentalControlEnabled=false;message=tr("تم تعطيل الرقابة الأبوية","Parental control disabled")},\n        SettingCardData("🔒",tr("الرقابة الأبوية","Parental control")){preferences.parentalControlEnabled=true;message=tr("تم تفعيل الرقابة الأبوية","Parental control enabled")},\n        SettingCardData("◉̸",tr("إخفاء فئات المسلسلات","Hide series categories")){openCategories(CategoryKind.SERIES)},\n        SettingCardData("◉̸",tr("إخفاء فئات الفيديو عند الطلب","Hide VOD categories")){openCategories(CategoryKind.VOD)},\n        SettingCardData("◉̸",tr("إخفاء فئات القنوات المباشرة","Hide live categories")){openCategories(CategoryKind.LIVE)},\n        SettingCardData("◷",tr("تنسيق الوقت","Time format")){preferences.use24HourClock=!preferences.use24HourClock;message=if(preferences.use24HourClock)tr("تم اعتماد نظام 24 ساعة","24-hour clock enabled") else tr("تم اعتماد نظام 12 ساعة","12-hour clock enabled")},\n        SettingCardData("▣",tr("تنسيق البث المباشر","Live TV layout")){preferences.liveLayoutMode=(preferences.liveLayoutMode+1)%3;message=tr("تنسيق البث المباشر: ${preferences.liveLayoutMode+1}","Live TV layout: ${preferences.liveLayoutMode+1}")},\n        SettingCardData("▦",tr("تغيير التخطيط","Change layout")){preferences.compactLibraryLayout=!preferences.compactLibraryLayout;message=if(preferences.compactLibraryLayout)tr("تم اعتماد التخطيط المضغوط","Compact layout enabled") else tr("تم اعتماد التخطيط القياسي","Standard layout enabled")},\n        SettingCardData("⌫",tr("مسح تاريخ المسلسلات","Clear series history")){preferences.clearSeriesHistory();message=tr("تم مسح تاريخ المسلسلات","Series history cleared")},\n        SettingCardData("⌫",tr("مسح تاريخ الأفلام","Clear movie history")){preferences.clearMovieHistory();message=tr("تم مسح تاريخ الأفلام","Movie history cleared")},\n        SettingCardData("⌫",tr("مسح قنوات السجل","Clear live history")){preferences.clearLiveHistory();message=tr("تم مسح سجل القنوات","Live history cleared")},\n    )\n'''
if s.count(old)!=1: raise SystemExit('Settings cards block mismatch')
s=s.replace(old,new)
s=s.replace('SettingCard("↩","رجوع",Modifier.width(150.dp).height(58.dp))','SettingCard("↩",tr("رجوع","Back"),Modifier.width(150.dp).height(58.dp))')
s=s.replace('Text("الإعدادات",color=Color.White','Text(tr("الإعدادات","Settings"),color=Color.White')
s=s.replace('''    categoryKind?.let{kind->CategoryVisibilityPanel(title=when(kind){CategoryKind.LIVE->"فئات القنوات المباشرة";CategoryKind.VOD->"فئات الفيديو عند الطلب";CategoryKind.SERIES->"فئات المسلسلات"},categories=categories,loading=categoryLoading,hidden=preferences.hiddenCategoryIds(),version=categoryVersion,onToggle={id->preferences.toggleHiddenCategory(id);categoryVersion++},onDismiss={categoryKind=null})}\n''','''    categoryKind?.let{kind->CategoryVisibilityPanel(title=when(kind){CategoryKind.LIVE->tr("فئات القنوات المباشرة","Live categories");CategoryKind.VOD->tr("فئات الفيديو عند الطلب","VOD categories");CategoryKind.SERIES->tr("فئات المسلسلات","Series categories")},categories=categories,loading=categoryLoading,hidden=preferences.hiddenCategoryIds(),version=categoryVersion,english=en,onToggle={id->preferences.toggleHiddenCategory(id);categoryVersion++},onDismiss={categoryKind=null})}\n''')
s=s.replace('''@Composable private fun CategoryVisibilityPanel(title:String,categories:List<IptvCategory>,loading:Boolean,hidden:Set<String>,version:Int,onToggle:(String)->Unit,onDismiss:()->Unit){''','''@Composable private fun CategoryVisibilityPanel(title:String,categories:List<IptvCategory>,loading:Boolean,hidden:Set<String>,version:Int,english:Boolean,onToggle:(String)->Unit,onDismiss:()->Unit){''')
s=s.replace('Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text("اضغط OK لإظهار/إخفاء الفئة",color=BroTvColors.TextSecondary)', 'Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(if(english)"Press OK to show/hide category" else "اضغط OK لإظهار/إخفاء الفئة",color=BroTvColors.TextSecondary)')
s=s.replace('items(categories,key={it.id}){cat->CategoryRow(cat,cat.id in hidden){onToggle(cat.id)}}', 'items(categories,key={it.id}){cat->CategoryRow(cat,cat.id in hidden,english){onToggle(cat.id)}}')
s=s.replace('SettingCard("↩","رجوع",Modifier.fillMaxWidth().height(54.dp),onDismiss)', 'SettingCard("↩",if(english)"Back" else "رجوع",Modifier.fillMaxWidth().height(54.dp),onDismiss)')
s=s.replace('@Composable private fun CategoryRow(category:IptvCategory,isHidden:Boolean,onClick:()->Unit)', '@Composable private fun CategoryRow(category:IptvCategory,isHidden:Boolean,english:Boolean,onClick:()->Unit)')
s=s.replace('Text(if(isHidden)"مخفي" else "ظاهر",color=if(isHidden)BroTvColors.TextSecondary else BroTvColors.Gold)', 'Text(if(english){if(isHidden)"Hidden" else "Visible"}else{if(isHidden)"مخفي" else "ظاهر"},color=if(isHidden)BroTvColors.TextSecondary else BroTvColors.Gold)')
p.write_text(s)
print('patched Settings visible language switching')

p=root/'app/src/main/java/com/brotv/iptv/ui/screens/home/HomeScreen.kt'
s=p.read_text()
s=s.replace('''    val timePattern = if (preferences.use24HourClock) "HH:mm" else "hh:mm a"\n    val time = remember(now, preferences.use24HourClock) { SimpleDateFormat(timePattern, Locale("ar")).format(Date(now)) }\n    val date = remember(now) { SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(now)) }\n''','''    val english = preferences.uiLanguage == "en"\n    fun tr(ar:String,en:String)=if(english) en else ar\n    val timePattern = if (preferences.use24HourClock) "HH:mm" else "hh:mm a"\n    val locale = if (english) Locale.ENGLISH else Locale("ar")\n    val time = remember(now, preferences.use24HourClock, english) { SimpleDateFormat(timePattern, locale).format(Date(now)) }\n    val date = remember(now, english) { SimpleDateFormat("EEEE d MMMM yyyy", locale).format(Date(now)) }\n''')
repls={
'"عالم من الترفيه"':'tr("عالم من الترفيه","A world of entertainment")',
'"بين يديك"':'tr("بين يديك","at your fingertips")',
'"☁  الرياض"':'tr("☁  الرياض","☁  Riyadh")',
'"الطقس"':'tr("الطقس","Weather")',
'"باقي على الاشتراك"':'tr("باقي على الاشتراك","Subscription remaining")',
'"مدة الاشتراك غير متاحة"':'tr("مدة الاشتراك غير متاحة","Subscription unavailable")',
'"المفضلة"':'tr("المفضلة","Favorites")',
'"القنوات"':'tr("القنوات","Channels")',
'"الأفلام"':'tr("الأفلام","Movies")',
'"المسلسلات"':'tr("المسلسلات","Series")',
'"التحديث"':'tr("التحديث","Refresh")',
'"تنسيق القنوات"':'tr("تنسيق القنوات","Channel layout")',
'"تغيير قائمة التشغيل"':'tr("تغيير قائمة التشغيل","Change playlist")',
'"الإعدادات"':'tr("الإعدادات","Settings")',
'"اللغة"':'tr("اللغة","Language")',
}
for old,new in repls.items(): s=s.replace(old,new)
p.write_text(s)
print('patched Home visible language switching')
PY

echo 'BRO PLUS TV fix8 applied: persisted Arabic/English switch with visible Home/Settings translations.'
