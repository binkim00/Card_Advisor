# 반복 고정비 새 프로세스·조회 실패·종료 검증 · 2026-10-19

## 확인한 원인과 확인하지 못한 원인
- 실제 MainActivity를 강제 종료하고 프로세스가 없음을 확인한 뒤 매번 새 instrumentation 프로세스로 실행했다. debug 전용 화면을 대신 사용하지 않았다.
- 수정 전 합성 응답에서 scheduledRemainingWon 또는 일정 remainingWon을 빼면 FixedCostEditor가 화면을 그리는 중 JSONObject.getLong을 호출해 JSONException을 발생시키는 것을 재현했다. 화면 상태로 넘기기 전 전체 응답 검증이 없었던 것이 이 재현 문제의 원인이다. 실제 서버에서 같은 누락 응답이 발생했다는 증거는 아니다.
- 첫 누락 응답 실행: 0cd73209-6078-4bd7-84dd-a0fa722087f7 / 3b3fcb16-08f3-4ca8-97a8-4373f4798cd7. 최초 보고 예외는 instrumentation.txt의 JSONException: No value for scheduledRemainingWon, FixedCostEditor.kt:63이다. 테스트 본문에는 이후 ComposeNotIdleException이 관측되어 두 기록을 구분한다.
- 중첩 일정 누락 실행: 4ac07437-8eeb-463f-8806-673b8ca6442a / 3b09df28-847d-46c7-ba36-d76246d6194e. 최초 보고 예외는 No value for remainingWon, 화면 내부 Card/Column 렌더링의 FixedCostEditor.kt:95이다.
- 취소 처리 수정 전 보존 APK(SHA256 875ED634838BAEC7C688ED14D1501956039B7584A1FA7CFE5ACD6A911770B279)로도 중첩 누락 응답을 비교했다. ef80b408-0057-4630-a11a-7baeaeb19a4a / 399d6726-3e09-4cef-85e4-a54e5e2a712a에서 같은 JSONException을 확인했다.
- 위 세 재현에서 원래 SlotWriter.moveSlotGapTo / ArrayIndexOutOfBoundsException은 발생하지 않았다. 이 JSON 오류를 최초 SlotTable 오류의 확정 원인으로 연결하지 않는다.
- 기기에 남은 최초 충돌 PID6164의 crash/main/system 기록을 확보했다. 10-19 00:44:45.308 이후 Activity 종료 중 SlotWriter 예외만 남아 있으며, 이전 원인 예외와 당시 응답은 없다. .run/coldstart-existing-app-crashes.log 및 coldstart-original-pid6164.log. 최초 SlotTable 오류의 정확한 원인은 계속 미확정이다.

## 수정 내용
- FixedCostResponse.kt에서 화면에 필요한 합계·중첩 일정·연결 거래·가계부·예산의 필드/형식/금액/기준 월을 조회 경계에서 검증한 뒤 전체 스냅샷을 한 번에 반영한다. 미확인 또는 누락 금액을0으로 바꾸지 않는다.
- 파싱/형식 오류는 안전한 한국어 재조회 안내로 처리한다. 조회 시작 시 이전 표시값을 오래된 값으로 취급하고 저장/연결을 막으며 정상 재조회 후 해제한다. 초기 조회 실패 시 마지막 조회값이 있다고 잘못 안내하지 않는다.
- Compose 버전, 서버 API, DB 스키마와 계산 규칙은 변경하지 않았다. 취소된 작업의 상태 변경 차단은 유지한다.
- FixedCostColdStartTest와 android-coldstart-smoke.py는 최초 보고 예외·검사 관측 예외·종료 예외를 분리 보존한다. 합성 계정 로그인 후 실패한 화면만 필요 시 저장하며 비밀번호/토큰/HTTP 본문은 로그에 기록하지 않는다.

## 수정 후 새 프로세스 검증
같은 수정 APK에서 아래7개 경로를 각각 새 프로세스로 확인했다. HTTP 장애는127.0.0.1:18081의 검사 전용 프록시로 주입했으며 나머지 요청은 기존 API18080으로 전달했다. 실제 금융 서비스 장애 검증이 아니다.

| 경로 | 새 PID | 실행 ID | 결과 |
|---|---:|---|---|
| healthy | 8821 | 440d4add-3fa0-4b6e-96fb-b495fe566aa9 | 통과 |
| http500 | 9037 | 856cddf9-2706-4ae5-b628-dfebf46f54b3 | 통과 |
| malformed | 9185 | 8b243eb3-b8fb-4b8b-9266-ee986f7bc717 | 통과 |
| missing_field | 9273 | 890e2504-c3c4-45ae-b454-0177f695dabd | 통과 |
| bad_item | 9341 | 126b7f90-7f54-46ba-bd79-438c1b81528b | 통과 |
| disconnect | 9409 | 18ca3705-c560-4949-a308-d1c2196ba737 | 통과 |
| delay_close | 9476 | fab9731c-d21f-4147-b747-3c024279dee5 | 통과 |

- 오류 경로는 안전한 오류 안내→설정 이동→고정비 재진입→초안 편집/닫기→추천→고정비 복귀→Activity 종료를 확인했다. delay_close는 초기 응답 도착 전에 Activity를 종료하고 뒤늦은 응답을 해제했다.
- 매 실행 직전 force-stop 후 pidof 결과가 비어 있음을 확인했다.7개 PID가 서로 다르다. 단순 Activity 재생성과 구분한다.
- DB: 일정0/원장0, 초기 전체예산120,000/수동고정비30,000 유지. 미저장 초안이 일정을 만들지 않는다. 정상/복구 경로의 추천1개, 응답 전 종료 경로의 추천0개. 각 합성 계정 삭제 후0개 확인.
- 중간 HTTP500 실행1회(1cb7b30d-c6ee-4cb6-81e2-db3458f87b58)는 오류 복구·초안 닫기 뒤 카드 비교 버튼을 기다리다15초 시간 초과했다. 첫 예외는 ComposeTimeoutException이며 SlotTable 예외는 없었다. 실패 화면 보존을 보강한 뒤 동일 제품 코드로856cddf9-2706-4ae5-b628-dfebf46f54b3가 통과했다. 이 시간 초과의 원인은 미확정이며 재실행 성공을 별도의 제품 수정으로 주장하지 않는다.

## 기존 앱→API→DB 회귀
- FixedCostFlowTest: de4808b6-7fd4-4f19-8e5a-28ae6650bc62. 일정 생성/수정/중지/재개, 수동/일정 기준, 연결/해제, 부분 취소,409 초안 보존, 월 격리, 선택 월 예산 이동, 재로그인, 추천을 검증했다.
- DB: 일정 버전10, 월별 규칙60,000/80,000, 연결1건, 구매50,000−취소20,000=순지출30,000. 예약 이번 월30,000/다음 월80,000, 수동값30,000/0 보존. 다음50,000 결제 추천 전체40,000/분류−10,000/고정비 제외10,000. 카드 실적300,000·혜택300·한도9,700 유지.
- BudgetFlowTest: 332ee4d0-7cb4-409a-88d7-79093a3f3bee. 전체/분류 예산 편집·충돌·월 격리·재조회·추천을 검증했다. DB 전체240,000/식비80,000/쇼핑100,000/고정비20,000, 다음 결제 추천 전체160,000/분류20,000/고정비 제외140,000.
- 정상 검증9개(새 프로세스7+기존 흐름2) 통과. 합성 계정 모두 정리. 기존 계정·DB 볼륨·마이그레이션V1~V8 보존. 백엔드 코드는 바꾸지 않아 변경 없는 전체 단위/API 검사는 반복하지 않았다.
- Android debug 및 검사 APK 빌드 성공. APK SHA256 34C686E72361D1A9C72D779D5ADEA9CA6FB6A5B1E6B45A7A1635F891E8C8D0B3.

## 재현 방법
PostgreSQL/API와 emulator-5554가 실행된 상태에서 프로젝트 루트에서 순서대로 실행한다.

```powershell
./scripts/android.ps1 cold-start-test
./scripts/android.ps1 fixed-test
./scripts/android.ps1 budget-test
```

같은 기기에 설치/검사를 동시에 실행하지 않는다. 프록시는 로컬에만 열리고 finally에서 종료하며 ADB 연결을 API18080으로 복원한다. 단일 장애만 확인할 때는 빌드 후 `python scripts/android-coldstart-smoke.py --modes missing_field`를 사용한다. `.run/coldstart/<batch>/<runId>`에 단계별 증거와 DB 대조가 남는다. 테스트의 강제 종료는 이 앱 프로세스만 대상으로 한다.

## 남은 범위
원래 SlotTable 예외와 일시적인 검사 대기 초과의 정확한 원인은 미확정이다. 물리 휴대폰/실제 금융 연결/배포 준비/공개 배포/스토어 승인은 확인하지 않았다. 전체 FR-01~FR-24 완료가 아니다.
