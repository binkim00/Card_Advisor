# CODEF 거절 진단 및 CF-12803 확인

- 공식 CODEF DEMO 오류 화면(/dashboard/demo/error)에서 2026-10-20 10:07:45 카드 개인 계정등록 요청: CF-12803, 아이디 또는 비밀번호 오류. 거래 식별자/요청 원문/금융 인증정보는 수집하지 않았다.
- OAuth 키 인증 성공과 카드사 로그인 실패를 구분한다. 해결에는 사용자가 KB카드 홈페이지에서 실제 로그인 가능한 ID/PW를 확인해야 한다. 비밀번호 변경/찾기는 사용자 직접 수행.
- 기존 코드는 result.code를 일반 CODEF_REJECTED로 축약해 세부 코드를 폐기했다. CodefFailure와 계정 errorList 처리에서 CF-[0-9]{5}만 선별하여 사용자 소유 connection/operation의 provider_code에 저장한다. 성공한 재시도는 현재 오류를 지우고 과거 작업 코드는 유지한다. 원문 message/extraMessage 및 요청값은 저장하지 않는다.
- V16 마이그레이션: 두 테이블에 nullable provider_code와 DB 형식 제약 추가. API connection 및 operation에 nullable provider_code 추가. 기존 레코드는 null을 유지하며 공식 사이트에서 본 값을 API 응답처럼 소급 기록하지 않는다.
- Android는 형식 검증된 세부 코드만 표시. debug APK 빌드 및 SM-F741N 업데이트 성공. Android unit task는 NO-SOURCE로 단위 검사 성공 근거로 사용하지 않는다. 새 실패 코드의 실기기 표시는 사용자 재시도 전 미검증.
- CodefClientContractTest 43개 및 FinancialConnectionsPostgresTest 20개, 총63개 통과. 신규 검사: 오류 메시지 비노출, 비정상 코드 폐기, 계정별 errorList 코드 추출, 실제 HTTP/PostgreSQL 저장/재조회/사용자 격리/재시도 초기화/과거 이력 유지. 외부 공급자는 명시적 loopback, 실제 키 설정 파일은 공백으로 대체.
- 실행 서버 PID51928, V16 적용 확인(안전 DB 검사에서 provider_code 조회 성공). 실데이터 상태는 FAILED/version6/CID없음/동기화0으로 보존. 실제 금융 인증이나 자동 재요청은 수행하지 않았다.
- 근거: .run/codef-diagnostic-tests.log, .run/codef-diagnostic-package.log, .run/codef-diagnostic-android.log, .run/codef-live-state.json.