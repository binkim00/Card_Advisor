# 실기기 → API → PostgreSQL 검증 (2026-10-19)

## 실제 확인 범위
사용자가 연결·USB 디버깅을 허용한 SM-F741N / Android 17 휴대폰으로 검증했다. 에뮬레이터가 아닌 실제 기기이며 adb로 모델/OS/가상 기기 표시를 확인했다. 앱·검사 APK를 설치하고 USB 포트 전달로 PC의 API와 연결했다. 외부 금융기관 요청은 없고 별도 가상 계정/카드/공급자만 사용했다.

회원가입·추천·수동 구매·부분 취소는 실행 중인 로컬 API18080과 PostgreSQL17.6에 연결했다. 금융 관리 검사는 별도 임시 Spring API와 같은 PostgreSQL, loopback 합성 CODEF 공급자를 연결했다. 종료 후 휴대폰의18080 전달을 기존 API18080으로 복원했다. 같은 휴대폰의 APK 설치·검사는 순차 실행했다.

## 검증 결과
| 흐름 | 실기기와 DB 확인 |
|---|---|
| 회원가입·가상 카드 설정 | 새 테스트 사용자1명, 가상 카드2개 |
| 추천 | 50,000원 주문에 가상 A카드, 이번 혜택1,500원 |
| 수동 구매 | 가상 B카드에 구매50,000원 저장, 다음 달용 실적270,000→320,000원 |
| 과다 취소 입력 | 50,001원 취소 입력 차단 |
| 부분 취소 | 20,000원 취소1건, 순지출30,000원, 다음 달용 실적300,000원 |
| 자격·혜택 분리 | 이번 혜택 자격은 충족 유지, 혜택 사용500→300원, 잔여 한도9,500→9,700원 |
| 추천 무효화·예산 | 구매 후 이전 추천 제거, 부분 취소 후 다음5만 원 추천 잔액 전체40,000/쇼핑−10,000/고정비 고려10,000원 |
| 재로그인 | DB에 저장된 구매/취소/실적/혜택 값 재조회 일치 |
| 금융 생성·조회·재인증·해제 | 화면에서 상태/버전 갱신, 해제 성공 후 DB 암호문 제거 |
| 충돌·조회 실패 | 두 번째 세션 충돌409 후 재조회, 최초 조회500 복구 |
| UNKNOWN | 생성·재인증·해제 각각 보류3건; 재로그인 후 유지, 재전송/해제 차단 |
| 금융 DB | 제공자 호출9건=작업9건, UNKNOWN3건, 재인증·해제 불명확 연결2개의 암호문 유지 |
| 인증정보 수명 | 백그라운드 이동 시 입력 삭제, 실제 DB 세션 만료 후 나가기/로그인 화면 복귀 |
| 정리 | 두 검사에서 생성한 가상 계정과 귀속 데이터 삭제 확인 |

- 구매 흐름 실행 ID: `0f2f0aef-1b65-420d-865c-c59dad00270c`. 근거 `.run/android-ui/0f2f0aef-1b65-420d-865c-c59dad00270c/verification.json` 및 `database.json`.
- 금융 실행 ID: `845e5b04-5d6e-4590-bb1c-7e83eb9bbc81`. 근거 `.run/android-finance/845e5b04-5d6e-4590-bb1c-7e83eb9bbc81/verification.json`.
- 실패 실행에서 생성된 계정은 정확한 실행 ID별로 조회/정리했다. 근거 `.run/physical-failed-attempts.json`. 기존 사용자·다른 검사 데이터·Docker 볼륨은 정리 대상에 포함하지 않았다.
- 빌드·검사 로그: `.run/physical-tracing-build.log`, `.run/physical-espresso-build.log`, `.run/physical-navigation-build.log`, `.run/physical-ledger-navigation.log`, `.run/physical-finance-final.log`.
- 검증 APK SHA256: `eb5e3e3b5e7ab3f6d97ef05b3abedcf27561317697b3e8e0a2a04739df910612`.
- 배포용 검증 사본: `outputs/physical-device-20261019/card-advisor-debug.apk`, `verification.json`.

## 발견한 오류와 수정
첫 실행(cfd9e3df-1ad0-4546-911d-6f364cf923a8)은 Activity 진입 전에 테스트의 AndroidXTracer.enableTracing에서 NoSuchMethodError로 종료됐다. 누락 메서드는 androidx.tracing.Trace.forceEnableAppTracing()이다. 이 앱 프로세스의 오류에서 예외 종류·코드 위치만 확인했으며, 제한된 앱 진단 파일에는 PROCESS_START만 있었다.

앱의 전이 의존성 tracing1.0.0과 테스트의1.2.0이 어긋났다. [공식 Trace API](https://developer.android.com/reference/androidx/tracing/Trace)에 따르면 해당 메서드는1.1.0에 추가됐다. debug/test 구성에 tracing1.2.0을 명시해 정렬했고 Gradle 해결 결과1.0.0→1.2.0을 확인했다.

두 번째 실행(7d5484c8-807b-4497-87e8-3ce3fcad0301)에서 Espresso3.5.0이 InputManager.getInstance를 반사 호출하다 NoSuchMethodException이 발생했다. [공식 AndroidX Test 변경 내역](https://developer.android.com/jetpack/androidx/releases/test#espresso_370)의 getSystemService 사용 수정에 따라 espresso-core3.7.0, core/runner1.7.0, junit1.3.0으로 테스트 의존성을 고정했다. alpha 버전은 사용하지 않았다. 사용자 잠금 해제 후 검사 Activity에만 KEEP_SCREEN_ON을 적용했다. 기기 잠금·자동 꺼짐 설정을 바꾸지 않았다.

재빌드·재설치 후 테스트 초기화를 넘어 실제 화면 검사가 실행됐다. release 런타임 의존성은 변경하지 않았다. Android17 자체 결함이나 기존 SlotTable 원인 해결로 주장하지 않는다.

이후 실제 앱에서 월별 값 저장 후 탐색 잠김을 재현했다(f6982391-d784-4966-8448-dd6e9af5a55b, c7a5856d-1da3-4d31-a334-761a985d382c). 가상 구매/취소와 월별 저장은 DB에 반영됐고, 검사 관측값은 월별 저장 버튼 enabled=1인 동시에 상위 설정/가계부/나가기 enabled=0이었다. 스크롤 순서 변경이나 상태 공유만으로는 해소되지 않았다(공유만 적용한 실행 eb4e7688-e595-45c3-87e3-5d623d0f1d37).

상태 공유 변경은 효과가 없어 되돌렸다. 최종 수정은 상단 탐색을 별도 AdvisorNavigation composable로 추출하고 enabled Boolean을 명시적으로 전달하는 것이다. 기존 inline Row/forEach 안의 상태 읽기를 분리해 탐색의 재구성 범위를 독립시켰다. LocalFlowTest의 enabled 대기 조건도 유지한 상태에서 월별 저장→가계부→로그아웃→재로그인 전체 검사가 통과했다. 확인된 증상은 완료된 저장 뒤 탐색 상태가 갱신되지 않은 것이며, Compose 내부 원인이나 기존 SlotTable 오류와의 인과관계는 미확정이다.

첫 금융 실행 e24cf156-1dab-476f-b921-684df3bb789c는 구매 검사 실패 직후 runner 결과에서 완료 문구/테스트 코드 위치 없이 중단됐다. 당시 원문을 보존하지 않아 최초 원인은 미확정이다. 이후 예외 종류·runner 상태 코드·허용된 라이브러리 프레임만 기록하도록 보완했고 199fb077-5286-46bf-b1d5-fd371f432212가 통과했다. 최종 탐색 수정 APK에서도 금융 흐름을 다시 통과했다. 이 단발 중단을 제품 오류 해결 근거로 사용하지 않는다.

자동 승인 검토에서 진단과 검사 재실행을 묶은 요청, 기존 검사에 포함된 화면 캡처가 거절됐다. 진단을 읽기 전용으로 분리하고 LocalFlowTest의 모든 화면 캡처를 제거한 뒤 승인된 검사를 수행했다. 원문 instrumentation 출력도 저장하지 않으며 실패한 소스 위치/예외 종류, 가상 금액·집계만 보존한다. 금융 검사 제어 코드는 test 소스에만 있다.

## 재현
PC API/PostgreSQL 실행, 휴대폰 USB 디버깅 허용 및 잠금 해제 상태에서:

```powershell
.\scripts\android-ui-smoke.ps1 -Device <adb 기기ID>
.\scripts\android-finance-test.ps1 -Device <adb 기기ID> -SkipBuildAndInstall
```

두 번째 명령의 SkipBuildAndInstall은 첫 명령이 현재 빌드 APK2개를 설치한 직후에만 사용한다. 새 코드 변경 시 생략해서 다시 빌드·설치한다. 검사에 실금융 인증정보를 입력하지 않는다. USB를 분리하면 현재 로컬 API 연결도 끊긴다.

## 남은 범위
이번에 실제 기기 확인을 완료한 것은 위 가상 데이터 흐름이다. 실제 CODEF/KB/하나 연결·거래 동기화, 쇼핑몰 앱 주문서 감지, 다른 기종·OS, 배포 준비·공개 배포·스토어 승인은 미검증이다. 반복 고정비 SlotTable 원인도 이 검사로 해결됐다고 주장하지 않는다.
