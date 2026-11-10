package io.github.poohcjs.hotdealradar.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.poohcjs.hotdealradar.MainActivity
import io.github.poohcjs.hotdealradar.R
import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.Item
import io.github.poohcjs.hotdealradar.data.Prefs
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.ArchiveClient
import io.github.poohcjs.hotdealradar.data.LiveProduct
import io.github.poohcjs.hotdealradar.data.LiveSearchClient
import io.github.poohcjs.hotdealradar.data.LiveSort
import io.github.poohcjs.hotdealradar.data.MallDirectory
import io.github.poohcjs.hotdealradar.data.PriceWatch
import io.github.poohcjs.hotdealradar.data.liveDeals
import io.github.poohcjs.hotdealradar.data.mergeSources
import io.github.poohcjs.hotdealradar.data.shownCost
import io.github.poohcjs.hotdealradar.data.shownUnit
import io.github.poohcjs.hotdealradar.data.sortLive
import io.github.poohcjs.hotdealradar.data.beatsOthers
import io.github.poohcjs.hotdealradar.data.currentBest
import io.github.poohcjs.hotdealradar.data.matchesKeyword
import io.github.poohcjs.hotdealradar.data.dealVisible
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.won
import java.util.concurrent.TimeUnit

/**
 * 1시간마다 deals.json을 받아 새 핫딜과 찜 목표가 도달을 알림으로 보낸다.
 * 수집기(GitHub Actions)도 1시간마다 돌기 때문에 이보다 자주 받을 이유가 없다.
 */
class DealCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val s = prefs.settings.value
        if (!s.notify && !s.notifyTarget && s.keywords.isEmpty() && prefs.watches.value.isEmpty()) return Result.success()
        val data = runCatching { Repository(applicationContext, prefs).fetch() }.getOrElse { return Result.retry() }
        val index = Index(data)
        if (s.keywords.isNotEmpty()) checkKeywords(index, prefs, s)
        if (prefs.watches.value.isNotEmpty()) checkWatches(index, prefs)
        if (s.notifyTarget) refreshFavPrices(index, prefs)

        if (s.notify) {
            val already = prefs.notifiedIds
            val fresh = pickNewDeals(index, s, already)
            if (fresh.isNotEmpty()) {
                notifyDeals(fresh, index)
                prefs.notifiedIds = already + fresh.map { it.key }
            } else if (already.isEmpty()) {
                // 처음 켰을 때 이미 떠 있던 딜로 알림이 쏟아지지 않게 '본 것'으로 표시만 한다
                prefs.notifiedIds = index.deals.map { it.key }.toSet()
            }
        }
        if (s.notifyTarget) checkTargets(index, prefs)
        return Result.success()
    }

    /** 관심 키워드가 들어간 새 커뮤니티 글 (최근 3시간 안에 처음 본 글만, 같은 글은 한 번만) */
    private fun checkKeywords(index: Index, prefs: Prefs, s: io.github.poohcjs.hotdealradar.data.Settings) {
        val already = prefs.notifiedIds
        val since = System.currentTimeMillis() - 3 * 3_600_000L
        val hits = index.posts.filter { !it.ended && (it.detectedAt ?: 0) >= since && "kw:${it.key}" !in already }
            .mapNotNull { p -> matchesKeyword(p.title, s.keywords)?.let { k -> k to p } }
        if (hits.isEmpty()) return
        if (canNotify(applicationContext)) {
            val nm = NotificationManagerCompat.from(applicationContext)
            hits.take(MAX_PER_RUN).forEach { (k, p) ->
                val target = index.itemForPost(p)
                val line = listOfNotNull(
                    index.srcName(p.source), p.price?.let { "${won(it)}원" },
                    target.rate?.let { "평균보다 ${pct(it)}%↓" },
                ).joinToString(" · ")
                val n = NotificationCompat.Builder(applicationContext, CHANNEL_DEALS)
                    .setSmallIcon(R.drawable.ic_fire)
                    .setContentTitle("‘$k’ 딜 · ${p.title}")
                    .setContentText(line)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${p.title}\n$line"))
                    .setContentIntent(openIntent(target.key))
                    .setAutoCancel(true)
                    .build()
                runCatching { nm.notify(("kw:" + p.key).hashCode(), n) }
            }
        }
        prefs.notifiedIds = already + hits.map { "kw:${it.second.key}" }
    }

    /**
     * 가격 알림: 알림을 걸 때 찾은 가장 싼 값보다 싼 것이 나오면 알린다.
     *  1) 새 커뮤니티 핫딜 (매시간, 최근 3시간에 처음 본 글)
     *  2) 다나와·에누리·관련 공식몰 다시 검색 (알림마다 6시간에 한 번, 한 번에 3개까지)
     */
    private suspend fun checkWatches(index: Index, prefs: Prefs) {
        val now = System.currentTimeMillis()
        val already = prefs.notifiedIds
        val sent = mutableSetOf<String>()
        val since = now - 3 * 3_600_000L
        val fresh = index.posts.filter { (it.detectedAt ?: 0) >= since }
        for (w in prefs.watches.value) {
            val deals = liveDeals(fresh, w.query)
            for (d in deals) {
                val id = "w:${w.query}:${d.pcode}"
                if (id in already || id in sent) continue
                val saving = watchSaving(w, d) ?: continue
                sent += id
                notifyWatch(w, "‘${w.query}’ 핫딜이 지금 최저가보다 ${saving.first}% 싸요", "${d.name}\n${d.mall} · ${saving.second}", w.query, id)
            }
        }
        if (sent.isNotEmpty()) prefs.notifiedIds = already + sent

        // 가격비교 사이트·공식몰 다시 검색
        val due = prefs.watches.value.filter { now - it.checkedAt > 6 * 3_600_000L }.sortedBy { it.checkedAt }.take(3)
        if (due.isEmpty()) return
        val client = LiveSearchClient()
        val malls = MallDirectory.merge(prefs.userMalls, runCatching {
            ArchiveClient(applicationContext) { Repository(applicationContext, prefs).dataUrl().substringBeforeLast('/') }.malls()
        }.getOrDefault(emptyList()))
        for (w in due) {
            val dn = runCatching { client.search(w.query) }.getOrDefault(emptyList())
            val en = runCatching { client.enuriSearch(w.query) }.getOrDefault(emptyList())
            val ml = MallDirectory.match(w.query, malls).flatMap { (m, _) -> runCatching { client.mallSearch(m, w.query) }.getOrDefault(emptyList()) }
            val all = mergeSources(dn, en, ml).filter { !it.used }
            val best = sortLive(all, LiveSort.UNIT, true).firstOrNull { b -> w.per == null || shownUnit(b, LiveSort.UNIT, true)?.per == w.per }
            var next = w.copy(checkedAt = now)
            if (best != null) {
                val saving = watchSaving(w, best)
                val bu = shownUnit(best, LiveSort.UNIT, true)?.value
                val bc = shownCost(best, LiveSort.UNIT, true)
                val newLow = if (w.per != null) bu != null && bu < (w.notifiedUnit ?: Long.MAX_VALUE) * 97 / 100 else bc < (w.notifiedCost ?: Long.MAX_VALUE) * 97 / 100
                if (saving != null && saving.first >= 5 && newLow) {
                    notifyWatch(w, "‘${w.query}’ 가격이 내려갔어요 (${saving.first}%↓)", "${best.name}\n${best.mall ?: best.source.label} · ${saving.second}", w.query, "w:${w.query}:price")
                    next = next.copy(notifiedUnit = bu, notifiedCost = bc)
                }
            }
            prefs.updateWatches { list -> list.map { if (it.query == w.query) next else it } }
        }
    }

    private fun notifyWatch(w: PriceWatch, title: String, text: String, query: String, id: String) {
        if (!canNotify(applicationContext)) return
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SEARCH, query)
        }
        val pi = PendingIntent.getActivity(applicationContext, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_DEALS)
            .setSmallIcon(R.drawable.ic_fire)
            .setContentTitle(title)
            .setContentText(text.substringAfter('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), n) }
    }

    /**
     * 찜한 제품 중 수집기가 지켜보지 않는 것은 앱이 다나와 상품 페이지를 직접 확인한다.
     * 제품마다 3시간에 한 번, 한 번에 최대 8개만 (다나와에 부담 주지 않게).
     */
    private suspend fun refreshFavPrices(index: Index, prefs: Prefs) {
        val now = System.currentTimeMillis()
        val due = prefs.favs.value.filter { (pcode, f) ->
            pcode.all { it.isDigit() } && index.products[pcode]?.lowest == null && now - f.checkedAt > 3 * 3_600_000L
        }.keys.take(8)
        if (due.isEmpty()) return
        val client = LiveSearchClient()
        for (pcode in due) {
            val page = runCatching { client.product(pcode) }.getOrNull() ?: continue
            val best = page.offers.minByOrNull { it.price } ?: continue
            prefs.updateFavs { favs -> favs[pcode]?.let { favs + (pcode to it.withPrice(best.price, best.mall, now)) } ?: favs }
        }
    }

    private fun checkTargets(index: Index, prefs: Prefs) {
        val favs = prefs.favs.value
        val sent = prefs.targetNotified.toMutableMap()
        for ((pcode, fav) in favs) {
            val target = fav.target ?: continue
            val best = currentBest(pcode, fav, index) ?: continue
            if (best.price > target) {
                sent.remove(pcode) // 다시 올라가면 다음에 내려올 때 또 알린다
                continue
            }
            if (sent[pcode]?.let { best.price >= it } == true) continue
            notifyTarget(pcode, fav, best.price, best.mall, best.via, if (index.products[pcode] != null) "prod:$pcode" else "live:$pcode")
            sent[pcode] = best.price
        }
        prefs.targetNotified = sent
    }

    private fun notifyDeals(deals: List<Item>, index: Index) {
        if (!canNotify(applicationContext)) return
        val nm = NotificationManagerCompat.from(applicationContext)
        deals.take(MAX_PER_RUN).forEach { d ->
            val text = "${d.mall} ${won(d.price)}원 · ${if (d.reference == "lowest") "최저가" else "평균가"} ${won(d.referencePrice)}원보다 ${pct(d.rate)}% ↓"
            val n = NotificationCompat.Builder(applicationContext, CHANNEL_DEALS)
                .setSmallIcon(R.drawable.ic_fire)
                .setContentTitle("${pct(d.rate)}% · ${d.name.ifBlank { d.title }}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${index.catName(d.cat)}"))
                .setContentIntent(openIntent(d.key))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            runCatching { nm.notify(d.key.hashCode(), n) }
        }
        if (deals.size > 1) {
            val summary = NotificationCompat.Builder(applicationContext, CHANNEL_DEALS)
                .setSmallIcon(R.drawable.ic_fire)
                .setContentTitle("새 핫딜 ${deals.size}개")
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openIntent(null))
                .build()
            runCatching { nm.notify(SUMMARY_ID, summary) }
        }
    }

    private fun notifyTarget(pcode: String, fav: Fav, price: Long, mall: String, via: String, openKey: String) {
        if (!canNotify(applicationContext)) return
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_DEALS)
            .setSmallIcon(R.drawable.ic_fire)
            .setContentTitle("목표가 도달 · ${fav.name}")
            .setContentText("$mall ${won(price)}원 ($via) · 목표 ${won(fav.target)}원")
            .setContentIntent(openIntent(openKey))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(("target:$pcode").hashCode(), n) }
    }

    private fun openIntent(key: String?): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (key != null) putExtra(MainActivity.EXTRA_ITEM, key)
        }
        return PendingIntent.getActivity(
            applicationContext, key?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_DEALS = "deals"
        private const val GROUP = "hotdeals"
        private const val SUMMARY_ID = 1
        private const val MAX_PER_RUN = 6
        private const val UNIQUE = "deal-check"

        /**
         * 가격 알림 기준보다 얼마나 싼지: (몇 %, 설명). 같은 단위 단가로 비교하고, 기준에 단가가 없을 때만 가격으로.
         * 2% 넘게 싸지 않으면 null.
         */
        fun watchSaving(w: PriceWatch, p: LiveProduct): Pair<Int, String>? {
            val u = shownUnit(p, LiveSort.UNIT, true)
            val cost = shownCost(p, LiveSort.UNIT, true)
            if (w.per != null && w.unit != null) {
                if (u == null || u.per != w.per || u.value * 100 >= w.unit * 98) return null
                val pctv = ((w.unit - u.value) * 100 / w.unit).toInt()
                return pctv to "${won(cost)}원 · ${if (u.estimated) "약 " else ""}${won(u.value)}원/${u.per} (알림 걸 때 ${won(w.unit)}원/${w.per})"
            }
            val base = w.cost ?: return null
            if (u != null || cost * 100 >= base * 98) return null
            val pctv = ((base - cost) * 100 / base).toInt()
            return pctv to "${won(cost)}원 (알림 걸 때 ${won(base)}원)"
        }

        /** 알림 대상: 아직 알리지 않았고, 알림 기준 이상이며, 화면 필터(제외 쇼핑몰·가격 확인 필요·끝난 딜)를 통과한 딜 */
        fun pickNewDeals(index: Index, s: Settings, already: Set<String>): List<Item> {
            if (already.isEmpty()) return emptyList()
            return index.deals
                .filter { it.key !in already }
                .filter { (it.rate ?: 0.0) >= s.notifyThreshold && (!s.onlyCheapest || beatsOthers(it)) && !it.outlier }
                .filter { dealVisible(it, s.copy(hideSuspicious = true, hideEnded = true)) }
                .filter { !s.notifyInterestsOnly || s.interests.isEmpty() || it.cat in s.interests }
                .sortedByDescending { it.rate }
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL_DEALS, context.getString(R.string.channel_deals), NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = context.getString(R.string.channel_deals_desc)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE)
                return
            }
            val req = PeriodicWorkRequestBuilder<DealCheckWorker>(60, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // UPDATE: 예전 버전이 등록한 30분 주기도 새 주기로 바꾼다
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
