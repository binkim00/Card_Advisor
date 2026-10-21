# debug 예외·화면 수명 기록 검증 (2026-10-19)

이번 요청의 진단 기능 구현과 로컬 검증은 완료했다. 기존 SlotTable 오류의 원인 확인/해결, 실제 금융 연동, 물리 휴대폰 검증, 배포 준비·공개 배포·스토어 승인은 완료하지 않았다.

## 검증 결과
- 최종 실행 ID: `5c2cd603-3343-49e3-ab6b-6a1c22b2c378`. Android16 emulator-5554, 실제 로컬 API18080/PostgreSQL54329와 합성 계정 사용.
- debug 앱·Android 검사 APK·unsigned release 빌드 성공. 최종 debug SHA256: `1dc05a1a6b852d2c2ca507045443767f8a35263a0b99cb21542da3e2da090d7a`.
- DebugDiagnosticsTest 4경로 통과: 개인정보 시험 문자열/로그 회전/최초 예외 고정/보존 정책; 고정비 최초 진입·HTTP500·재조회·이탈·재진입·정상 종료; 합성 비정상 프로세스 종료; 이후 새 프로세스 시작·정상 종료.
- MainActivity 가입→가상 데이터→고정비 조회에 로컬 프록시가 HTTP500을1회 주입했다. 이후 실제 API 조회 복구, 다시 진입 및 Activity 종료까지 기록 순서를 확인했다. DB 사용자1/예산1/일정0/거래0 확인 후 해당 합성 계정만 삭제하고0명 확인. 이번 범위는 기록 보존이며 기존 구매/부분 취소 계산 검사는 다시 실행하지 않았다.
- 관측된 흐름: PROCESS_START→ACTIVITY_CREATED/STARTED/RESUMED→FIXED_ENTER→FIXED_LOAD_START→EXCEPTION→FIXED_LOAD_START/OK→FIXED_EXIT→FIXED_ENTER→FIXED_LOAD_START/OK→ACTIVITY_PAUSED/STOPPED/DESTROYING→FIXED_EXIT→ACTIVITY_DESTROYED.
- 최초 ApiException 파일은 복구/종료 후 바이트가 동일했다. 합성 JSONException 후 다른 스레드에서 합성 ArrayIndexOutOfBoundsException을 던져 Android의 정상 미처리 예외 종료 동작이 유지되는지 확인했다. 실제 프로세스 부재 및 별도 first-uncaught.json 생성 확인. 새 프로세스 실행 후 앞선 두 예외 파일 모두 바이트 동일.
- 메시지·원인·억제 예외·스레드명·위조 프레임에 넣은 PRIVATE_CANARY 문자열이 기록에 없음. 원인/억제 예외 유형은 보존.1000개 이벤트로 회전 유도 후 최초 예외 유지 및 각 파일64KiB 이하 확인. 별도 시험 저장소에서 정상 세션9개를 추가해도 이전 종료 세션 보존 확인.
- release DEX에 DebugDiagnosticsApplication/DiagnosticStore/저장 경로 문자열이 없음. debug에서만 기록한다.
- 읽기 전용 추출 CLI 실행 전후 프로세스 부재 유지, 세션 목록과 모든 파일 바이트 동일. 추출한 세션 6개. 앱 실행/재설치/DB 변경 없음.

## 저장 범위와 보존
앱 내부 `no_backup/debug-diagnostics/session-*`에 저장하며 백업·자동 업로드·AI 전송을 하지 않는다. 앱 제거/데이터 삭제 시 사라진다.

| 파일 | 의미 |
|---|---|
| first-exception.json | 이 프로세스에서 최초로 관측한 예외; 후속 예외로 덮어쓰지 않음 |
| first-uncaught.json | 이 프로세스의 최초 미처리 종료 예외; 앞선 관측 예외와 독립 |
| events.jsonl / events.previous.jsonl | 현재/직전 이벤트 묶음, 각64KiB 이내 |

최신5개 실행 세션과 최근 비정상 종료3개 세션의 합집합, 최대8개를 보존한다. 예외 파일은 원자적으로 쓰고 이벤트는 기록마다 동기화한다. 예외 기록은 최대8개 유형과 유형당16개 코드 프레임으로 제한한다. 저장 실패는 원래 예외를 대체하지 않는다.

기록 필드는 고정된 이벤트·지점, 순서/단조시간/PID, MAIN·OTHER, 예외 클래스와 검증한 코드 클래스/메서드/행 번호뿐이다. 메시지·파일명·스레드명·화면 텍스트/캡처/입력·Bundle·요청/응답·URL·헤더·계정/인증값을 저장하지 않는다. 원본 검사 출력도 파일에 저장하지 않는다. 이 보장은 새 진단 파일/이번 검사 산출물 범위이며 Android 자체 시스템 기록 수집 기능은 추가하지 않았다.

## 재현·추출 방법
프로젝트 루트에서 PostgreSQL·API·emulator-5554가 준비됐을 때 `./scripts/android.ps1 diagnostics-test`로 전체 검사를 실행한다. 이 명령에는 합성 예외로 앱을 의도적으로 종료하는 검사가 포함된다. 서버/기기 설치 검사를 병렬 실행하지 않는다.

실제 오류 재발 직후 먼저 `python scripts/export_debug_diagnostics.py --device emulator-5554`를 실행한다. 실제 휴대폰은 기기 ID로 바꾼다. 앱을 다시 켜거나 데이터를 지우지 않아도 추출된다. 목적지 기본값은 `.run/diagnostics-export/<ID>`, 변경은 `--output <폴더>`다. 쓰기 도중 스냅샷이 불완전하면 안전하게 실패하며 잠시 후 다시 시도한다. 원본 logcat/화면/인증정보를 요청하거나 저장하지 않는다.

구현: debug/Diagnostics.kt·DebugDiagnosticsApplication.kt, main/DiagnosticEvent.kt·FixedCostEditor.kt·ApiClient.kt, release/Diagnostics.kt. 검사: DebugDiagnosticsTest.kt, scripts/android-diagnostics-smoke.py. 추출: scripts/export_debug_diagnostics.py.

근거: `.run/diagnostics/5c2cd603-3343-49e3-ab6b-6a1c22b2c378/verification.json`, 같은 폴더 records와 readonly-before, `.run/diagnostics-final-build-test.log`. 기존 예산·구매·취소·추천 근거는 `2026-10-19-fixed-cost-cold-start.md`에 보존했다.

## 한계
기존 SlotTable 오류는 이번에도 자연 재현되지 않았다. 합성 종료 검사는 기록 보존만 증명하며 원래 오류의 원인과 연결하지 않는다. 고정비 처리/HTTP I/O에서 잡은 예외와 앱 기본 처리기로 전달된 미처리 예외를 관측한다. 프레임워크 내부에서 삼킨 예외, 네이티브 크래시·ANR·OS 강제 종료, 메모리/디스크 고갈까지 예외 저장을 보장하지 않는다. 강제 종료에는 종료 이벤트 자체가 없을 수 있다. 최초 관측 예외가 반드시 인과적 최초 원인인 것은 아니다.

사용 API 근거: [ActivityLifecycleCallbacks](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks), [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile), [UncaughtExceptionHandler](https://developer.android.com/reference/java/lang/Thread.UncaughtExceptionHandler).
