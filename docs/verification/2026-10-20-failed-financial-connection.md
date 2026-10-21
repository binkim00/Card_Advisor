# 실패 금융 연결 수정·기록 삭제 — 2026-10-20

## 원인과 구현
- 실제 KB0301 연결은 CREATE 작업 FAILED, Connected ID 없음, CODEF_AUTHENTICATION. 기존 UI는 CID가 있는 ACTIVE/AUTH_REQUIRED에만 재인증/해제를 허용해 확정 실패에서 수정·삭제 동작이 없었다.
- Android 금융 실패 카드에 인증정보 수정·다시 연결, 실패 기록 삭제 확인을 추가. 저장된 비밀번호는 없으며 매번 새 입력과 동의를 받는다. 오류 코드와 키/토큰 인증 가능성을 구분해 표시한다.
- POST /finance/connections/{id}/retry: 소유권, 현재 공급자 환경/프로젝트, FAILED/CID없음, expectedVersion, 명시 동의 검사. 기존 CREATE 작업 처리와 암호화/중복키 방지 사용. 이전 성공 계정의 update와 초기 실패의 create를 혼동하지 않는다.
- POST /finance/connections/{id}/archive: CID없는 확정 초기 CREATE 실패·동기화 없음만 가능. V15 archived_at으로 목록에서 숨기고 요청 이력을 보존한다. 외부 해제 요청0. 중복 삭제 안전, 원래 요청 키 재조회는 외부 재요청/복원 없음. 같은 기관을 새로 추가하면 기존 기록을 명시적으로 복원한다.
- UNKNOWN/진행/활성/추가 인증은 실패 삭제로 우회하지 않는다. 버전 충돌409/타 사용자404. 실제 사용자 기록은 자동 삭제/재시도하지 않았다.

## 검증
- FinancialConnectionsPostgresTest19개, CodefClientContractTest37개, FinanceStatusControllerTest1개 총57개 통과(변경 영향에 따라 분리 실행). 합성 공급자는 설정 공백+loopback 강제 대체. 실제 키/카드사 인증정보를 테스트에 사용하지 않았다.
- debug/test APK 빌드·SM-F741N 설치. FailedConnectionFlowTest → 실제 HTTP API → loopback 공급자 → PostgreSQL 통과: 5314eda9-3b5e-45ff-a9c9-9b5e359ee8ac.
- 실패→인증 재입력→연결됨→외부 해제, 별도 실패→확인 후 목록 삭제→재조회 후 숨김을 UI로 확인. 합성 공급자4회/작업 이력4개 유지/실패 기록 archive1/삭제의 외부 요청0. 정확한 합성 검사 계정 정리, USB18080과 일반 앱 시작 복원.
- 최초 실기기 검사는 USB 분리로 시작 실패했으며 연결 복구 후 위 검사가 통과했다. 그 시도를 제품 기능 실패로 합산하지 않는다.

## 실제 CODEF 인증 확인
- POST /finance/codef/authentication-check: 앱 로그인 필요, CODEF OAuth 토큰 인증만 명시적으로 확인. 토큰·키·응답 원문을 반환하지 않으며 카드사 로그인이나 계정 생성/조회를 수행하지 않는다.
- 실제 공식 OAuth 호출 결과 HTTP401/CODEF_AUTHENTICATION. 카드사 인증 요청 없이 재현했으므로 KB 비밀번호 오류라고 단정할 수 없다. 데모 Client ID/Secret의 동일 환경 키 쌍·이용 상태 확인이 필요하다. 구체적으로 어떤 키/계정 설정이 틀렸는지는 미확정이다.
- 토큰 발급의400/401,403,429,5xx를 인증/접근/호출제한/통신 장애로 구분하도록 보완. 예외에는 고정 코드와 HTTP 숫자만 포함한다.
- 실제 KB 실패 기록은 FAILED/version2/CID없음/archived=false로 보존. 실제 금융 연결·조회 성공 없음. 사용자에게 로컬 데모 키 확인을 요청했고 비밀값은 받지 않는다.

## 근거
- .run/failed-connection-tests.log, failed-connection-oauth-tests.log, failed-connection-build.log
- .run/failed-connection-ui.json, failed-connection-ui.log
- .run/codef-demo-readiness.json, codef-live-state.json
- docs/api/openapi.json 및 V15. 새 JAR 반영·API 기동. 공개 배포/스토어 승인과 무관하다.
