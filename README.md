# 핫딜 레이더 (안드로이드 앱)

[hotdeal-radar](https://github.com/poohcjs-cpu/hotdeal-radar) 수집기가 30분마다 만드는 핫딜 데이터를 보여 주는 안드로이드 앱이에요.
커뮤니티 핫딜 글을 다나와 쇼핑몰 가격과 비교해서, **쇼핑몰 평균가보다 내 기준 이상 싼 딜**만 골라 보여 주고 알림을 보내요.

```
collector-service (이 저장소의 GitHub Actions, 30분마다)        앱
  커뮤니티 글 수집 → 다나와 가격 비교      →     deals.json 받기 → 화면 · 알림
  → data 브랜치의 deals.json
```

수집기는 [hotdeal-radar](https://github.com/poohcjs-cpu/hotdeal-radar)의 수집기를 보완한 판이에요(`collector-service/`).
다나와 새 검색 화면 대응, 카드 조건가 제외, '원' 없는 가격·본문 가격 읽기, 막힌 출처 재시도 간격 늘리기가 들어 있어요.
`.github/workflows/collect.yml`이 30분마다 돌려 결과를 `data` 브랜치에 올리고, 앱은
`https://raw.githubusercontent.com/poohcjs-cpu/ClaudeApk/data/deals.json`을 받아요. 받지 못하면 hotdeal-radar 웹앱의 데이터로 다시 시도해요.

앱은 사이트를 직접 긁지 않아요. 수집기가 만든 `deals.json` 한 파일만 받아요. 그래서 배터리·데이터를 거의 쓰지 않고, 사이트에 부담을 주지도 않아요.

## 설치

1. 휴대폰에서 [최신 APK](https://github.com/poohcjs-cpu/ClaudeApk/releases/download/latest/hotdeal-radar.apk)를 받아요.
2. 받은 파일을 열고, "출처를 알 수 없는 앱 설치"를 허용해요.
3. 처음 열면 알림 권한을 물어요. 허용하면 새 핫딜을 알려 드려요.

새 버전이 나오면 같은 주소에서 받아 그대로 설치하면 돼요(찜·설정은 유지돼요).

## 핫딜 기준 (기본값)

**쇼핑몰 평균가보다 10% 이상 싸고, 다른 어느 쇼핑몰보다도 싼 딜**을 핫딜로 보여 줘요. 따로 설정할 필요 없어요.

실제 수집 데이터(2026-11)로 정한 값이에요.

- 다나와에서 가장 싼 판매처는 **평소에도 나머지 판매처 평균보다 10% 안팎 싸요**(중간값 10.3%). 그래서 '평균보다 5%'는 평소 최저가 수준이라 거의 모든 제품(20개 중 18개)이 걸려요.
- 반대로 **'다른 곳 최저가보다 5%'** 싸면 정말 싼 거예요. 그래서 카드마다 "다른 곳 최저가보다 N% 쌈"을 함께 보여 줘요.
- 평균보다 17~19% 싸 보여도 다른 쇼핑몰이 같은 값에 팔면 특별한 딜이 아니라서 핫딜에서 빼요.
- 30% 이상 **역대급**, 15% 이상 **핫딜**, 그 아래는 **알뜰**로 표시해요. 알림은 15% 이상일 때만 보내요.
- 첫 버전의 기본값(30%)을 그대로 쓰던 기기는 업데이트하면 새 기본값으로 바뀌어요. 직접 바꾼 값은 그대로 둬요.

## 화면

| 탭 | 하는 일 |
|---|---|
| 핫딜 | 오늘의 핫딜 요약, 할인율 TOP 5, 내 기준(기본 10%) 이상 싸고 다른 곳보다도 싼 딜. 제품군·커뮤니티/가격비교 발굴로 거르고, 할인율·새로 뜬 순·반응순·가격으로 정렬. 지난 방문 뒤 새로 뜬 딜에 NEW |
| 커뮤니티 | 게시판 글 전부와 비교 결과(‘평균보다 36%↓’, ‘같은 제품 못 찾음’ 등). 게시판별 필터, 비교된 글만, 끝난 딜 숨기기 |
| 상세 | 딜 가격과 기준가, 판정 문장, **쇼핑몰별 가격표**, 최저가·평균가 **그래프**, 올라온 게시판들, 같은 제품의 다른 글, 어떻게 비교했는지 |
| 검색 | 지켜보는 제품과 커뮤니티 글에서 찾기. 없으면 다나와로 바로 연결 |
| 찜 | 제품 단위로 찜하고 목표가 설정. 진행 중인 딜과 다나와 최저가 중 싼 값으로 추적 |
| 설정 | 핫딜 기준(5~70%), 관심 제품군, 가격 확인 필요·끝난 딜 숨기기, 알림 기준, 출처별 켜기·끄기와 수집 상태, 쇼핑몰 제외, 데이터 주소 |

웹앱(GitHub Pages)과 같은 판정 규칙을 써요.

## 알림

- 30분마다 `deals.json`을 받아, **아직 알리지 않은** 딜 중 알림 기준(기본 15%) 이상이고 다른 곳보다도 싼 것을 알려요.
- 가격 확인 필요(평균보다 60% 넘게 쌈)·끝난 딜·제외한 쇼핑몰은 알리지 않아요.
- 처음 켰을 때 이미 떠 있던 딜로 알림이 쏟아지지 않도록, 첫 확인은 ‘본 것’으로만 표시해요.
- 찜한 제품이 목표가 아래로 내려오면 알려요. 같은 가격으로 반복해서 알리지 않아요.
- 안드로이드 배터리 절약 때문에 몇 분~수십 분 늦을 수 있어요.

## 개발

- Kotlin · Jetpack Compose(Material 3) · WorkManager · kotlinx.serialization · OkHttp · Coil
- 글꼴: HDR Sans — [Pretendard](https://github.com/orioncactus/pretendard)의 한글 2,350자(KS X 1001) 부분집합, 가변 굵기. SIL OFL 1.1의 예약 이름 규정에 따라 이름을 바꿨어요(`app/src/main/assets/licenses/`)
- 화면 그림: `./gradlew testDebugUnitTest -Pscreenshots --tests '*ScreenshotTest*'` → `app/build/screenshots/` (기기 없이 Robolectric으로 그려요)
- minSdk 26(Android 8.0), targetSdk 36
- 구조

```
app/src/main/java/io/github/poohcjs/hotdealradar/
  data/Model.kt          deals.json 형식 (모르는 칸은 무시, 모든 칸 기본값)
  data/Logic.kt          핫딜 판정·필터·정렬·판정 문장·목표가 (안드로이드 의존 없음, 단위 테스트 대상)
  data/Repository.kt     deals.json 받기 + 파일 캐시(오프라인)
  data/Prefs.kt          설정·찜·알림 기록 (기기에만 저장)
  work/DealCheckWorker.kt 30분마다 확인해 알림
  ui/                    화면 (핫딜·커뮤니티·검색·찜·설정·상세)
```

```bash
./gradlew testDebugUnitTest assembleDebug
```

단위 테스트는 수집기가 실제로 만든 `deals.json`(`app/src/test/resources/deals_sample.json`)으로 판정 규칙을 확인해요.

### 자동 빌드

`.github/workflows/android.yml`이 커밋마다 테스트하고 APK를 만들어요. `main`에 올리면 [latest 릴리스](https://github.com/poohcjs-cpu/ClaudeApk/releases/tag/latest)의 APK를 바꿔 끼워요.

### 서명 키

안드로이드는 **처음 설치한 앱과 같은 키로 서명된 APK만** 업데이트로 받아 줘요. 그래서 매번 같은 키로 서명해요.

- 기본: 저장소의 공용 키 `signing/shared-debug.keystore`(비밀번호 `android`). 개인용으로는 충분해요. 다만 공개 저장소라 누구나 이 키로 서명할 수 있으니, 다른 사람에게 배포할 거라면 아래처럼 개인 키를 쓰세요.
- 개인 키: 저장소 **Settings → Secrets and variables → Actions**에 `SIGNING_KEYSTORE_BASE64`(keystore 파일을 base64로), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`를 넣으면 그 키로 서명해요. 키를 바꾸면 한 번은 앱을 지우고 다시 설치해야 해요.
