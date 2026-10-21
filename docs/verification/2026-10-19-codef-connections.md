# CODEF 사용자별 연결·암호화 저장 검증 — 2026-10-19

이번 범위의 백엔드 구현과 합성 공급자/실제 PostgreSQL 검증을 완료했다. 실제 CODEF·금융기관 인증, Android 금융 연결 화면, 거래 동기화는 완료하지 않았다.

## 구현
- POST /v1/finance/connections: 기관·동의·ID/PW로 생성. Connected ID를 클라이언트가 지정할 수 없다.
- GET /v1/finance/connections 및 /{id}: 본인 연결 목록/상태 조회. 공급자 호출 없이 저장 상태를 반환한다.
- POST /v1/finance/connections/{id}/reauthorize: 저장된 본인 Connected ID로 ID/PW 인증수단 갱신.
- POST /v1/finance/connections/{id}/disconnect: 공급자 계정 삭제 성공 확인 후 암호문/식별 해시 제거.
- GET /v1/finance/operations/{id}: 본인 요청 결과 조회.
- 모든 변경에 UUID Idempotency-Key, 재인증·해제에 expectedVersion 필요. 사용자별 행 잠금으로 DB 요청 기록을 먼저 커밋하고 외부 요청은 트랜잭션 밖에서1회 실행한다.
- 같은 키/같은 본문은 기존 작업을 반환하고, 본문 변경은409. 다른 키로 같은 기관을 동시에 생성하거나 확인 필요 상태를 재시도하면409. 재전송의 operation은 원래 결과, connection은 현재 상태를 반환한다.
- HMAC 요청 지문만 저장하며 로그인 ID/비밀번호·요청 원문은 저장하지 않는다. 개인카드 연결은 사용자/환경/CODEF 프로젝트/기관별1개 레코드를 재사용한다.
- AES-256-GCM, 임의12바이트 nonce,128비트 태그. 사용자/연결/환경/프로젝트/기관/키 ID를 인증 데이터에 포함한다. DB에는 키 ID·nonce·암호문·중복 소유권 검사용 해시만 저장한다. 같은 환경/프로젝트의 같은 Connected ID를 다른 연결에 배정하지 못한다.
- 실제 키는 서버 로컬 파일에 있고 DB/저장소/응답에 없다. 키 교체 시 이전 키를 남겨 이전 암호문/요청 지문을 읽을 수 있다.
- 결과 불명확 UNKNOWN, 추가 인증 AUTH_REQUIRED, 진행 중 PENDING을 성공과 구분한다.2분이 지난 미완료 작업은 조회 시 UNKNOWN으로 전환하며 자동 재실행하지 않는다. 동일 작업의 늦은 확정 결과만 반영할 수 있다.
- 연결·불명확한 작업이 남아 있으면 계정 삭제409. 해제 확인 없이 로컬 데이터만 삭제해 외부 연결을 잃지 않도록 했다.
- V9 추가. V1~V8 파일/기존 DB/볼륨은 보존했다.

## 검증 근거
1. 초기 전체101개(기존 계산52, CODEF 통신31, 상태 API1, 암호화4, PostgreSQL13) 통과: `.run/finance-connections-tests.log`.
2. 재연결 동의 시각이2000년 과거값을 재사용하는 결함을 단일 검사로 재현: `.run/finance-reconnect-before.log`의 실패1개. 원인은 기존 레코드를 재사용하면서 새 동의 시각을 갱신하지 않은 것. 생성 예약 트랜잭션에서 새 동의 버전/시각을 기록하도록 수정.
3. 추가한 성공 재인증·해제 재전송, 실제 공급자 응답 지연, 재연결 동의 검사를 포함해 최종 PostgreSQL16개 전부 통과: `.run/finance-connections-final.log` 및 `backend/target/surefire-reports/TEST-dev.cardadvisor.finance.FinancialConnectionsPostgresTest.xml`. 변경 영향 범위를 재실행했으며 초기101개와 별도로 모두104개를 한 번에 돌렸다고 주장하지 않는다.
4. 최종 JAR 빌드·로컬18080 서버 반영 완료. 기존 수동 입력·추천·구매·부분 취소·계정 삭제 API 회귀16개 통과(scripts/api_smoke.py).
5. 실행 중 API에서 미인증401, 안전한 설정 조회, 본인 빈 목록, CODEF 비활성503 및 미생성, 계정 정리 확인: `.run/finance-connections-runtime.json`.
6. PostgreSQL V9 성공1, 합성 finance 계정0, 연결0, 작업0 확인. 모든 검사 계정만 정리했고 실제 금융 호출0. 런타임 저장 키는 구성됐지만 CODEF enabled=false, userConnectionApiReady=false, realConnectionVerified=false.

PostgreSQL16개는 실제 Spring HTTP 서버/앱 인증 필터/Flyway/JDBC/PostgreSQL과 로컬 합성 CODEF HTTP 서버를 사용했다. 단순 mock DB 검사가 아니다. 암호화 키와 CODEF 자격·기관 로그인은 검사 메모리의 합성값이다.

검사에는 사용자 간 조회/변경404, 다른 사용자의 요청 키 분리, DB 복합 외래키 차단, 동시 동일 요청의 외부 호출1회, 본문 변경409, 버전 충돌409, 전송 후 응답 시간초과 UNKNOWN, 재인증/해제 불명확 시 암호문 유지, 부분 성공 보류, 추가 인증 중단, 동일 Connected ID 중복 소유 차단, 오래된 PENDING 복구, 암호문 변조 차단, DB/응답 원문 비노출, 계정 삭제 차단/해제 후 삭제가 포함된다. 프로세스 강제 종료 재현 대신 DB에 남은 오래된 PENDING 상태를 주입해 복구 로직을 검사했다.

## 실행과 제한
PostgreSQL 실행 후 `./scripts/backend.ps1 finance-test`. 합성 HTTP 공급자만 사용하므로 실제 인증정보는 필요 없다. 일반 test에서는 PostgreSQL 통합 검사가 기본 제외되며 finance-test가 명시적으로 활성화한다.

`UNKNOWN`을 임의로 성공/실패로 바꾸는 API는 없다. 해당 연결의 원래 결과를 CODEF 기록·읽기 조회 등으로 확인하는 복구 절차는 후속 작업이다. Connected ID 없는 AUTH_REQUIRED도 추가 인증 이어하기 구현 전 보류한다. 현재 재인증은 ID/PW update이며 간편인증·인증서·OTP 이어하기를 완료한 것으로 주장하지 않는다. 실제 KB/하나 기관별 지원·최신 필드·계약 조건 확인은 남아 있다.

사용자 준비와 로컬 키 경로는 [CODEF 안내](../integrations/CODEF.md). Android 금융 연결 화면·실제 기기·실금융·배포 준비/공개 배포/스토어 승인은 미검증이다.
