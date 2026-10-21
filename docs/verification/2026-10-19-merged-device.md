# 최신 금융 기능·새 디자인 통합 검증 — 2026-10-19

다른 채팅 `노션 페이지 세부 계획·명세 작성`의 완료 작업을 확인했다. Android 기간별 금융 동기화·보류 확인·수동 중복 구매/취소 원거래 연결, 응답 유실/충돌/세션 만료 검사가 완료됐으며 최신 로컬 API도 반영됐다. 그 뒤 적용한 새 디자인과 함께 현재 원본 소스36개를 별도 사본에 복사하고 해시 일치를 확인했다. 원본 소스/서버를 변경하지 않았다.

- debug/테스트 APK 빌드 통과. 새 앱 SHA256 `c97b481b327c49f68282b7b1dfcec05149ec9e2bc573cf296b302853a80feb08`.
- 최신 통합 APK 금융 시나리오: emulator-5554 / Android16 / run `2a25c46f-cdab-4dd7-80a0-3935f968cf6e` 통과.
- 앱→합성 공급자를 사용하는 실제 API→PostgreSQL: 원장4건, 취소2건·40,000원, 예산 지출170,000원, 보류0건. 응답 유실 복구·버전 충돌·UNKNOWN/RUNNING 차단·재로그인·만료 세션 포함. 합성 계정 삭제 확인.
- 이전 디자인 구매/취소 증거와 구분: 이번 통합 APK의 신규 실기기 구매/금융 검사는 아직 못 함.
- 휴대폰 R3CX701TE5J는 최초 shell timeout. 해당 기기 reconnect 후 목록에서 사라졌고, 사용자가 케이블 재연결·잠금 해제 후 Windows의 Samsung ADB Interface는 정상이나 ADB에는 나타나지 않았다. 실행 중인 검사가 없는 것을 확인한 뒤 ADB 서버를 재시작하자 `(no serial number) offline` 상태가 됐다. 에뮬레이터의18080 전달은 복원했다. 사용자에게 USB 디버깅 끄기/켜기를 요청했다. 데이터/앱 삭제와 USB 인증 철회는 하지 않았다.
- 결과물 `outputs/merged-device-20261019`. 실제 금융 인증·공개 배포·스토어 승인은 미검증.

## 실기기에서 이어서 실행

ADB가 device 상태로 정상 응답하고 다른 검사가 없는 경우:
```powershell
.\scripts\android-blue-device-test.ps1 -Device R3CX701TE5J -AppApk .\outputs\merged-device-20261019\card-advisor-debug.apk -TestApk .\outputs\merged-device-20261019\card-advisor-androidTest.apk
.\scripts\android-sync-test.ps1 -Device R3CX701TE5J -SkipBuildAndInstall
```
두 명령은 순차 실행한다. 두 번째는 첫 번째에서 설치한 동일 APK를 검사하며 합성 공급자는 test 서버에만 존재한다. 최신 소스 변경이 있으면 다시 빌드해야 한다.


## 실기기 진행 결과 — 같은 턴 후속

USB 디버깅을 껐다 켠 뒤 R3CX701TE5J(SM-F741N/Android17)가 정상 응답했다. 최신 앱/테스트 APK 설치 성공, `pm path`의 설치 앱 SHA256이 `c97b481b327c49f68282b7b1dfcec05149ec9e2bc573cf296b302853a80feb08`와 일치함을 확인했다. 이전 USB 차단은 해소됐다.

실기기 전체 검사는 **실패**이며 에뮬레이터 통과와 구분한다.

1. `1c5ea8c7-e092-473f-bb1e-e14550a60174`: 가입/가상 설정/추천 뒤 가계부 이동에서 RuntimeException→ExecutionException→IllegalArgumentException. 구매0/취소0. 기존 uncaught 진단에 기록되지 않았고 최초 전체 스택을 확보하지 못해 원인 미확정. SlotTable 오류라고 단정하지 않는다.
2. `eb0eb003-0ce9-461d-9570-93596b8c019e`: 메시지/화면 없는 최초 예외 타입·프레임 기록을 BlueDeviceFlowTest에 추가했다. 구매50,000원 후 부분 취소 버튼 대기 초과. 가계부 재조회 중 비활성 행을 누르던 details() 검사에 enabled 대기를 추가했다. 제품 수정으로 계산하지 않는다.
3. `ecb5d45d-eb02-4fa3-b491-8420f485f8cf`: 구매50,000원·취소20,000원·다음 구매 후 예산40,000원을 DB와 대조했다. 실제 앱 캡처에서 홈 공식 이미지3개/부분 취소 순지출30,000원/남은 예산90,000원을 확인. 이후 설정 클릭의 enabled 대기 초과로 전체 회귀는 실패. 제품 상태와 검사 선택자 문제 중 어느 원인인지 미확정이다. 최초 Compose 예외는 이 실행에서 재현되지 않았으며 해결됐다고 주장하지 않는다.
4. 금융 독립 검사 `eb565e2b-dafc-4cea-aa5c-7561471a6d3a`: connection→first_sync→reconcile 진행, FinanceSyncFlowTest.kt:64의 취소 원거래 연결 후 notice 완료 대기 초과/notice_unknown. 프로세스 crash=false. 최종 DB 상태는 physical-finance-verification.json에 보존. 원인 미확정이며 이전 다른 채팅의 일회성 대기 실패와 같은 원인이라고 단정하지 않는다.

실패 수동 계정3개는 각 DB 증거 보존 후 정확한 이메일로만 삭제했고 금융 시험 계정은 test finally에서 정리했다. 테스트 코드 변경은 BlueDeviceFlowTest/LocalFlowTest의 활성 행 대기와 BlueDeviceFlowTest의 비민감 최초 예외 기록뿐이다. 앱 제품 코드는 이번 턴에 변경하지 않았다.

휴대폰에는 새 디자인+최신 금융 코드 APK가 설치되어 있다. 두 전체 실기기 흐름, 실제 금융 연결, 배포 준비는 완료되지 않았다.
