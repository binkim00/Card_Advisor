# 카드 어드바이저

현재는 전체 프로젝트의 첫 구현 단위인 **가상 카드 기반 수동 입력 → 추천 → 예산 → 수동 거래/취소** 흐름입니다. 실제 금융 연동·실제 카드 규칙·쇼핑몰 감지·AI·배포는 아직 완료되지 않았습니다.

## 로컬 실행

1. Windows에서 Java17+, Maven3.6.3+, Docker Desktop을 준비합니다. 설치된 JDK21/Maven 캐시는 scripts가 찾아 사용합니다.
2. 프로젝트 루트에서 `./scripts/configure.ps1` 실행. 무작위 로컬 DB 비밀번호를 .env에 생성하며 출력하지 않습니다.
3. `docker compose up -d db`로 프로젝트 전용 PostgreSQL 실행.
4. `./scripts/backend.ps1 test`, `./scripts/backend.ps1 run` 실행. API는 http://127.0.0.1:18080/v1/health.
5. `./scripts/android.ps1 build`로 Android 디버그 APK 빌드. Android SDK35, Gradle8.14.3 필요.
6. USB 디버깅을 허용한 기기를 연결하고 `adb reverse tcp:18080 tcp:18080` 후 APK를 설치합니다. 계정은 앱에서 직접 생성합니다. '가상 데이터 준비' → '카드 비교'로 진행합니다.

공개 배포용 설정이 아니며 release 앱은 HTTP를 허용하지 않습니다. 실제 금융 비밀값은 필요하지 않습니다. .env와 금융정보를 저장소에 올리지 마세요.

진행 근거와 다음 작업은 docs/STATE.md, 전체 명세 스냅샷은 docs/specifications에 있습니다.


## Android 화면 통합 검증
백엔드·PostgreSQL·emulator-5554를 실행한 뒤 ./scripts/android.ps1 ui-test를 실행합니다. 실제 앱 입력과 API·DB를 검증하며 성공 시 해당 합성 계정만 삭제합니다. 실패 시 실행 ID를 남겨 진단할 수 있습니다. 상세 근거는 docs/verification/2026-10-16-android-ui.md에 있습니다.

## 카드별 실적 확인
가계부에서 사용 카드를 먼저 선택한 뒤 구매를 기록합니다. 기존 미기록 실적과 연결 거래를 합산하며 부분 취소 시 카드 환불액을 차감합니다. 혼합 결제는 취소액 중 카드 환불액을 따로 입력합니다. 실제 카드 약관에 적용한 값이 아니라 가상 규칙 추정입니다. 실제 API 검사: python scripts/card_performance_smoke.py. 월 경계 검사: python scripts/recommendation_month_smoke.py. 상세: docs/verification/2026-10-16-card-performance.md.

## 월별 실적·현재 자격 수정
로그인 → 설정 → 가상 데이터 준비 → 월별 실적·자격 수정에서 카드와 월을 조회합니다. 앱에 연결한 거래를 제외한 미기록 실적, 현재 혜택 자격, 사용자 확인/추정 출처와 확인일을 입력합니다. 값이 없으면 미확인을 선택하세요. 가상 규칙의 현재 자격 기준은 직전 월이며, 이번 달 실적이 증가해도 현재 자격을 자동 승격하지 않습니다.

저장 시 다른 세션의 변경과 충돌하면 내 입력을 유지합니다. '최신 값 불러오기 (내 입력 초기화)'로 저장값을 확인한 뒤 다시 수정하세요. 저장 성공 후 서버 값을 재조회하며 네트워크 실패 시 오류를 표시합니다. 카드/월 재조회 시 작성 중인 입력은 초기화됩니다.

백엔드/PostgreSQL과 에뮬레이터 실행 후 `./scripts/android.ps1 monthly-test`로 앱·테스트 APK 빌드, 실제 UI/API/DB 검증, 해당 합성 계정 정리를 수행합니다. 실제 휴대폰은 별도 승인된 USB 디버깅 연결 후 `./scripts/android-month-state-smoke.ps1 -Device <adb기기ID>`로 확인할 수 있으나 아직 실행하지 않았습니다. API 검사는 `python scripts/month_state_smoke.py`. 상세 결과는 docs/verification/2026-10-19-month-state.md.

## 구매·취소에 따른 혜택 한도
가계부에서 카드를 선택하면 연결 거래 혜택 사용액과 잔여 한도가 표시됩니다. 가상 B카드(1%, 기준 한도10,000원)는 50,000원 구매 후 사용500원/잔여9,500원, 20,000원 부분 취소 후 사용300원/잔여9,700원입니다. 추천만 조회하면 한도를 사용하지 않습니다. 가상 추정이며 실제 카드사의 확정 혜택은 아닙니다.

월별 편집은 거래 차감 전 기준 한도를 보존합니다. API 호출 시 `baseRemainingCapWon`을 월별 PUT의 `remainingCapWon`에 넣으세요. 거래 반영 후 계산값을 다시 넣으면 이중 차감됩니다. 합성 API 검사: `python scripts/benefit_smoke.py`. 실제 화면·DB 검사: `./scripts/android.ps1 ui-test`. 월별 충돌 회귀: `./scripts/android.ps1 monthly-test`.

## 가계부 분류·예산 포함 수정
가계부의 거래에서 '분류·예산 포함 수정'을 누르고 카테고리와 '예산에 포함'을 저장합니다. 부분 취소 후 남은 지출이 새 분류의 예산으로 이동합니다. 예산에서 제외해도 카드 실적·혜택과 원장은 유지됩니다. 카드 비교 결과는 무효화되므로 다시 비교하세요.

동시 수정409이면 내 초안을 보존합니다. '최신 분류 불러오기 (입력 초기화)'로 저장된 값을 확인한 뒤 다시 수정할 수 있습니다. 설정되지 않은 카테고리 예산은 미설정으로 표시합니다.

`python scripts/classification_smoke.py`는 실제 API/PostgreSQL 검사입니다. `./scripts/android.ps1 classification-test`는 앱·검사 APK를 빌드한 뒤 실제 Android 화면→API→DB를 검증하고 해당 합성 계정만 정리합니다. 상세 결과는 docs/verification/2026-10-19-ledger-classification.md.


## 월별 전체·카테고리 예산 수정
설정 → '월별 예산 수정'에서 해당 월의 저장값을 불러옵니다. 월을 바꾸면 '저장된 예산 불러오기 (입력 초기화)'를 누른 뒤 수정하세요. 없는 월은 빈 입력으로 시작합니다. 전체 예산과 미반영 고정비를 입력하고 필요한 분류의 한도 스위치를 켜 금액을 입력한 뒤 '월 예산 저장'을 누릅니다. 미반영 고정비가 없으면0을 명시합니다.

설정되지 않은 분류와0원 한도는 다릅니다. 분류 한도 합계는 전체 예산 이하여야 합니다. 저장 충돌 시 입력은 보존되며 최신 값 조회 후 다시 수정할 수 있습니다. 저장 후 가계부의 잔액과 새 카드 추천에 반영되고 실적·혜택은 유지됩니다.

검사: `python scripts/budget_smoke.py`(실제 API/PostgreSQL), `./scripts/android.ps1 budget-test`(앱 빌드→에뮬레이터 조작→DB 대조→해당 합성 계정 정리). 실행 전 PostgreSQL·API·에뮬레이터를 준비하세요. 증거는 `.run/android-budget/<runId>`와 `docs/verification/2026-10-19-monthly-budget.md`에 남습니다. 실제 휴대폰·금융 연동 검증과는 별개입니다.


## 반복 고정비 일정과 거래 연결
설정 → 반복 고정비 관리에서 월을 선택하고 저장값을 불러옵니다. 일정 추가/수정에서 이름, 예상액, 예정일(1~31), 분류를 입력합니다. 변경은 선택한 월부터 적용되고 이전 월 기록은 유지됩니다. 사용 스위치를 끄면 그 월부터 중지하며 이미 따로 지정한 이후 월 변경은 유지합니다.

기존 직접 입력 잔액은 그대로 보존됩니다. 모든 고정비를 일정에 등록한 뒤 '반복 일정 기준으로 전환'해야 자동 계산 잔액을 추천에 사용합니다. 두 기준은 합산하지 않습니다. 예산 없는 월은 먼저 예산을 설정해야 합니다.

가계부에 실제 사용을 기록한 뒤 일정의 '거래 연결'에서 해당 월 거래를 선택합니다. 한 거래를 여러 고정비에 연결할 수 없습니다. 연결 해제는 거래를 삭제하지 않습니다. 부분 취소·예산 제외는 예약액을 복원합니다. 예상보다 덜 지출했다면 차액은 남으므로 앞으로 추가 지출이 없는지 확인해 예상액을 수정하세요.

검사: `python scripts/fixed_smoke.py`와 `./scripts/android.ps1 fixed-test`. 후자는 앱/검사 APK 빌드→에뮬레이터→API→DB 대조→해당 합성 계정 정리까지 수행합니다. 실제 서버와 PostgreSQL, emulator-5554가 준비돼 있어야 합니다. 실행 결과 `.run/android-fixed/<runId>`, 상세 `docs/verification/2026-10-19-recurring-fixed-costs.md`.


## 고정비 화면 종료 안정성 검사
`./scripts/android.ps1 lifecycle-test`는 앱·검사 APK 빌드 후 화면12회 진입/편집/이동/종료,4회 재생성 및 지연 조회 중 화면 제거를 검증합니다. 실제 API/DB 검사는 저장값30,000원이 미저장 편집으로 바뀌지 않는지 대조합니다. 지연 응답 취소 검사는 기기 내부 합성 HTTP 서버를 사용하므로 실제 API 성공 근거와 구분합니다.

PostgreSQL·API·emulator-5554를 먼저 준비하고 검사/설치를 같은 기기에서 동시에 실행하지 마세요. 이어서 `./scripts/android.ps1 fixed-test`, `./scripts/android.ps1 budget-test`로 실제 MainActivity의 고정비/부분 취소/예산/추천을 검증합니다. 증거는 `.run/lifecycle/<runId>`와 `docs/verification/2026-10-19-fixed-cost-stability.md`입니다. 원래 SlotTable 예외의 정확한 원인은 아직 확정하지 않았습니다.


## 완전 종료 후 조회 실패·복구 검사
PostgreSQL/API와 에뮬레이터 실행 후 `./scripts/android.ps1 cold-start-test`를 실행합니다. 앱 프로세스를 매번 완전히 종료하고 정상 조회,서버 오류,잘못된 JSON,필수 필드 누락,중첩 일정 오류,연결 끊김,조회 중 종료7개 경로를 확인합니다. 장애는 로컬 검사 프록시에서만 주입하며 금융기관 호출은 없습니다. 마지막에 원래 API 연결로 복구하고 합성 계정만 정리합니다.

기록은 `.run/coldstart/<batch>/<runId>`에 저장합니다. `first-reported-exception.txt`는 검사 프레임워크의 최초 보고 예외, `test-observation.txt`는 검사 코드가 관측한 예외, `teardown.txt`는 종료 예외입니다. 이들을 같은 원인으로 단정하지 마세요. 상세 결과와 남은 미확정 사항: `docs/verification/2026-10-19-fixed-cost-cold-start.md`.


## debug 예외·화면 수명 기록
새 debug APK는 고정비 진입/조회/이탈과 Activity 수명, 최초 관측 예외 및 미처리 종료 예외를 앱 내부 백업 제외 폴더에 기록합니다. 메시지·화면 내용·인증정보는 기록하지 않으며 자동 전송하지 않습니다. release 빌드에는 저장 기능이 없습니다.

오류 재발 직후 **앱 데이터 삭제·재설치·검사 실행 전에** 프로젝트 루트에서 `python scripts/export_debug_diagnostics.py --device emulator-5554`를 실행하세요. 실제 휴대폰은 연결된 기기 ID로 바꿉니다. 이 명령은 앱을 실행/종료하지 않고 `.run/diagnostics-export/<실행ID>`에 검증된 기록만 복사합니다. 일시적 쓰기 중에는 추출이 실패할 수 있으므로 기록이 안정된 뒤 다시 실행합니다. 비밀키나 화면 캡처를 보낼 필요는 없습니다.

`first-exception.json`은 해당 프로세스에서 최초로 관측한 예외, `first-uncaught.json`은 최초 미처리 종료 예외입니다. 둘은 원인이 같다는 뜻이 아닙니다. 최신5개 실행과 최근 종료 오류3개 실행(합집합 최대8개)을 보존하며 앱 데이터 삭제/앱 제거 시 사라집니다. 상세 저장 범위와 제한은 `docs/verification/2026-10-19-debug-diagnostics.md`를 참고하세요.

검증은 PostgreSQL·API·emulator-5554 실행 후 `./scripts/android.ps1 diagnostics-test`입니다. 시험 계정과 로컬 HTTP500 주입, 의도적인 합성 예외로 프로세스를 종료하는 검사가 포함됩니다. 화면 캡처·UI 덤프·원본 예외 메시지/검사 출력을 저장하지 않습니다. 기존 SlotTable 오류를 재현하는 검사는 아닙니다.


## CODEF 연동 개발 상태
공식 SDK/샘플 기준 OAuth·계정 관리·카드 조회 통신 코드와 로컬 합성 계약 검사를 추가했습니다. `./scripts/backend.ps1 test`로 실행하며 외부 금융기관에는 호출하지 않습니다. 전체 금융 연결은 아직 미완료입니다. 사용자 소유 연결 API/암호화 DB 저장은 구현했고, Android 인증 화면·추가 인증 이어하기·기관별 거래 정규화·동기화가 남아 있습니다.

설정 기본값은 비활성입니다. 실제 연결 준비와 로컬 비밀 설정 경로는 [CODEF 안내](docs/integrations/CODEF.md), 검증 범위는 [검사 결과](docs/verification/2026-10-19-codef-contract.md)를 참고하세요. 새 백엔드 실행 시 로그인 후 `GET /v1/finance/codef/status`에서 준비 상태를 조회할 수 있습니다. 키를 설정했다는 이유로 실제 연결 성공으로 표시하지 않습니다.


## CODEF 연결 API와 PostgreSQL 검사
`./scripts/backend.ps1 finance-test`는 실제 PostgreSQL에 V9를 적용하고 로컬 합성 공급자로 연결·재인증·해제, 사용자 격리, 중복 요청, 응답 시간초과·불명확 결과, 암호문 변조를 검사합니다. 합성 계정은 검사 종료 때 정리하며 실제 CODEF 인증정보는 필요 없습니다.

연결 API는 `/v1/finance/connections`, 요청 결과는 `/v1/finance/operations/{id}`입니다. 변경에는 UUID Idempotency-Key, 재인증/해제에는 조회한 expectedVersion이 필요합니다. UNKNOWN/AUTH_REQUIRED를 성공으로 표시하면 안 됩니다. 상세 API·동의/키 설정은 [CODEF 안내](docs/integrations/CODEF.md), 실행 근거는 [연결 검증](docs/verification/2026-10-19-codef-connections.md)에 있습니다.

Connected ID 저장 키 생성은 `./scripts/configure-finance-key.ps1`입니다. 로컬 `.local/finance-storage.properties`와 CODEF_STORAGE_KEY_FILE을 사용하며 기존 키를 덮어쓰지 않습니다. 이번 PC에는 저장 키 설정을 마쳤고 외부 CODEF는 비활성 상태로 유지합니다.
