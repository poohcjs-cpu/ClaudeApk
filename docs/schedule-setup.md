# 정확히 1시간마다 수집하기 (cron-job.org)

GitHub의 예약 실행(schedule)은 붐빌 때 몇 시간씩 건너뛰어요. 그래서 무료 예약 서비스 **cron-job.org**가 1시간마다 GitHub에 "지금 수집해"라고 요청하게 해요. GitHub의 예약 실행도 그대로 두어서, 둘 중 하나만 돌아도 수집돼요.

필요한 것: GitHub 토큰 1개(이 저장소의 Actions만 실행할 수 있는 좁은 권한), cron-job.org 계정. 약 5분.

## 1. GitHub 토큰 만들기

1. https://github.com/settings/personal-access-tokens/new 를 열어요(GitHub 로그인 상태).
2. **Token name**: `hotdeal-collect`
3. **Expiration**: `Custom` → 1년 뒤 날짜. 만료 일주일 전에 GitHub가 이메일로 알려 줘요.
4. **Repository access**: `Only select repositories` → `poohcjs-cpu/ClaudeApk` 하나만 고르기.
5. **Permissions** → **Repository permissions** → **Actions**: `Read and write`
   (Metadata: Read-only는 자동으로 들어가요. 다른 권한은 건드리지 마세요.)
6. 맨 아래 **Generate token** → 나온 토큰(`github_pat_…`)을 **복사**해 두세요. 이 화면을 닫으면 다시 볼 수 없어요.

이 토큰으로는 ClaudeApk 저장소의 Actions 실행만 할 수 있어요. 코드 수정이나 다른 저장소 접근은 안 돼요.

## 2. cron-job.org에 예약 만들기

1. https://console.cron-job.org/signup 에서 무료 가입 후 로그인.
2. **CREATE CRONJOB** 누르기.
3. **COMMON** 탭
   - **Title**: `핫딜 수집`
   - **URL**: `https://api.github.com/repos/poohcjs-cpu/ClaudeApk/actions/workflows/collect.yml/dispatches`
   - **Execution schedule**: `Every hour` 고르고, 분은 `23` (정시를 피하면 덜 밀려요)
4. **ADVANCED** 탭
   - **Request method**: `POST`
   - **Headers**: 아래 4개를 하나씩 추가 (`ADD HEADER`)

     | Key | Value |
     |---|---|
     | `Authorization` | `Bearer 복사한토큰` (Bearer 뒤에 한 칸 띄우기) |
     | `Accept` | `application/vnd.github+json` |
     | `X-GitHub-Api-Version` | `2022-11-28` |
     | `Content-Type` | `application/json` |

   - **Request body**: `{"ref":"android-app"}`
   - **Treat redirects with HTTP 3xx status code as success**: 꺼 두기(기본값)
5. **TEST RUN** → 응답 코드가 **204**면 성공이에요. 바로 **CREATE** 눌러 저장.

## 3. 확인

- https://github.com/poohcjs-cpu/ClaudeApk/actions/workflows/collect.yml 에 `workflow_dispatch`로 실행된 기록이 생기면 돼요.
- 앱 핫딜 탭 오른쪽 위 "○분 전 업데이트"가 1시간 안쪽으로 유지돼요. 3시간이 넘으면 ⚠ 표시가 떠요.

## 문제가 생기면

| 응답 코드 | 뜻 | 할 일 |
|---|---|---|
| 401 | 토큰이 틀렸거나 만료됨 | `Authorization` 값이 `Bearer ` + 토큰인지 확인, 만료됐으면 1번을 다시 |
| 403 / 404 | 토큰 권한 부족 또는 저장소 선택 누락 | 1번 4·5단계(ClaudeApk 선택, Actions: Read and write) 확인 |
| 422 | 브랜치 이름이 틀림 | body가 `{"ref":"android-app"}`인지 확인 |

토큰은 cron-job.org와 이 문서 말고 다른 곳(채팅, 저장소 파일)에 붙여 넣지 마세요.
