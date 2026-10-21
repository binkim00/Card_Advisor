# 반복 고정비 종료 안정성 점검 · 2026-10-19

## 결론의 범위
- 종료 중 취소된 조회 작업이 이미 제거된 화면의 부모 상태를 다시 쓰는 결함을 통제된 지연 응답으로 재현했다. 기존 구현에서 제거 후 busy 콜백1회, 기대값0으로 실패했다.
- FixedCostEditor의 예외 처리 및 finally에서 취소 여부를 확인하여 취소된 작업의 오류 표시·부모 상태 변경을 차단했다. Compose/컴파일러 버전은 변경하지 않았다.
- 최초 실행의 ArrayIndexOutOfBoundsException / SlotWriter.moveSlotGapTo와 이 결함의 인과관계는 확인되지 않았다. 원래 예외가 해결되었다고 확정하지 않는다.

## 재현과 검증
- 수정 전 유효 재현: d769e6b6-75f9-4c94-a653-08fc98499c9c. 두 검사 중 지연 조회 취소 검사가 실패(lateCallbacks=1), 실제 API에 연결한 화면12회 진입/편집/이동/종료·4회 재생성은 통과했다.
- 지연 응답 검사는 테스트 기기 안의 loopback HTTP 서버를 사용하며 실제 금융/API 성공 근거가 아니다. 화면을 제거한 뒤 응답을 해제하고1초 동안 잘못된 상태 변경이 없는지 확인한다.
- 생명주기 검사는 debug 전용 비공개 Activity에 실제 FixedCostEditor를 표시한다. 실제 API 경로에서는12회 진입/편집/화면 이동/거래 선택/종료 및4회 Activity 재생성을 수행한다. 미저장 편집값31,000/32,000원이 저장값30,000원을 덮어쓰지 않아야 한다.
- 실제 MainActivity의 고정비·예산·추천은 기존 FixedCostFlowTest/BudgetFlowTest로 따로 확인한다. debug harness만 통과한 것을 전체 앱 검증으로 대신하지 않는다.
- 종료 과정에 예외가 나도 검사 본문의 최초 예외를 failure.txt에 따로 보존한다. 지연 응답과 실제 API 검사는 구분해서 기록한다.
- 중간 진단62230236-07c7-4521-9981-f8567a566ed3는 새 APK 설치와 겹쳐 프로세스가 중단된 실행이다. 제품 오류 재현 또는 성공 근거에서 제외한다. 첫 진단b3df5e23-3b16-4859-87f5-ee42959674a4는 debug 네트워크 설정을 누락한 검사 설정 오류였으며 원래 localhost 전용 설정을 복구한 뒤 진행했다.

## 수정 위치
- android/app/src/main/java/dev/cardadvisor/android/FixedCostEditor.kt: 취소 예외 전파 및 활성 작업만 상태 갱신.
- android/app/src/debug/java/dev/cardadvisor/android/FixedCostLifecycleActivity.kt 및 debug/AndroidManifest.xml: release에 포함되지 않는 생명주기 검사 화면. 외부 호출 불가, 기존 localhost 전용 네트워크 설정 유지.
- android/app/src/androidTest/java/dev/cardadvisor/android/FixedCostLifecycleTest.kt: 지연 응답 취소·반복 화면 생명주기 검사 및 최초 예외 기록.
- scripts/android-lifecycle-smoke.ps1: 설치·실행·DB 대조·정확한 합성 계정 정리.
- scripts/android.ps1: lifecycle-test 연결 및 budget-test 조건에서 잘못된 배열 비교 제거. 예산 검사 명령이 실제 검사를 호출하는지 별도 실행한다.

## 재현 방법
기존 PostgreSQL/API와 emulator-5554가 실행된 상태에서 순서대로 실행한다. 같은 기기에서 검사와 APK 설치를 동시에 실행하지 않는다.

```powershell
./scripts/android.ps1 lifecycle-test
./scripts/android.ps1 fixed-test
./scripts/android.ps1 budget-test
```

검사 결과는 .run/lifecycle/<runId>, .run/android-fixed/<runId>, .run/android-budget/<runId>에 저장된다. 실패한 합성 계정은 진단을 위해 보존되며 확인 후 해당 ID만 정리한다. 개인/기존 데이터·Docker 볼륨·마이그레이션은 변경하지 않는다.

## 남은 범위
최초 SlotTable 예외의 정확한 재현 조건과 최초 원인은 미확정이다. 물리 휴대폰, 실제 금융 연동, 배포 준비, 공개 배포, 스토어 승인은 확인하지 않았다. 전체 FR-01~FR-24 구현 완료를 뜻하지 않는다.

## 수정 후 실제 결과
- Android debug/검사 APK 빌드 성공. 변경 없는 백엔드 단위/API 전체 검사는 반복하지 않았으며 아래 Android→실제 API→PostgreSQL 검증을 수행했다.
- 생명주기2개 통과(95.298초): d636fafe-cb79-4469-b914-a7ca445b68ab. 취소 후 콜백0, 진입/편집/이동/종료12회, 재생성4회. DB 일정1/저장액30,000/가계부0 유지.
- MainActivity 고정비 전체 흐름1개 통과(41.856초): 18a87a4c-5164-4843-aa4c-59c65153b72b. 생성·기준 전환·거래 연결·부분 취소·연결 해제/재연결·편집·충돌·다음 월·중지/재개·예산 이동·재로그인·추천 확인.
- MainActivity 월별 예산 흐름1개 통과(31.003초): e9af90e1-fb38-45e4-a04a-24eb8f336433. 수정한 android.ps1 budget-test 명령으로 실제 검사가 실행됐으며 화면·API·DB를 대조했다.
- 고정비 DB: 이번 월60,000/다음 월80,000, 일정 버전10, 연결1건, 구매50,000−부분 취소20,000=순지출30,000. 이번 월 예약30,000/다음 월80,000, 수동 입력30,000/0 보존. 다음50,000 결제 추천의 전체잔액40,000/쇼핑−10,000/고정비 제외10,000. B 카드 실적300,000·혜택300·한도9,700 유지.
- 예산 DB: 이번 월 전체240,000/쇼핑100,000/식비80,000/수동 고정비20,000, 다음 월 전체50,000/식비20,000/기타0. 다음50,000 쇼핑 추천의 전체160,000/카테고리20,000/고정비 제외140,000 확인.
- 각 정상 실행의 합성 계정 정리 완료. 실패/중단된 진단 계정2개도 DB 상태 보존 후 정확한 계정만 삭제·잔여0 확인(.run/lifecycle-diagnostic-cleanup.json). 기존 DB 볼륨·V1~V8 유지.
- APK SHA256: CFC9A78CA1C3E76D665F20E92D8FD8FC205CAE775CB6312C2C7448CFD1EEDDB4
- 증거: .run/lifecycle/d636fafe-cb79-4469-b914-a7ca445b68ab, .run/android-fixed/18a87a4c-5164-4843-aa4c-59c65153b72b, .run/android-budget/e9af90e1-fb38-45e4-a04a-24eb8f336433. 초기 재현의 cancellation.json lateCallbacks=1과 수정 후0을 대조한다.
