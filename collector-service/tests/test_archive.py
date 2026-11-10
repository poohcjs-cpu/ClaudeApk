"""커뮤니티 핫딜 가격 기록·쇼핑몰 주소록 테스트."""
import json
from datetime import datetime, timedelta, timezone

from collector import archive

KST = timezone(timedelta(hours=9))


def _post(key, price=45000, mall="건봉국밥", name="순천건봉국밥 4+1팩", seen="2026-11-10T21:00:00+09:00", **kw):
    src, pid = key.split(":")
    return {"key": key, "source": src, "id": pid, "url": f"https://example.com/{pid}", "title": f"[{mall}] {name}",
            "name": name, "mall": mall, "price": price, "currency": "KRW", "shipping": "3,500원", "shippingFee": 3500,
            "postedAt": seen, "firstSeen": seen, "votes": 3, "flags": [], **kw}


def test_entry_skips_posts_without_price_or_digital():
    assert archive.archive_entry(_post("ppomppu:1", price=None)) is None
    assert archive.archive_entry(_post("ppomppu:2", flags=["digital"])) is None
    assert archive.archive_entry(_post("ppomppu:3", currency="USD")) is None
    e = archive.archive_entry(_post("ppomppu:4"))
    assert e["p"] == 45000 and e["f"] == 3500 and e["m"] == "건봉국밥" and e["t"] == "2026-11-10T21:00"


def test_update_archive_upserts_and_prunes(tmp_path):
    now = datetime(2026, 11, 10, 22, tzinfo=KST)
    com = {"ppomppu:1": _post("ppomppu:1"), "clien:2": _post("clien:2", price=54000, seen="2026-10-31T23:00:00+09:00")}
    assert archive.update_archive(tmp_path, com, now) == 2
    nov = json.loads((tmp_path / "data/archive/2026-11.json").read_text())
    assert [it["k"] for it in nov["items"]] == ["ppomppu:1"]
    assert (tmp_path / "data/archive/2026-10.json").exists()
    # 추천 수가 바뀌면 같은 줄을 고친다
    com["ppomppu:1"]["votes"] = 30
    com["ppomppu:1"]["ended"] = True
    archive.update_archive(tmp_path, {"ppomppu:1": com["ppomppu:1"]}, now)
    nov = json.loads((tmp_path / "data/archive/2026-11.json").read_text())
    assert len(nov["items"]) == 1 and nov["items"][0]["v"] == 30 and nov["items"][0]["e"] == 1
    # 보관 기간이 지난 달은 지운다
    old = tmp_path / "data/archive/2024-01.json"
    old.write_text("{}")
    archive.update_archive(tmp_path, {}, now, keep_months=12)
    assert not old.exists() and (tmp_path / "data/archive/2026-10.json").exists()


def test_platform_and_host():
    assert archive.platform_of("https://gunbong1987.com/product/50만팩-판매돌파/20/category/26/display/1/") == "cafe24"
    assert archive.platform_of("https://sofrano.com/product/detail.html?product_no=4068&cate_no=326") == "cafe24"
    assert archive.platform_of("https://shop.example.co.kr/goods/goods_view.php?goodsNo=1000") == "godomall"
    assert archive.platform_of("https://www.example.com/about") is None
    assert archive.mall_host("https://m.gunbong1987.com/product/x/20/") == "gunbong1987.com"
    assert archive.mall_host("https://item.gmarket.co.kr/Item?goodscode=1") is None
    assert archive.mall_host("https://smartstore.naver.com/a/products/1") is None


def test_learn_mall_and_links():
    html = """<html><body><div class="source_url">원본 <a href="https://sofrano.com/product/detail.html?product_no=4068">링크</a></div>
      <div class="view_content"><a href="https://bbs.ruliweb.com/hashtag/x">태그</a>
      <a href="https://smartstore.naver.com/a/products/1">다른 곳</a></div></body></html>"""
    links = archive.shop_links(html, "ruliweb.com")
    assert links == ["https://sofrano.com/product/detail.html?product_no=4068"]
    malls = {}
    post = _post("ruliweb:9", mall="소프라노몰", name="스위치 파이널 판타지 한정판")
    assert archive.learn_mall(malls, links[0], post, "2026-11-10T22:00") == "sofrano.com"
    assert archive.learn_mall(malls, "https://smartstore.naver.com/a/products/1", post, "x") is None
    m = malls["sofrano.com"]
    assert m["name"] == "소프라노몰" and m["platform"] == "cafe24" and "소프라노몰" in m["keywords"] and m["count"] == 1
