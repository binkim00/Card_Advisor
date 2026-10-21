# 2026-10-19 대기 실패 수정 및 최종 실기기 검증

## 결과

이번 대상인 API 처리 뒤 완료 문구·탐색 버튼이 갱신되지 않는 대기 실패를 수정했다. 원본 프로젝트에서 debug·androidTest·release 빌드 성공, 최종 APK 설치 지문 일치, SM-F741N / Android17에서 구매 전체 흐름과 합성 금융 전체 흐름 통과를 확인했다.

- 최종 제품 APK SHA256: `954387c908a7e46b43ca478f33004a222874846ff9233708cfe644988f75ca9d`
- 구매 최종 실행: `520f3824-e87b-43e6-8bae-3d9c13a7ec71`
- 금융 최종 실행: `4f641cfd-89d5-4804-a8ab-33ea93e1698d`
- 증거: `outputs/wait-root-fix-20261019/`

## 원인과 수정

실제 HTTP를 호출하는 Compose 통합 검사에서 코루틴이 IO 이후 작업 스레드(OTHER)로 재개됐다. 작업은 이미 종료됐지만 화면에는 이전 안내 또는 비활성 탐색 상태가 남는 경합을 재현했다. `c54db5f9`에서 가상 설정과 후속 원장·실적·예산 조회는 약 0.2초 안에 끝났고 APP_TASK_END도 기록됐지만 15초 뒤에도 가입 안내가 남았다. 네트워크 시간 초과가 아니었다.

UI 작업 scope에 `Dispatchers.Main.immediate`를 명시했다. MainActivity, FinanceEditor/FinanceSyncEditor, MonthlyStateEditor, BudgetEditor, ClassificationEditor, FixedCostEditor가 화면 상태와 상위 busy 콜백을 Android Main에서 직렬로 갱신한다. HTTP 자체는 기존처럼 ApiClient의 Dispatchers.IO에서 실행한다. composition 수명에 따른 취소는 유지했다.

MainActivity와 금융 화면만 먼저 바꾼 중간 실행 `875fb6f0`에서는 월별 저장 뒤 같은 경합이 남았다. MonthlyStateEditor의 HTTP 재개가 OTHER로 기록됐고 탐색 버튼은 비활성이었다. 같은 구조의 나머지 편집 화면까지 수정한 후 두 전체 흐름이 통과했다. 제한 시간은 늘리지 않았다.

Compose 검사에서 LaunchedEffect/rememberCoroutineScope의 실행 문맥이 effectContext에서 파생되는 동작은 [AndroidX 공식 소스](https://android.googlesource.com/platform/frameworks/support/+/afa2b9b28c6b733b6ed64d9c120cfa2fe10e4c26/compose/ui/ui-test/src/androidMain/kotlin/androidx/compose/ui/test/ComposeUiTest.android.kt)와 대조했다. 프레임워크 내부의 구체적인 snapshot 결함이나 일반 실행의 동일 장애까지 확정했다는 의미는 아니다. 이번에는 실제 통합 검사에서 관측한 UI 상태 갱신 경합을 고쳤다.

## 회귀 및 진단

- BlueDeviceFlowTest: 초기 설정 후 추천→가계부 조회→홈을 12회 반복하고, 가입·구매·추천 무효화·과다 취소 거절·부분 취소·실적/혜택 한도 재계산·월별 값 저장·재로그인·재조회까지 검증한다. UI 작업 완료 및 UI HTTP 재개가 모두 Main인지도 검사한다.
- 최종 보존 trace: APP_TASK_END 60건, HTTP RESUMED 169건 모두 Main. 해당 요청의 최대 소요 66ms. 메모리 버퍼는 최근 1024개를 보존하므로 이 수치는 보존된 구간 기준이다.
- FinanceSyncFlowTest: 최초 입력 폼이 composition에 나타난 뒤 입력하도록 대기한다. 금융 작업 종료가 Main인지 검사한다. 최종 SYNC_TASK_END 26건 모두 Main.
- 요청 trace는 고정된 route/phase 코드·숫자 상태·시간만 기록한다. 경합의 타이밍을 바꾸지 않도록 요청마다 디스크 fsync를 하지 않고 제한된 메모리 버퍼를 쓰며, 검사 실패/종료 시 증거 파일로 저장한다. URL·Connected ID·인증값·요청 본문·원시 UI 트리는 넣지 않는다.
- DebugDiagnosticsTest.privacyRotationAndFirstFailure 통과: 비밀 표식 비노출, 첫 예외 보존, 파일 회전/크기, 요청 trace의 고정 스키마·메모리 한도 확인.
- Android17에서 지원되지 않던 중복 검사 확인 명령을 정상 dumpsys activity 메타데이터 확인으로 고쳤다. 원시 dump는 출력/저장하지 않는다. 실행 스크립트에 재현용 `-Stress` 옵션을 추가했다.

## 검증 근거

| 검사 | 최종 결과 |
| --- | --- |
| 원본 폴더 debug/androidTest/release 빌드 | 통과 |
| 기기 설치 APK와 빌드 파일 SHA256 | 일치 |
| 실기기 12회 전환 및 구매 전체 흐름 | 통과 |
| UI→API→DB 구매/부분 취소 | 구매50,000원, 취소20,000원, 순지출30,000원 |
| 다음 달용 실적/연결 혜택/잔여 한도 | 300,000원 / 300원 / 9,700원 |
| 다음 구매 후 예산 | 40,000원 |
| 월별 저장·재조회·재로그인 | 통과 |
| 합성 금융 연결/동기화/중복·취소/복구 | 통과 |
| 금융 최종 DB | 원장4건, 취소2건·40,000원, 예산 지출170,000원, 보류0 |
| 금융 409·500·잘못된 응답·응답 유실·UNKNOWN·세션 만료 | 통과 |
| 진단 개인정보·회전·용량 검사 | 통과 |
| 실제 금융 인증 | 미실시 |
| 배포 준비/공개 배포/스토어 승인 | 미판정/미실시/미실시 |

격리 폴더 최종 수정본도 구매 12회 전환(`62724785`)과 금융 전체(`b0d120b3`)가 통과했고, 원본 프로젝트 최종 빌드로 다시 위 결과를 확인했다. 성공 검사 계정은 각 검사에서 삭제·잔존0을 확인했다. 실패/중단 진단 계정5개도 DB 증거 저장 후 정확한 합성 계정만 삭제했고, 기존 사용자 데이터는 정리 대상으로 삼지 않았다. `synthetic-account-cleanup.json`에 기록했다. 앱은 일반 시작 상태로 돌렸고 API health UP/USB reverse18080을 확인했다.

테스트 실행기를 StandardTestDispatcher로 바꾸는 실험은 정지가 생겨 중단했고 최종 소스에 포함하지 않았다. 초기 반복 스크립트에서 사라진 가상 설정 버튼을 누르던 오류도 제품 오류로 세지 않는다. 이전 최초 IllegalArgumentException은 원래 전체 스택이 없어 원인 확정을 주장하지 않으며, 이번 최종 검사의 예외 목록은 비어 있다.

## 재실행

프로젝트 루트 PowerShell에서 PostgreSQL·API 실행 및 USB 디버깅 연결 후:

```powershell
.\scripts\android-blue-device-test.ps1 -Device R3CX701TE5J -AppApk .\android\app\build\outputs\apk\debug\app-debug.apk -TestApk .\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk -Stress
.\scripts\android-sync-test.ps1 -Device R3CX701TE5J -SkipBuildAndInstall
```

금융 검사는 테스트 클래스 경로의 합성 공급자를 사용하며 실제 금융 인증정보가 필요 없다. 금융 검사 종료 시 로컬 API 포트 연결을 복원한다. debug APK는 휴대폰에 설치되어 있다. release 산출물은 서명하지 않은 빌드이며 배포 승인/준비 완료를 의미하지 않는다.
