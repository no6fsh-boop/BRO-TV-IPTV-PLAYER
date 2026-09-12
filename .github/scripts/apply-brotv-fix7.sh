#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
python3 - <<'PY'
from pathlib import Path
import os

root = Path(os.environ['PROJECT_DIR'])

def edit(rel, transforms):
    p = root / rel
    s = p.read_text()
    original = s
    for old, new, count in transforms:
        n = s.count(old)
        if n != count:
            raise SystemExit(f'{rel}: expected {count} occurrences, found {n}: {old[:100]!r}')
        s = s.replace(old, new)
    if s == original:
        raise SystemExit(f'{rel}: no changes made')
    p.write_text(s)
    print('patched', rel)

# Explicit refresh from Home must refresh provider data instead of only navigating to Live TV.
edit('app/src/main/java/com/brotv/iptv/ui/screens/home/HomeScreen.kt', [
    ('import kotlinx.coroutines.delay\n', 'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n', 1),
    ('    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }\n    var daysLeft by remember { mutableStateOf<Int?>(null) }\n',
     '    val scope = rememberCoroutineScope()\n    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }\n    var daysLeft by remember { mutableStateOf<Int?>(null) }\n    var refreshNotice by remember { mutableStateOf<String?>(null) }\n', 1),
    ('                HomeCard(null, "↻", "التحديث") { onNavigate(BroTvDestinations.LIVE_TV) },',
     '''                HomeCard(null, "↻", "التحديث") {\n                    val p = profile\n                    if (p == null) refreshNotice = "لا توجد قائمة تشغيل" else scope.launch {\n                        refreshNotice = "جاري التحديث…"\n                        val result = runCatching {\n                            repository.getLiveCategories(p, forceRefresh = true)\n                            repository.getLiveStreams(p, categoryId = null, forceRefresh = true)\n                            repository.getVodCategories(p, forceRefresh = true)\n                            repository.getVodStreams(p, categoryId = null, forceRefresh = true)\n                            repository.getSeriesCategories(p, forceRefresh = true)\n                            repository.getSeries(p, categoryId = null, forceRefresh = true)\n                        }\n                        refreshNotice = if (result.isSuccess) "تم تحديث المحتوى" else "تعذر تحديث المحتوى"\n                        delay(1800)\n                        refreshNotice = null\n                    }\n                },''', 1),
    ('            }\n        }\n    }\n}\n\n@Composable\nprivate fun InfoBlock',
     '''            }\n        }\n        refreshNotice?.let { notice ->\n            Text(\n                notice,\n                color = Color.White,\n                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)\n                    .background(Color(0xE8141A24), RoundedCornerShape(9.dp)).border(1.dp, BroTvColors.Gold, RoundedCornerShape(9.dp)).padding(horizontal = 18.dp, vertical = 8.dp),\n                fontWeight = FontWeight.Bold,\n            )\n        }\n    }\n}\n\n@Composable\nprivate fun InfoBlock''', 1),
])

# Allow explicit refresh for all library cache families while preserving cache-first defaults everywhere else.
edit('app/src/main/java/com/brotv/iptv/data/remote/IptvRepository.kt', [
    ('suspend fun getVodCategories(profile: PlaylistProfile): List<IptvCategory>', 'suspend fun getVodCategories(profile: PlaylistProfile, forceRefresh: Boolean = false): List<IptvCategory>', 1),
    ('cachedList("${profile.cacheId()}:vod_categories", IptvCategory::class.java) {', 'cachedList("${profile.cacheId()}:vod_categories", IptvCategory::class.java, forceRefresh) {', 1),
    ('suspend fun getVodStreams(profile: PlaylistProfile, categoryId: String? = null): List<VodItem>', 'suspend fun getVodStreams(profile: PlaylistProfile, categoryId: String? = null, forceRefresh: Boolean = false): List<VodItem>', 1),
    ('cachedList("${profile.cacheId()}:vod:${categoryId.orEmpty()}", VodItem::class.java) {', 'cachedList("${profile.cacheId()}:vod:${categoryId.orEmpty()}", VodItem::class.java, forceRefresh) {', 1),
    ('suspend fun getSeriesCategories(profile: PlaylistProfile): List<IptvCategory>', 'suspend fun getSeriesCategories(profile: PlaylistProfile, forceRefresh: Boolean = false): List<IptvCategory>', 1),
    ('cachedList("${profile.cacheId()}:series_categories", IptvCategory::class.java) {', 'cachedList("${profile.cacheId()}:series_categories", IptvCategory::class.java, forceRefresh) {', 1),
    ('suspend fun getSeries(profile: PlaylistProfile, categoryId: String? = null): List<SeriesItem>', 'suspend fun getSeries(profile: PlaylistProfile, categoryId: String? = null, forceRefresh: Boolean = false): List<SeriesItem>', 1),
    ('cachedList("${profile.cacheId()}:series:${categoryId.orEmpty()}", SeriesItem::class.java) {', 'cachedList("${profile.cacheId()}:series:${categoryId.orEmpty()}", SeriesItem::class.java, forceRefresh) {', 1),
])

# Enforce parental category filtering when enabled. The setting now changes visible content, not only a preference bit.
live = root/'app/src/main/java/com/brotv/iptv/ui/screens/live/LiveTvScreen.kt'
s = live.read_text()
old = '            val loadedCategories = repository.getLiveCategories(profile, forceRefresh).filter { it.id !in hiddenCats }\n'
new = '            val loadedCategories = repository.getLiveCategories(profile, forceRefresh).filter { it.id !in hiddenCats }.filterNot { preferences.parentalControlEnabled && isParentalCategory(it.name) }\n'
if s.count(old) != 1: raise SystemExit('LiveTvScreen: parental insertion target mismatch')
s = s.replace(old,new)
anchor = '\nprivate fun smartGroupChannels(channels: List<LiveChannel>): List<SmartChannelGroup>'
if s.count(anchor)!=1: raise SystemExit('LiveTvScreen: helper anchor mismatch')
s = s.replace(anchor, '\nprivate fun isParentalCategory(name:String)=Regex("(?i)(xxx|adult|18\\\\+|للكبار|بالغ)").containsMatchIn(name)\n'+anchor, 1)
live.write_text(s)
print('patched LiveTvScreen parental filter')

lib = root/'app/src/main/java/com/brotv/iptv/ui/screens/library/MediaLibraryScreen.kt'
s = lib.read_text()
old = '                categories = preferences.sortCategories(if (isSeries) "series" else "movies", list.filterNot { it.id in preferences.hiddenCategoryIds() })\n'
new = '                categories = preferences.sortCategories(if (isSeries) "series" else "movies", list.filterNot { it.id in preferences.hiddenCategoryIds() }.filterNot { preferences.parentalControlEnabled && isParentalCategory(it.name) })\n'
if s.count(old)!=1: raise SystemExit('MediaLibraryScreen: parental insertion target mismatch')
s=s.replace(old,new)
anchor='\nprivate fun nextSort(current: MediaSort): MediaSort'
if s.count(anchor)!=1: raise SystemExit('MediaLibraryScreen: helper anchor mismatch')
s=s.replace(anchor,'\nprivate fun isParentalCategory(name:String)=Regex("(?i)(xxx|adult|18\\\\+|للكبار|بالغ)").containsMatchIn(name)\n'+anchor,1)
lib.write_text(s)
print('patched MediaLibrary parental filter')

# Contract guards for the latest TV UX requirements.
settings = (root/'app/src/main/java/com/brotv/iptv/ui/screens/settings/SettingsScreen.kt').read_text()
if 'Text(category.name,color=Color.White' not in settings:
    raise SystemExit('Category visibility rows must keep category name text white')
live_s = live.read_text()
folder = live_s.split('@Composable private fun FolderRow',1)[1].split('@Composable private fun ChannelRow',1)[0]
if 'onFocusChanged{focused=it.isFocused;if(it.isFocused)ensureVisible' not in folder or '.clickable(onClick=onClick)' not in folder:
    raise SystemExit('FolderRow focus/click contract changed unexpectedly')
if 'applyCategory' in folder:
    raise SystemExit('FolderRow focus must not apply a category; category changes only on OK/click')
print('verified white hidden-category labels and focus-only folder navigation contract')
PY

echo 'BRO PLUS TV fix7 applied: real home refresh, full cache refresh support, parental filtering, UX contract guards.'
