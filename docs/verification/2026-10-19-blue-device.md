# 2026-10-19 새 디자인 실기기 회귀 검증

## 결과
- 기기: Samsung SM-F741N / Android17 / USB. 수정한 새 디자인 APK 설치 완료.
- 실행 ID: 887dcfb4-ef5b-47e0-8f1a-a9edd1d8070f.
- 회원가입→가상 카드 준비→추천→카드 선택→수동 구매→부분 취소→추천 무효화/재계산→월별 값 저장→탐색→로그아웃/재로그인→재조회까지 실제 MainActivity→로컬 API(18080)→PostgreSQL 검증 통과.
- 가상 카드 A 초기 추천 혜택1,500원. B로5만원 구매 후2만원 부분 취소: DB 순지출3만원, 카드 승인5만원/환불2만원, 다음 달용 실적30만원, 현재 혜택 자격 유지, 혜택 사용300원/잔여9,700원. 다음5만원 구매 추천의 전체 예산4만원/카테고리−1만원/고정비 제외1만원 확인.
- 재로그인 후 거래/실적/혜택 재조회 일치. 최종 UI 결과와 DB를 대조한 뒤 해당 합성 계정만 삭제하고0건 확인.
- 같은 APK의 DesignUiTest3개도 휴대폰에서 통과. 합성 홈/공식 이미지 배치, 탐색 busy 활성화/복구, 로그인 입력/버튼 접근, 미조회 예산 표시 확인.
- 실제 앱 자체의 합성 화면5장을 확인했다. 해당 기기의 기본 배율/세로 화면에서 확인한 텍스트·버튼·카드 이미지가 겹치지 않았고, 긴 내용은 스크롤/줄바꿈으로 표시됐다. 모든 배율/회전/기종의 적합성을 의미하지 않는다.

## 재현 결함과 수정
- 최초 APK cd782c5...의 실행 f087fb35-a17b-458d-b566-cea8fc7c6428에서 월별 저장 후 가계부 이동이15초 대기 초과로 실패했다.
- 저장 버튼 enabled=1, 설정/가계부/나가기 enabled=0 관측. DB 구매50,000·취소20,000·월별 버전[1,2]로 저장 완료 확인. 서버 장애/저장 미완료가 아니라 지속된 탐색 비활성 상태를 재현했다.
- AdvisorShell의 Scaffold/내용 슬롯에서 전달된 Boolean 값을 사용하던 탐색 enabled/로딩 표시를, 루트의 단일 busyState를 해당 슬롯에서 직접 관찰하도록 변경했다. MonthlyStateEditor와 요청/계산 로직은 변경하지 않았다.
- 수정 후 같은 실기기 전체 흐름 및 상태 전환 UI 검사 통과. Compose 내부 원인 또는 이전 SlotTable 오류 해결로 확대해 주장하지 않는다.
- 실패 계정도 정확한 실행 이메일1개만 정리하고0건 확인. 실패 단계 증거는 outputs/blue-device-20261019/first-failure에 보존.

## 충돌/개인정보 경계
- 다른 채팅은 CODEF 백엔드 동기화 작업 중임을 읽기 전용 확인했고, 기기 instrumentation이 없는 상태에서 설치/검사를 시작했다. 별도 Android 사본과 빌드/증거 폴더 사용.
- 원본 MainActivity/AdvisorDesign/DesignUiTest는 기준 SHA-256 일치 시만 반영. 기존 LocalFlowTest·금융 테스트·백엔드 코드·마이그레이션·서버 프로세스·Docker 볼륨을 수정하거나 재시작하지 않았다.
- 공유 DB에는 이번 고유 합성 계정의 데이터만 생성/조회/정리했다. 다른 사용자/다른 채팅 검사 데이터에는 접근하거나 수정하지 않았다.
- 캡처는 인증 입력이 없는 테스트 계정의 Compose 앱 영역뿐이다. 전체 기기 화면·접근성 트리·금융 인증정보·원문 instrumentation 출력은 저장하지 않았다.

## 결과물/재현
- outputs/blue-device-20261019/card-advisor-debug.apk : 기기에 설치된 수정본과 SHA256 일치.
- 같은 폴더 card-advisor-androidTest.apk, unsigned release APK, verification.json, DB/UI 집계와 화면5장 보존.
- 설치 APK SHA256: a2f4a6090ca8099f723c46ecfc442db7d8352a0e67522aa27b1f856c837899ef.
- 다음 명령은 APK2개를 설치하고 실제 API/DB에 고유 합성 계정을 만든 뒤 정리한다. 휴대폰 잠금 해제·PC API/PostgreSQL 실행·다른 기기 검사 없음이 전제다.

```powershell
.\scripts\android-blue-device-test.ps1 -Device R3CX701TE5J -AppApk .\outputs\blue-device-20261019\card-advisor-debug.apk -TestApk .\outputs\blue-device-20261019\card-advisor-androidTest.apk
```

## 범위
이번 요청의 실기기 흐름은 완료. 실제 금융기관 연결/실거래·쇼핑몰 주문 감지·공개 배포·스토어 승인은 포함하지 않는다. 금융/예산/고정비의 모든 새 디자인 실기기 회귀는 이번 흐름의 성공으로 대체하지 않는다.
