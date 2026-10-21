# 카드 연결 및 월별 실적 자동 재계산 검증

검증일: 2026-10-16 (Asia/Seoul)
프로젝트: C:\Users\SSAFY\Documents\card-advisor

이번 요청의 구현과 앱→API→PostgreSQL 검증을 가상 카드 규칙으로 완료했습니다. 실제 카드 약관·금융 연결 검증은 포함하지 않습니다.

## 구현 결과
- 수동 구매 때 사용 카드를 직접 선택합니다. 추천된 카드를 자동 선택하지 않습니다.
- 원거래에 카드 ID, 카드 승인액, 실적 인정 여부와 가상 규칙 버전을 저장합니다. 전체 지출액은 포인트를 제외하고, 카드 승인액은 포인트와 충전머니를 모두 제외합니다.
- 거래의 연결 카드를 수정하면 기존 카드와 새 카드의 순실적이 함께 재계산됩니다. 본인 카드만 연결할 수 있습니다.
- 취소에는 전체 환불액과 카드 환불액을 나눠 저장합니다. 순수 카드 거래에서는 같은 금액으로 추론할 수 있지만, 혼합 결제의 카드 환불액은 명시해야 합니다.
- 가상 규칙 SYNTHETIC_V1은 카드 승인−카드 환불을 원구매 월 실적에 반영합니다. 실제 상품의 취소월·할부 정책을 대신하는 규칙이 아닙니다.
- 월별 미기록 기존 실적 + 해당 카드에 연결된 월 거래 실적을 합산합니다. 기존 실적 입력에는 이미 원장에 등록된 거래를 중복 포함하면 안 됩니다.
- 현재 혜택 자격은 별도 월별 스냅샷으로 보존합니다. 다음 달 기준을 달성해도 이번 결제 혜택 자격을 승격하지 않습니다. 미래 혜택을 확정 수익으로 합산하지 않습니다.
- 다음 월의 스냅샷이 없거나 실적 인정 여부·기초 실적이 미확인이면 0으로 대체하지 않습니다.
- 추천 조회는 원장을 변경하지 않습니다. 거래·취소·연결·월별 상태 변경 시 이전 추천이 무효화되며, 계산 월이 달라진 추천도 재사용하지 않습니다.
- 적용된 V1은 수정하지 않고 V2·V3를 추가했습니다. 이전 카드 미연결 원장이나 날짜 없는 카드 상태를 임의의 카드/월에 귀속하지 않습니다.

## 실제 화면·DB 대조
최종 실행: be278fdf-3b8a-4074-b7c1-640f880464ba
환경: Android 16 emulator-5554, 실제 Spring API, PostgreSQL 17.6.

| 단계 | 다음 달용 B 카드 실적 | 이번 혜택 자격 |
|---|---:|---|
| 미기록 기존 실적 | 270,000원 | 충족 유지 |
| B 카드로 50,000원 구매 | 320,000원 | 충족 유지 |
| 카드로 20,000원 부분 환불 | 300,000원 | 충족 유지 |

DB에서 B 카드 연결, 승인50,000, 카드 환불20,000, 기초270,000과 현재 자격 true를 직접 확인했습니다. 앱의 실적300,000과 일치했습니다. 순지출은30,000원이며 재로그인 후 유지됐습니다. 추가50,000원 구매 추천의 전체 예산 잔액40,000원도 대조했습니다.

별도 실제 API 검사에서는 현재 자격 false인 카드가 다음 달 실적 기준을 넘어도 이번 혜택이0원이고 현재 자격 false가 유지됨을 확인했습니다.

## 검사 결과
- 계산 단위 테스트: 25개 통과(기존 추천16 + 신규 실적9).
- 실제 API: 27개 검사 통과(기존16 + 실적10 + 월 경계1).
- Android 화면 통합 시나리오: 최종1개 통과,18.321초. 최종 서버 수정 전에도1회 통과했으나 별개의 기능 검사 개수로 합산하지 않았습니다.
- 앱·테스트 APK·백엔드 JAR 빌드 성공. V1/V2/V3 마이그레이션 적용.
- 합성 테스트 계정은 검증 후 삭제했고 앱을 로그인 화면으로 열었습니다.

검사한 경계: 실적 목표−1/목표, 부분/전체 취소, 예산 제외와 실적 인정의 독립성, 다른 카드·다른 사용자 격리, 카드 재연결, 이전 월 구매의 취소, 미래 월 미확인 상태, 혼합 결제 카드 환불, 실적 제외/미확인 규칙, 미확인 기초값, 데이터 변경 및 월 변경 추천 무효화.

현재 자격을 공식 거래에서 산출한 검증이 아닙니다. 가상 월별 스냅샷과 실제 연결 거래의 계산을 검증했습니다. 혜택 잔여한도 자동 소진/복원은 이번 범위에 포함하지 않았습니다.

## 재현 방법
프로젝트에서 PostgreSQL과 백엔드가 실행 중일 때:

    python scripts/api_smoke.py
    python scripts/card_performance_smoke.py
    python scripts/recommendation_month_smoke.py
    ./scripts/android.ps1 ui-test

마지막 명령은 emulator-5554를 사용합니다. 월 경계 검사는 방금 생성한 합성 계정의 추천 한 건만 이전 계산 월로 변경하는 테스트 준비 작업을 수행하고 계정을 삭제합니다. 화면 검사는 성공 후 해당 합성 계정을 정리하고 실패하면 실행 ID를 남깁니다.

주요 코드:
- backend/src/main/java/dev/cardadvisor/domain/PerformanceCalculator.java
- backend/src/main/java/dev/cardadvisor/api/AdvisorService.java
- backend/src/main/resources/db/migration/V2__card_month_performance.sql 및 V3__recommendation_month.sql
- android/app/src/main/java/dev/cardadvisor/android/MainActivity.kt
- android/app/src/androidTest/java/dev/cardadvisor/android/LocalFlowTest.kt
- docs/api/openapi.json

근거:
- .run/card-performance-build.log, card-performance-package-final.log, card-performance-android-build.log
- .run/api-smoke.json, card-performance-api.json, recommendation-month.json
- .run/android-ui/be278fdf-3b8a-4074-b7c1-640f880464ba/verification.json 및 화면 PNG

## 전체 프로젝트 상태
이번 기능 구현과 로컬 검증은 완료했습니다. 실제 카드 규칙·금융 연동·물리 기기·쇼핑몰 감지·AI·배포 준비·공개 배포·스토어 승인은 미완료입니다.
다음 행동은 월별 미기록 실적·현재 혜택 자격의 Android 수정 화면 및 버전 충돌 검증입니다.
