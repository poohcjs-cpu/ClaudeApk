"""커뮤니티 핫딜 가격 기록과 쇼핑몰 주소록.

  data/archive/YYYY-MM.json   그달에 올라온 커뮤니티 딜 (가격이 있는 글만, 오래 보관)
  data/malls.json             딜 글에 걸린 링크로 알게 된 브랜드 공식몰·자사몰 주소

앱은 사용자가 검색할 때 이 기록에서 같은 상품의 지난 핫딜 가격을 찾아
'지금 사도 될까?'를 판단하고, 주소록에 있는 공식몰은 직접 검색해 본다.
community.json은 72시간만 보관하므로, 기록은 여기에 따로 쌓는다.
"""
from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

from . import store

ARCHIVE_VERSION = 1


def _month_of(iso: str | None) -> str | None:
    t = store.parse_time(iso)
    return f"{t:%Y-%m}" if t else None


def archive_entry(post: dict) -> dict | None:
    """community.json의 글 하나 → 기록 한 줄 (가격이 없거나 원화가 아니면 None)."""
    price = post.get("price")
    if not price or price <= 0 or (post.get("currency") not in (None, "KRW")):
        return None
    flags = set(post.get("flags") or [])
    if flags & {"non_product", "digital", "free"}:
        return None
    e = {
        "k": post["key"],
        "t": (post.get("postedAt") or post.get("firstSeen") or "")[:16],
        "s": post["source"],
        "n": (post.get("name") or post.get("title") or "").strip()[:120],
        "m": post.get("mall") or "",
        "p": int(price),
        "u": post.get("url") or "",
    }
    if post.get("shippingFee") is not None:
        e["f"] = int(post["shippingFee"])
    if post.get("shipping"):
        e["sh"] = str(post["shipping"])[:20]
    for src, dst in (("votes", "v"), ("comments", "c")):
        if post.get(src) is not None:
            e[dst] = post[src]
    if post.get("ended"):
        e["e"] = 1
    if post.get("cat") and post["cat"] != "etc":
        e["cat"] = post["cat"]
    if post.get("shop"):
        e["shop"] = post["shop"]
    return e


def update_archive(root: Path, community: dict, now: datetime, keep_months: int = 24) -> int:
    """지금 가진 글을 달별 기록 파일에 넣거나 갱신한다 (추천 수·종료 여부가 바뀔 수 있어서). 넣은 줄 수를 돌려준다."""
    by_month: dict[str, list[dict]] = {}
    for post in community.values():
        e = archive_entry(post)
        month = _month_of(post.get("firstSeen") or post.get("postedAt"))
        if e and month:
            by_month.setdefault(month, []).append(e)
    folder = root / "data" / "archive"
    count = 0
    for month, entries in by_month.items():
        path = folder / f"{month}.json"
        old = store.read_json(path, {}).get("items", [])
        items = {it["k"]: it for it in old if isinstance(it, dict) and it.get("k")}
        for e in entries:
            items[e["k"]] = {**items.get(e["k"], {}), **e}
            count += 1
        ordered = sorted(items.values(), key=lambda it: it.get("t", ""))
        store.write_json(path, {"version": ARCHIVE_VERSION, "month": month, "updatedAt": now.isoformat(),
                                "items": ordered}, compact=True)
    # 오래된 달은 지운다
    if folder.exists():
        cutoff = (now.year * 12 + now.month - 1) - keep_months
        for f in folder.glob("*.json"):
            m = re.fullmatch(r"(\d{4})-(\d{2})", f.stem)
            if m and int(m.group(1)) * 12 + int(m.group(2)) - 1 < cutoff:
                f.unlink()
    return count


# ── 쇼핑몰 주소록 ───────────────────────────────────────────

# 쇼핑몰 솔루션별 상품 주소 모양. 앱은 이 솔루션의 검색 화면을 읽을 수 있다 (지금은 카페24만).
_PLATFORMS = [
    ("cafe24", re.compile(r"/product/detail\.html|/product/[^/?#]+/\d+(?:/|$)|/surl/[OP]/\d+")),
    ("godomall", re.compile(r"/goods/goods_view\.php")),
    ("makeshop", re.compile(r"/shop/shopdetail\.html")),
    ("imweb", re.compile(r"/shop_view/|[?&]idx=\d+")),
]
# 큰 오픈마켓·포털·커뮤니티는 주소록에 넣지 않는다 (공식몰을 찾으려는 것)
_SKIP_HOSTS = (
    "gmarket.co.kr", "auction.co.kr", "11st.co.kr", "coupang.com", "ssg.com", "lotteon.com", "kurly.com", "ohou.se",
    "musinsa.com", "29cm.co.kr", "wconcept.co.kr", "tmon.co.kr", "wemakeprice.com", "interpark.com", "gsshop.com",
    "cjonstyle.com", "hmall.com", "akmall.com", "nsmall.com", "homeplus.co.kr", "emart.com", "lotteimall.com",
    "naver.com", "naver.me", "kakao.com", "toss.im", "tossshopping.com", "aliexpress.com", "amazon.com", "ebay.com",
    "apple.com", "steampowered.com", "epicgames.com", "google.com", "youtube.com", "youtu.be", "instagram.com",
    "facebook.com", "twitter.com", "x.com", "ruliweb.com", "clien.net", "ppomppu.co.kr", "damoang.net", "danawa.com",
    "enuri.com", "bit.ly", "han.gl", "me2.do", "vo.la", "link.coupang.com", "temu.com", "qoo10.com",
)
_STOP = {"무료배송", "무배", "무료", "배송", "특가", "할인", "최저가", "공식몰", "공홈", "자사몰", "쿠폰", "적립", "세트", "증정",
         "단독", "한정", "행사", "기획", "추천", "신상", "정품", "국내", "해외", "오늘", "타임딜", "원", "개입"}


def platform_of(url: str) -> str | None:
    p = urlparse(url)
    path = p.path + ("?" + p.query if p.query else "")
    for name, pat in _PLATFORMS:
        if pat.search(path):
            return name
    if p.hostname and p.hostname.endswith(".cafe24.com"):
        return "cafe24"
    return None


def mall_host(url: str) -> str | None:
    host = (urlparse(url).hostname or "").lower()
    if not host or "." not in host:
        return None
    if any(host == h or host.endswith("." + h) for h in _SKIP_HOSTS):
        return None
    if host.startswith("m.") and host.count(".") >= 2:
        host = host[2:]  # 모바일 주소는 같은 쇼핑몰로
    return host


def keywords_of(text: str) -> list[str]:
    words = re.findall(r"[가-힣A-Za-z][가-힣A-Za-z0-9]+", text or "")
    out = []
    for w in words:
        if len(w) < 2 or w in _STOP or re.fullmatch(r"\d+[A-Za-z가-힣]{0,2}", w):
            continue
        if w not in out:
            out.append(w)
    return out[:8]


def shop_links(html: str, base_host: str) -> list[str]:
    """글 페이지에서 본문(또는 루리웹의 '원본 링크')에 걸린 바깥 쇼핑몰 주소."""
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "html.parser")
    out: list[str] = []
    for sel in (".source_url", ".post_article", ".post_content", "article", "#bo_v_con", ".view-content", ".xe_content",
                ".view_content"):
        for box in soup.select(sel):
            for a in box.select("a[href^=http]"):
                href = a.get("href", "").strip()
                host = urlparse(href).hostname or ""
                if host and not host.endswith(base_host) and href not in out:
                    out.append(href)
        if out:
            break
    return out[:12]


def learn_mall(malls: dict, url: str, post: dict, now_iso: str) -> str | None:
    """링크가 공식몰·자사몰 상품이면 주소록에 넣는다. 넣은(또는 갱신한) 주소를 돌려준다."""
    host = mall_host(url)
    platform = platform_of(url)
    if not host or not platform:
        return None
    m = malls.setdefault(host, {"name": "", "platform": platform, "keywords": [], "count": 0, "firstSeen": now_iso})
    name = (post.get("mall") or "").strip()
    if name and name not in ("자사몰", "공홈", "공식몰", "공식홈"):
        m["name"] = m.get("name") or name
    kws = m.get("keywords", [])
    for w in keywords_of(f"{name} {post.get('name') or post.get('title') or ''}"):
        if w in kws:
            kws.remove(w)
        kws.insert(0, w)
    m["keywords"] = kws[:40]
    m["count"] = int(m.get("count") or 0) + 1
    m["lastSeen"] = now_iso
    m["example"] = url[:300]
    m["platform"] = platform
    return host
