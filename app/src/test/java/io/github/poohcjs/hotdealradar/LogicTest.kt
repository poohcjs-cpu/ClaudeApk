package io.github.poohcjs.hotdealradar

import io.github.poohcjs.hotdealradar.data.Fav
import io.github.poohcjs.hotdealradar.data.Index
import io.github.poohcjs.hotdealradar.data.ItemKind
import io.github.poohcjs.hotdealradar.data.Repository
import io.github.poohcjs.hotdealradar.data.Settings
import io.github.poohcjs.hotdealradar.data.dealVisible
import io.github.poohcjs.hotdealradar.data.isHot
import io.github.poohcjs.hotdealradar.data.pct
import io.github.poohcjs.hotdealradar.data.search
import io.github.poohcjs.hotdealradar.data.targetReached
import io.github.poohcjs.hotdealradar.data.Tier
import io.github.poohcjs.hotdealradar.data.tier
import io.github.poohcjs.hotdealradar.data.verdictText
import io.github.poohcjs.hotdealradar.data.won
import io.github.poohcjs.hotdealradar.work.DealCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 수집기가 실제로 만든 deals.json(2026-11-01 실행분)으로 확인한다. */
class LogicTest {
    private val index: Index by lazy {
        val text = javaClass.classLoader!!.getResource("deals_sample.json")!!.readText()
        Index(Repository.parse(text))
    }

    @Test fun parsesRealFile() {
        assertTrue(index.deals.isNotEmpty())
        assertTrue(index.posts.size > 50)
        assertTrue(index.products.isNotEmpty())
        assertTrue(index.deals.any { it.kind == ItemKind.MARKET })
        assertTrue(index.deals.any { it.kind == ItemKind.COMMUNITY })
    }

    @Test fun thresholdFilters() {
        val s30 = Settings(threshold = 30)
        val s5 = Settings(threshold = 5)
        assertTrue(index.deals.count { isHot(it, s5) } >= index.deals.count { isHot(it, s30) })
        index.deals.filter { isHot(it, s30) }.forEach { assertTrue(it.rate!! >= 30.0) }
    }

    @Test fun excludedMallAndHiddenSource() {
        val d = index.deals.first { it.mall.isNotBlank() }
        assertFalse(dealVisible(d, Settings(excludedMalls = listOf(d.mall), hideSuspicious = false, hideEnded = false)))
        val market = index.deals.first { it.kind == ItemKind.MARKET }
        assertFalse(dealVisible(market, Settings(hiddenSources = listOf("danawa"), hideSuspicious = false, hideEnded = false)))
    }

    @Test fun communityPostOpensMergedDeal() {
        val deal = index.deals.first { it.kind == ItemKind.COMMUNITY }
        val post = index.posts.first { it.key == "post:${deal.mentions.first().id}" }
        assertEquals(deal.key, index.itemForPost(post).key)
    }

    @Test fun verdictMentionsSavingAndThreshold() {
        val d = index.deals.first { (it.save ?: 0) > 0 }
        val text = verdictText(d, Settings(threshold = 30))
        assertTrue(text, text.contains("${won(d.save)}원(${pct(d.rate)}%) 싸요"))
    }

    @Test fun targetPrice() {
        val pcode = index.products.values.first { it.lowest != null }.pcode
        val best = index.bestNow(pcode)!!.price
        assertTrue(targetReached(pcode, mapOf(pcode to Fav(target = best)), index))
        assertFalse(targetReached(pcode, mapOf(pcode to Fav(target = best - 1)), index))
        assertFalse(targetReached(pcode, mapOf(pcode to Fav(target = null)), index))
    }

    @Test fun searchFindsProduct() {
        val p = index.products.values.first { it.name.length > 4 }
        val res = search(index, p.name.take(4))
        assertTrue(res.any { it.pcode == p.pcode })
    }

    @Test fun notificationsSkipFirstRunAndAlreadySent() {
        val s = Settings(notifyThreshold = 5, notifyInterestsOnly = false)
        assertTrue(DealCheckWorker.pickNewDeals(index, s, emptySet()).isEmpty()) // 처음엔 쏟아내지 않음
        val all = DealCheckWorker.pickNewDeals(index, s, setOf("dummy"))
        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it.suspicious || it.ended })
        val again = DealCheckWorker.pickNewDeals(index, s, all.map { it.key }.toSet())
        assertTrue(again.isEmpty())
    }

    @Test fun defaultsAreRealistic() {
        val s = Settings()
        assertEquals(10, s.threshold)
        assertEquals(15, s.notifyThreshold)
        assertTrue(s.onlyCheapest)
        // 기본 기준으로 실제 데이터에서 핫딜이 나와야 한다 (30% 기준에서는 거의 비었다)
        assertTrue(index.deals.count { isHot(it, s) } > index.deals.count { isHot(it, s.copy(threshold = 30)) })
    }

    @Test fun tiedWithAnotherMallIsNotHot() {
        // 다른 쇼핑몰도 같은 값에 파는 딜(2위와 차이 없음)은 평균보다 싸도 핫딜이 아니다
        val tie = index.deals.first { (it.vsBest ?: 99.0) < 1.0 && (it.rate ?: 0.0) >= 10 }
        assertFalse(isHot(tie, Settings()))
        assertTrue(isHot(tie, Settings(onlyCheapest = false)))
        val clear = index.deals.first { (it.vsBest ?: 0.0) >= 1.0 && (it.rate ?: 0.0) >= 10 }
        assertTrue(isHot(clear, Settings()))
    }

    @Test fun tiers() {
        assertEquals(Tier.LEGEND, tier(30.0))
        assertEquals(Tier.HOT, tier(15.0))
        assertEquals(Tier.GOOD, tier(10.0))
        assertEquals(null, tier(null))
    }

    @Test fun toleratesUnknownAndMissingFields() {
        val data = Repository.parse("""{"version":3,"future":{"x":1},"deals":[{"id":"a","price":1000,"rate":40,"referencePrice":2000,"newField":true}],"products":{"1":{"pcode":"1","history":[["2026-10-01",1000,""]]}}}""")
        val ix = Index(data)
        assertEquals(1, ix.deals.size)
        assertEquals(null, ix.products["1"]!!.historyPoints().first().average)
    }
}
