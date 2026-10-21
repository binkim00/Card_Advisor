# 2026-10-20 실제 금융 거래 실적 확인

## 구현
- FinancialPerformanceService/Controller, V20 financial_performance_reviews 및 financial_performance_refund_dates: 사용자별 인정/제외/미확인, 규칙·매핑·원자료 버전, 취소별 접수일 저장. 승인/원장/취소 원문을 수정하지 않음.
- 기존 CardCatalogService에 금융 실적 합산. 이중 연결된 수동 원장은 금융 쪽 한 번만 계산. 예산 포함/카테고리는 실적과 독립. 현재 자격 스냅샷은 변경하지 않음.
- KB Easy all 공식 기준: 승인일 기준 할부 전체 승인액, 취소 전표 접수 월 차감. [공식 상품 안내](https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?cooperationcode=09256&mainCC=a). 사용자 확인에 따른 약관 추정이며 공식 조회값이 아님.
- 미확인 거래/미연결 취소/접수일 누락/미검증 상품의 환불 귀속은 월 전체 합계를 null로 유지. 확인된 부분 합계와 인정·제외·미확인·접수일 미확인 건수 별도 표시. 기존 미기록 실적도 미확인이면 총실적은 미확인.
- 승인/취소 증거·매핑 버전 변경 시 기존 확인 무효화. 낡은 버전 저장409, 사용자별404, 같은 저장 재전송 시 revision 불변. 변경 시 revision으로 기존 추천 무효화.
- FinancialPerformanceEditor: 가계부 거래 상세와 금융 이용내역에서 진입, secure Dialog, 월 선택/조회/인정·제외·미확인/취소별 접수일/명시 확인/저장 후 GET 대조. 오류·충돌 시 입력 보존 및 재조회 전 저장 차단. 결과 불명확 시 자동 재전송하지 않음.

## 통과 근거
- `.run/financial-performance-pg.log`: FinancialSyncPostgresTest **28개 통과**. 신규3개는 결정 상태·현재자격 분리·기초값 미확인·할부 총액·예산독립·수동원장 중복배제·증거/매핑 변경·취소 접수월/별도취소·사용자격리/버전 충돌/중복 조회 포함.
- `.run/financial-performance-package.log`: ActualCardRulesTest2 + PerformanceCalculatorTest9 = **11개 통과**, 실행 JAR 패키징 성공.
- `.run/financial-performance-build.log`: debug/검사용 APK 빌드 성공. 휴대폰에 `install -r` 적용, 데이터 삭제/앱 제거 없음.
- `.run/financial-performance-physical.log` 및 `.run/financial-performance-device.json`: SM-F741N 실기기 → 실제 HTTP Spring API → PostgreSQL **전체 시나리오1개 통과**. CODEF 설정 공백/primary loopback 합성 공급자, 외부 금융 호출0.
- 합성 현재자격 미충족, 미기록450,000 + 승인90,000 = 다음달용540,000. 제외450,000, 미확인null. 부분취소20,000 후 증거변경/접수일 미입력null, 접수일 확인 뒤520,000. 누적전체취소90,000 뒤 재확인450,000. 이번 자격 미충족 유지, 실제혜택 사용액null 유지.
- 실기기 동시수정409 → 기존 입력 보존/저장 차단 → 최신상태 재조회 → 명시 재저장 성공. 기존 수동 구매/부분·전체취소·가상카드 실적/혜택·예산 회귀도 통과.
- 최종 합성 원장2/금융승인1/취소4/순지출0. 개인세션 암호문 전후 불변, 기존10개 표 집계 전후 동일, 합성 계정·슬롯·키 삭제. 개인 거래/인증정보/전체 화면 출력·캡처 없음.
- 검증 중 수정: pending 상태 명시. 검사용 API필드명/기존 펼침 상태/복수창 스크린샷/요청스키마 오류를 바로잡고 최종 전체 시나리오 재실행 통과. 제품의 검증 조건을 완화하지 않음.
- 정상 API PID4336 재시작, Flyway20개 검증, USB18080 복원 및 일반앱 COLD 시작 Status ok. 정상 health200은 최종 실행 확인 결과를 참조.

## 사용 및 검증 경계
1. 가계부에서 금융 거래를 펼쳐 **다음 달용 실적 인정·취소 확인**을 선택. 또는 금융 연결 관리의 이용내역에서 같은 버튼 선택.
2. 카드 약관과 거래를 확인해 인정/제외/미확인 선택. 취소가 있으면 실제 카드사 취소 전표 접수일을 확인해 입력. 모르면 비워 두면 계산 보류.
3. 확인 체크 후 실적 확인 저장. 위쪽 저장 안내 및 아래쪽 다음 달용 총실적/이번 자격을 별도로 확인.
4. 설정 → 월별 실적·자격 수정의 미기록 실적은 저장된 금융 내역과 겹치지 않는 금액만 입력. 미기록 거래가 없음을 확인한 경우에만0으로 입력. 이번 자격의 기준 기간/출처는 별도 관리.

실제 사용자의 거래는 임의 인정·제외하거나 접수일을 채우지 않았다. 미연결 취소1건도 보류 유지. 실제 KB 데이터 수집은 기존 성공 범위를 유지하지만 이번 검사는 새 CODEF 호출/카드사 공식 월실적 대조가 아니다. Hana GO 환불 정책/실제 금융 혜택 사용·잔여한도/배포·스토어는 미완료다.

## 재현
- `.env`를 프로세스에 로드하고 `RUN_FINANCE_PG_TESTS=true`에서 backend `mvn -Dtest=FinancialSyncPostgresTest test`.
- Java21/Android SDK 설정 후 android `gradle :app:assembleDebug :app:assembleDebugAndroidTest`, 기기에 두 APK 덮어설치.
- 같은 DB로 다른 검사를 동시에 실행하지 말고, 잠금 해제 후 `RUN_ANDROID_LEDGER_DEVICE_TEST=true`, `ANDROID_TEST_DEVICE=R3CX701TE5J`, backend `mvn -Dtest=AndroidLedgerDevicePostgresTest test`. 개인 클라이언트는 검사용 서버에 연결하지 않는다. 호스트가 USB 포트/합성계정을 복원·정리하며 검사 뒤 일반 MainActivity로 돌아간다.

최종 정상 실행 확인: health200/서버PID4336/기기앱 실행/USB18080. DB 금융190/원장187/고유187/연결상품1/실사용자검토0/미연결취소1. 근거 .run/financial-performance-runtime.json. 실제 사용자 거래의 인정·제외를 대신 선택하지 않았습니다.
