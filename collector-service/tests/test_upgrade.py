"""2026-11 수집기 보완: 다나와 새 검색 화면, 카드 할인가 제외, 제목 가격 읽기, 본문 가격, 재시도 간격."""
import unittest
from pathlib import Path

from collector import danawa
from collector.collect import Run
from collector.community import extract_body_price
from collector.compare import match_deal
from collector.textnorm import parse_title

FIX = Path(__file__).parent / "fixtures"


def read(name: str) -> str:
    return (FIX / name).read_text(encoding="utf-8")


class DanawaNextSearchTest(unittest.TestCase):
    def setUp(self):
        self.cands, self.strategy = danawa.parse_search(read("danawa_search_next.html"))

    def test_reads_embedded_product_data(self):
        self.assertEqual(self.strategy, "next")
        self.assertEqual([c.pcode for c in self.cands], ["18297002", "18518834", "20600144"])  # 가격 없는 상품 제외
        first = self.cands[0]
        self.assertEqual(first.name, "삼성전자 990 PRO M.2 NVMe")  # <b> 강조 태그 제거, '이미지 보기' 아님
        self.assertEqual(first.price, 386250)
        self.assertEqual(sorted(o.label for o in first.options), ["1TB", "2TB", "4TB"])
        self.assertIn("overseas", self.cands[1].variant)  # 병행수입

    def test_match_picks_capacity_option_and_base_model(self):
        tp = parse_title("[11번가] 삼성 990 PRO 2TB (759,000원/무료)")
        m = match_deal(tp.name, tp.price, self.cands)
        self.assertIsNotNone(m)
        self.assertEqual(m.candidate.pcode, "18297002")  # 히트싱크 모델이 아닌 기본 모델
        self.assertEqual(m.option.label, "2TB")

    def test_search_url_uses_new_address(self):
        self.assertTrue(danawa.search_url("990 PRO").startswith("https://search.danawa.com/?query="))


class DanawaProductTest(unittest.TestCase):
    def test_card_only_price_and_delivery_text(self):
        page = danawa.parse_product(read("danawa_product_membership.html"), "18297722")
        malls = {o.mall: o for o in page.offers}
        self.assertEqual(malls["이마트몰"].price, 807293)  # '현대카드 결제 시' 757,293원은 판매가가 아님
        self.assertEqual(malls["이마트몰"].shipping_fee, 2900)  # 빠른배송 말풍선이 아니라 배송비 칸
        self.assertEqual(malls["11번가"].shipping_fee, 0)
        self.assertEqual(malls["컴오아시스"].price, 801990)


class TitlePriceTest(unittest.TestCase):
    def test_bare_slash_prices(self):
        cases = {
            "[네이버] 스위치 하늘의 궤적 the 1ST + 2nd / 151,700": (151700, None),
            "[네이버] NS2 요시의 도감 외 / 47,280 / 3,000": (47280, 3000),
            "[겜우리] PS5 캐슬바니아 미드나이트 에디션/46,000/2500": (46000, 2500),
            "[11번가] LG 그램 16 2025 / 1,299,000원 / 무료": (1299000, 0),
        }
        for title, (price, fee) in cases.items():
            tp = parse_title(title)
            self.assertEqual((tp.price, tp.shipping_fee), (price, fee), title)
            self.assertNotRegex(tp.name, r"\d{2},\d{3}")

    def test_small_bare_number_is_not_price(self):
        self.assertIsNone(parse_title("[쿠팡] 생수 / 500").price)

    def test_free_apps_are_digital(self):
        for title in ("[WearOS] LX029 Car Luxury Watch Face 외 일시 무료", "iOS 일시무료 Calm Loop"):
            self.assertIn("digital", parse_title(title).flags, title)


class BodyPriceTest(unittest.TestCase):
    def test_labeled_price(self):
        html = '<meta name="description" content="가격: 1,890,000원 배송: 무료 / 쿠폰 5,000원">'
        self.assertEqual(extract_body_price(html), ("1,890,000원", "무료"))

    def test_single_amount(self):
        html = '<div class="post_article">하이마트 온라인에서 1,890,000원 입니다.</div>'
        self.assertEqual(extract_body_price(html)[0], "1,890,000원")

    def test_ambiguous_amounts_are_ignored(self):
        html = '<meta name="description" content="쿠폰 5,000원 받고 39,000원">'
        self.assertIsNone(extract_body_price(html))


class BackoffTest(unittest.TestCase):
    def test_retry_interval_grows_and_caps(self):
        st = {}
        self.assertEqual([Run._backoff(st, 6) for _ in range(5)], [6, 12, 24, 48, 48])


if __name__ == "__main__":
    unittest.main()
