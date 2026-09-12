#!/usr/bin/env bash
set -euo pipefail
: "${PROJECT_DIR:?PROJECT_DIR is required}"
python3 - <<'PY'
from pathlib import Path
import os
root=Path(os.environ['PROJECT_DIR'])

p=root/'app/src/main/java/com/brotv/iptv/data/local/AppPreferences.kt'
s=p.read_text()
old='''    fun hiddenLiveIds(): Set<Int> = intSet(KEY_HIDDEN_LIVE)\n    fun hiddenCategoryIds(): Set<String> = prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()).orEmpty()\n\n    fun toggleFavoriteLive(id: Int) = toggleInt(KEY_FAVORITE_LIVE, id)\n    fun toggleFavoriteMovie(id: Int) = toggleInt(KEY_FAVORITE_MOVIES, id)\n    fun toggleFavoriteSeries(id: Int) = toggleInt(KEY_FAVORITE_SERIES, id)\n    fun toggleHiddenLive(id: Int) = toggleInt(KEY_HIDDEN_LIVE, id)\n\n    fun toggleHiddenCategory(id: String) {\n        val set = hiddenCategoryIds().toMutableSet()\n        if (!set.add(id)) set.remove(id)\n        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, set).apply()\n    }\n'''
new='''    fun hiddenLiveIds(): Set<Int> = intSet(KEY_HIDDEN_LIVE)\n    fun hiddenCategoryIds(scope: String): Set<String> = prefs.getStringSet("$KEY_HIDDEN_CATEGORIES:$scope", emptySet()).orEmpty()\n\n    fun toggleFavoriteLive(id: Int) = toggleInt(KEY_FAVORITE_LIVE, id)\n    fun toggleFavoriteMovie(id: Int) = toggleInt(KEY_FAVORITE_MOVIES, id)\n    fun toggleFavoriteSeries(id: Int) = toggleInt(KEY_FAVORITE_SERIES, id)\n    fun toggleHiddenLive(id: Int) = toggleInt(KEY_HIDDEN_LIVE, id)\n\n    fun toggleHiddenCategory(scope: String, id: String) {\n        val set = hiddenCategoryIds(scope).toMutableSet()\n        if (!set.add(id)) set.remove(id)\n        prefs.edit().putStringSet("$KEY_HIDDEN_CATEGORIES:$scope", set).apply()\n    }\n'''
if s.count(old)!=1: raise SystemExit('AppPreferences hidden-category block mismatch')
s=s.replace(old,new)
old='''    fun unhideCategory(id: String) {\n        val set = hiddenCategoryIds().toMutableSet().apply { remove(id) }\n        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, set).apply()\n    }\n\n    fun clearHidden() {\n        prefs.edit().remove(KEY_HIDDEN_LIVE).remove(KEY_HIDDEN_CATEGORIES).apply()\n    }\n'''
new='''    fun unhideCategory(scope: String, id: String) {\n        val set = hiddenCategoryIds(scope).toMutableSet().apply { remove(id) }\n        prefs.edit().putStringSet("$KEY_HIDDEN_CATEGORIES:$scope", set).apply()\n    }\n\n    fun clearHidden() {\n        val e = prefs.edit().remove(KEY_HIDDEN_LIVE)\n        prefs.all.keys.filter { it == KEY_HIDDEN_CATEGORIES || it.startsWith("$KEY_HIDDEN_CATEGORIES:") }.forEach(e::remove)\n        e.apply()\n    }\n'''
if s.count(old)!=1: raise SystemExit('AppPreferences clear hidden block mismatch')
p.write_text(s.replace(old,new))
print('patched scoped hidden categories')

p=root/'app/src/main/java/com/brotv/iptv/ui/screens/live/LiveTvScreen.kt'
s=p.read_text()
if s.count('preferences.hiddenCategoryIds()')!=1: raise SystemExit('LiveTv hidden category usage mismatch')
p.write_text(s.replace('preferences.hiddenCategoryIds()','preferences.hiddenCategoryIds("live")'))
print('patched LiveTv hidden scope')

p=root/'app/src/main/java/com/brotv/iptv/ui/screens/library/MediaLibraryScreen.kt'
s=p.read_text()
old='list.filterNot { it.id in preferences.hiddenCategoryIds() }'
new='list.filterNot { it.id in preferences.hiddenCategoryIds(if (isSeries) "series" else "movies") }'
if s.count(old)!=1: raise SystemExit('MediaLibrary hidden category usage mismatch')
p.write_text(s.replace(old,new))
print('patched library hidden scope')

p=root/'app/src/main/java/com/brotv/iptv/ui/screens/settings/SettingsScreen.kt'
s=p.read_text()
old='''    categoryKind?.let{kind->CategoryVisibilityPanel(title=when(kind){CategoryKind.LIVE->tr("فئات القنوات المباشرة","Live categories");CategoryKind.VOD->tr("فئات الفيديو عند الطلب","VOD categories");CategoryKind.SERIES->tr("فئات المسلسلات","Series categories")},categories=categories,loading=categoryLoading,hidden=preferences.hiddenCategoryIds(),version=categoryVersion,english=en,onToggle={id->preferences.toggleHiddenCategory(id);categoryVersion++},onDismiss={categoryKind=null})}\n'''
new='''    categoryKind?.let{kind->\n        val scopeName=when(kind){CategoryKind.LIVE->"live";CategoryKind.VOD->"movies";CategoryKind.SERIES->"series"}\n        CategoryVisibilityPanel(title=when(kind){CategoryKind.LIVE->tr("فئات القنوات المباشرة","Live categories");CategoryKind.VOD->tr("فئات الفيديو عند الطلب","VOD categories");CategoryKind.SERIES->tr("فئات المسلسلات","Series categories")},categories=categories,loading=categoryLoading,hidden=preferences.hiddenCategoryIds(scopeName),version=categoryVersion,english=en,onToggle={id->preferences.toggleHiddenCategory(scopeName,id);categoryVersion++},onDismiss={categoryKind=null})\n    }\n'''
if s.count(old)!=1: raise SystemExit('Settings hidden scope block mismatch')
p.write_text(s.replace(old,new))
print('patched Settings hidden scopes')
PY

echo 'BRO PLUS TV fix9 applied: live/movie/series hidden categories are independent.'
