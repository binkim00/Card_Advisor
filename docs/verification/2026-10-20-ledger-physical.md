# 2026-10-20 실기기 가계부 검증

## 개인 로그인·거래 보존
- LedgerVerificationActivity는 debug에만 포함되는 비공개 Activity이며 검사용 ApiClient를 명시적으로 전달한다. MainActivity와 같은 AdvisorApp 화면/상태/요청 코드를 사용하며 정상 앱은 기존 persistent client를 유지한다.
- 테스트 슬롯 device-ledger-UUID만 사용하고 제거. 개인 session-v1 암호문은 복호화/복사/출력 없이 메모리 내 해시를 전후 비교, 불변 확인. 개인 계정으로 테스트 API에 요청하거나 새 테스트 토큰을 개인 슬롯에 쓰지 않음.
- 실기기 검사 전 일반앱 종료 → 테스트 API로 해당 USB 포트 전환 → 검사 → 합성계정 삭제/테스트슬롯·키 삭제 → USB18080 원래 API 복원 → 일반앱 재시작.
- 기존 app_users/cards/card_month_states/budgets/ledger_entries/cancellations/financial_transactions/financial_card_mappings의 서버 내부 집계 해시 전후 동일. 해시/개인ID/원문/인증정보를 결과에 출력하지 않음.

## 검증 흐름
- SM-F741N R3CX701TE5J, 실제 Android 터치/입력 및 Compose 화면 → 실제 Spring HTTP API → 실제 PostgreSQL. CODEF는 설정 파일 공백과 primary loopback 공급자로 대체, 외부 CODEF 호출0.
- 가입/가상데이터 준비 후 가계부에서 B카드 수동 구매50,000원 생성. 카드명/금액/결제 상태와 예산50,000원/실적320,000원/혜택500원/잔여9,500원 확인.
- 같은 가계부 화면에서 부분취소20,000원: 순지출30,000원/부분취소, 예산30,000원/실적300,000원/혜택300원/잔여9,700원. 이어 전액 취소: 순지출0원/전체취소, 예산0원/실적270,000원/혜택0원/잔여10,000원. 현재 혜택 자격은 유지.
- 휴대폰에서 합성 KB 승인90,000원을 API로 가져와 테스트 상품을 명시적으로 연결. 가계부 가맹점 ‘테스트 온라인스토어’/KB Easy all 상품명/끝자리123/90,000원 확인.
- 동일 승인 부분취소20,000원/누적전체취소90,000원 응답을 휴대폰에서 각각 가져오고 가계부 새로고침: 70,000원 부분취소→0원 전체취소 표시, 예산90,000→70,000→0원. B카드 실적270,000원 유지, 실제 상품의 미검수 실적·혜택은 null 유지.
- 화면의 예산·실적 상세 값과 API 응답 및 최종 DB 대조. 원장2/금융승인1/취소4/합계취소140,000원/순지출0, 중복원장없음. 공급자 요청4회(연결1+조회3), 모두 loopback.

## 근거와 결과
- `.run/ledger-device-build.log`: debug/검사APK 빌드.
- `.run/ledger-device-physical.log`: AndroidLedgerDevicePostgresTest 통과.
- `.run/ledger-device-R3CX701TE5J.json`: 기기/각 단계/개인세션 보존/기존DB불변/합성계정삭제/최종DB수치. 개인 원문·개인 화면·전체 UI트리·인증정보 미수집. 실기기의 격리 합성 앱 영역만 ledger-device-preview/financial-{partial,full}.png로 캡처해 별도로 시각 검토했다.
- 새 검증 전용 진입을 위한 clientOverride 의존성 전달과 debug호스트를 추가했다. 기존 계산·금액·DB·정상 로그인 경로 변경 없음. 첫 실행의 취소 확인창 스크롤 시도는 검사 코드 문제로 수정했고, 최종 실제 표시 assertion/합성 화면 캡처를 포함해 전체 경로를 재실행해 통과했다.

## 범위
실기기→API→DB 검증 완료이며 실제 금융 결제/취소나 CODEF를 새로 호출한 근거는 아니다. 개인 계정 거래를 변경하지 않았고 미연결 취소도 그대로 유지한다. 공개 배포/스토어 검증은 별도다.

검증 종료 복원: /v1/health200, USB18080→18080, 일반 MainActivity COLD 시작 Status ok/앱PID22866. 합성 부분·전체취소 PNG에서 카드/가맹점/금액/상태 배치와 잘림을 확인했다. 이번 검사범위에서 제품 표시·계산 결함은 재현되지 않았다.
