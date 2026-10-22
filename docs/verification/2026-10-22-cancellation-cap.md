# 취소와 한도 복원 분리 — 2026-10-22

[KB 공식 안내](https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?cooperationcode=09256&mainCC=a)는 취소 후 한도 복원이 즉시 이루어지지 않을 수 있음을 명시한다. [하나 GO 계좌형 설명서](https://m.hanacard.co.kr/leaflet/15/15252_20250115.pdf)의 취소 접수월 적립금 차감은 즉시 한도·횟수 복원 보증이 아니다. 동일 카드명처럼 보이는 신세계 신용/선불형 및 이벤트 실적 규칙을 이 상품에 이식하지 않았다.

수정: 예상 혜택 사용액은 순구매 기준 재계산하되, 추천이 사용하는 잔여 한도는 취소 전 사용까지 고려한 보수적 값이다. `recalculatedRemainingCapWon`와 `unconfirmedRestorationWon`을 분리해 앱에 복원 미확인을 표시한다. 공식 복원값을 수집한 것으로 꾸미지 않는다. 체크카드 할부 자료는 일시불 전용 실적 규칙으로 인정하지 않으며 원장·예산은 보존한다.

KB 합성 9만원 음식 결제: 2,700원 예상 → 2만원 부분취소 후2,100원, 재계산 잔여13,900원과 사용 가능 보수적 잔여13,300원/미확인 복원600원을 분리. 전체 취소 예상0원이어도 복원2,700원은 확정하지 않는다. 하나GO 2만원→5천원 취소는 예상750원·보수적 잔여4천원/미확인 복원250원.

검증: 금융·실적·혜택 PG41 통과. 전체 backend300 통과(실패/오류/skip0), Android JVM15·lint 오류0·debug/androidTest/미서명release 빌드 통과. 에뮬레이터7검사묶음(앱9흐름), 부분/전체취소 안내·추천·예산·실적·추가인증·자격 화면 저장/충돌 회귀 통과. `.run/financial-final-backend.log`, `.run/financial-final-android.log`, `.run/financial-final-emulator.log`. 합성 공급자만 사용, 실제 금융·휴대폰 호출0.
