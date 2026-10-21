# CODEF 통신 계층과 실제 연결 준비

2026-10-19 기준. 통신 어댑터와 사용자별 연결 생성·조회·재인증·해제 API, Connected ID 암호화 DB 저장을 구현하고 합성 공급자/실제 PostgreSQL로 검증했다. Android 금융 연결 화면도 합성 응답으로 앱→API→DB 검증했다. KB·하나 승인 동기화 백엔드도 구현해 합성 응답/실제 PostgreSQL로 검증했다. Android 동기화 화면은 합성 응답으로 검증했다. 신규 실기기·추가 인증 이어하기·실금융 검증은 미완료다. 상세는 [승인 정규화 계약](CODEF-APPROVALS.md)을 따른다. 로컬 시험 서버 성공을 CODEF 또는 금융기관 연결 성공으로 표시하지 않는다.

## 구현과 확인 범위
| 기능 | 구현 범위 |
|---|---|
| OAuth | client_credentials, scope=read, Basic 인증, Bearer 전송, expires_in 기반 메모리 재사용 |
| 계정 | 개인카드 ID/PW 공통 요청의 create/add/update/delete, 기관 비밀번호 RSA 암호화, successList/errorList 분리 |
| 조회 | account/list, 개인 card-list·approval-list의 공식 예제에 나온 경로·필드로 요청 |
| 실패 | 읽기 요청의401/CF-00401만1회 토큰 갱신,403/429/추가 인증/거절/형식 오류 구분 |
| 불명확한 변경 | 시간초과·서버 장애·잘못된 성공 응답을 UNKNOWN_OUTCOME으로 분리, 자동 재실행 없음 |
| 추가 인증 | CF-03002이면 AUTH_REQUIRED로 중단. 기관별 인증 이어하기는 미구현 |
| 결과 | KB·하나 승인내역은 정규화/보류 판정 후 명시적 동기화 API로 원장 반영. 계정/보유카드 미매핑 응답을 거래로 간주하지 않음 |
| 설정 | 기본 비활성, SANDBOX/DEMO/PRODUCTION 분리, 공식 호스트 고정, 임의 URL/리다이렉트 금지 |
| 자체 API | 로그인 필요 상태 조회 및 사용자별 연결/작업 API. 실제 금융 연결 검증 여부는false |

승인 조회는 공식 KB/하나 기관 코드·필드·취소·할부 계약에 따라 정규화한다. inquiryType=1, orderBy=1, memberStoreInfoType=0을 사용한다. 카드번호/비밀번호 및 추가 인증이 필요한 실제 기관 경로는 미검증이다. 보유카드 상품 매핑·공식 실적/혜택 계산은 별도 미구현이며 카드 결제 가능 한도를 혜택 잔여 한도로 사용하지 않는다.

## 공식 근거와 제한
2026-10-19 웹에서 확인했다. 현재 공개된 공식 SDK/샘플 소스에 근거하며 오래된 샘플이 기관별 최신 계약 전체를 보증하는 것은 아니다.

- [공식 Java SDK Connector](https://github.com/codef-io/easycodef-java/blob/master/src/main/java/io/codef/api/EasyCodefConnector.java): OAuth 인증, 폼 인코딩, URL 인코딩 JSON, 토큰 거절 처리.
- [공식 환경·계정 경로](https://github.com/codef-io/easycodef-java/blob/master/src/main/java/io/codef/api/EasyCodefConstant.java): 환경별 호스트와 create/add/update/delete/list.
- [공식 계정 관리 예제](https://github.com/codef-io/codef-java): accountList, 인증수단, successList/errorList, connectedId.
- [공식 RSA 구현](https://github.com/codef-io/easycodef-java/blob/master/src/main/java/io/codef/api/EasyCodefUtil.java): X509 RSA 공개키와 Base64. 본 구현은 Java 기본 RSA 패딩을 명시한 RSA/ECB/PKCS1Padding, UTF-8을 사용한다.
- [공식 개인 카드 목록 예제](https://github.com/codef-io/codef-python/blob/master/sample/cd/TestKR_CD_P001.py), [공식 승인내역 예제](https://github.com/codef-io/codef-python/blob/master/sample/cd/TestKR_CD_P002.py): 경로·요청 필드명 확인. 값의 선택지/기관별 지원/응답 의미는 별도 확인 필요.
- [공식 추가 인증 예제](https://github.com/codef-io/easycodef-java): CF-03002/continue2Way. 자동 인증 이어하기는 사용하지 않는다.
- [공식 v2 SDK](https://github.com/codef-io/easycodef-java-v2) 존재 확인. 이번에는 SDK 의존성을 추가하지 않고 기존 Java17 HttpClient/Jackson으로 작은 어댑터를 작성했다. SDK 소스의 원문 출력·무차별 재시도 동작을 가져오지 않는다.
- 이전 통신 단계에서 동적 본문을 확보하지 못했던 [개발가이드 승인내역](https://developer.codef.io/products/card/common/p/approval)·카드 개요·연결된 공식 취소 표는 동기화 단계에서 브라우저로 확인했다. [Connected ID](https://developer.codef.io/common-guide/connected-id/cid) 상세 동적 본문과 [요금](https://codef.io/pricing/overview)·계약·호출 제한은 확인 완료로 표시하지 않는다. 호출량/데모기간/본인 조회 허용을 과거 기억으로 확정하지 않는다.

## 실제 연결을 위해 사용자가 준비할 사항
1. CODEF 계정/프로젝트에서 사용할 환경을 선택하고, 본인 금융자료 조회 허용과 필요한 상품 권한을 확인한다. DEMO를 가상 데이터로 가정하지 않는다. 유료 계약/결제는 이번에 진행하지 않았다.
2. KB국민카드·하나카드의 개인카드 승인 공식 기관코드·필드·취소 표는 구현 문서에서 확인했다. 신청 환경의 상품 권한, 실제 인증 방식·필수 추가 입력, 이용 조건/호출 제한은 사용자가 확인해야 한다. 민감값을 제거한 공식 문서 또는 링크만 전달하면 된다.
3. 프로젝트의 `config/codef.example.properties`를 `.local/codef.properties`로 복사한다. CODEF 프로젝트에서 발급된 clientId/clientSecret/publicKey를 로컬 파일에만 입력한다. 금융기관 로그인 비밀번호·인증서·OTP·Connected ID는 이 설정 파일이나 채팅에 넣지 않는다. `enabled=false`, `usageApproved=false`를 유지하고 준비된 실제 연결 절차에서만 활성화한다.
4. 실행 환경 변수 CODEF_CONFIG_FILE에 해당 파일의 절대 경로를 지정한다. 예: `C:/Users/SSAFY/Documents/card-advisor/.local/codef.properties`. 기존 scripts/backend.ps1은 .env의 이 변수도 읽을 수 있다. 실제 비밀값은 .env에 복제할 필요가 없다.
5. 기관별 규격/이용 조건과 안전한 인증 입력 경로를 확인한 후 본인이 금융 인증을 수행한다. Android 연결 화면도 구현했으나 기관 코드·ID/PW 지원 확인과 이용 승인이 필요하다. 키만 넣는 것으로 실제 연결 검증이 완료되지 않는다.

`.local/`은 Git 제외 대상이다. 토큰은 메모리에만 유지한다. 요청/응답·토큰·기관 로그인·Connected ID를 로그/예외 메시지에 쓰지 않으며 객체 문자열도 가린다. 금융 자료는 AI에 전달하지 않는다. Connected ID는 사용자 소유권과 별도 암호화 키로 보관한다. 저장 키는 CODEF 공개키/클라이언트 비밀값과 별개다.

## 검사 실행
`./scripts/backend.ps1 test`로 실행한다. CODEF 계약 검사만 실행하려면 Maven에서 `-Dtest=CodefClientContractTest,FinanceStatusControllerTest test`를 사용한다. 검사 서버는127.0.0.1의 임시 포트만 열며 외부 CODEF 인증정보나 금융기관 호출은 없다. 인증정보/본문은 합성값이며 전역 네트워크를 성공한 것으로 표시하지 않는다.

연결 저장 V9를 추가 적용했으며 기존 거래/추천 데이터는 변경하지 않았다. 새 API를 로컬 서버에 반영하고 비활성 차단을 확인했다. Android→API→DB 금융 연결은 합성 공급자로 검증했다. 실금융은 아직 검증하지 않았다.


## 사용자별 연결 API (V9)
모든 경로는 앱 Bearer 인증이 필요하다. Connected ID/클라이언트 비밀값은 앱에서 받거나 응답하지 않는다.

| 메서드·경로 | 입력/동작 |
|---|---|
| POST /v1/finance/connections | organization, loginId, password, consent=true, consentVersion=FINANCE_LINK_V1 |
| GET /v1/finance/connections | 본인 연결 목록 |
| GET /v1/finance/connections/{id} | 본인 연결 상태/버전 |
| POST /v1/finance/connections/{id}/reauthorize | expectedVersion, loginId, password, consent=true, consentVersion |
| POST /v1/finance/connections/{id}/disconnect | expectedVersion |
| GET /v1/finance/operations/{id} | 본인 작업 결과 |

변경마다 UUID Idempotency-Key 헤더를 사용한다. 같은 요청의 네트워크 재전송은 같은 키/같은 본문으로 보낸다. 같은 키의 다른 본문은409이고, 결과 불명확 상태에서 새 키를 만들어 우회 재시도할 수 없다. 오류 본문에는 고정 코드만 있고 공급자 원문 메시지를 넣지 않는다.

응답은 connection/operation/replayed다. connection은 id/environment/organization/state/version/consent_version/consented_at/last_operation_id/last_error/created_at/updated_at/hasConnectedId를 포함한다. operation은 id/connection_id/kind/status/error_code/created_at/updated_at다. replay의 operation은 원래 작업 결과이고 connection은 현재 상태다.

200은 작업 처리 결과가 확정됐다는 뜻이며 operation.status=FAILED일 수도 있다. 금융 연결 성공은 SUCCEEDED와 connection.state=ACTIVE를 함께 확인한다. 진행/결과 불명확/추가 인증은202이며 각각 PENDING/UNKNOWN/AUTH_REQUIRED로 표시한다. 미인증401, 타 사용자404, 키/버전/상태 충돌409, 잘못된 입력422, 공급자 비활성 또는 저장 키 없음503이다. 해제는 SUCCEEDED/DISCONNECTED가 되어야 확정이다.

FINANCE_LINK_V1 동의 목적은 선택 기관의 카드 연결·조회와 사용자가 요청하는 갱신/해제다. 서버는 로그인 정보를 요청 처리 동안만 사용하고 Connected ID를 암호화 보관한다. 자동 조회·AI 전송 동의가 아니며 별도 포괄 동의 UI가 완성된 것도 아니다. 생성/재인증에서 명시적으로 확인한 버전을 기록한다.

## 저장 키와 로컬 상태
`./scripts/configure-finance-key.ps1`는 기존 파일을 덮어쓰지 않고 `.local/finance-storage.properties`에256비트 저장 키를 생성한다. Windows에서 현재 사용자와 SYSTEM만 접근하도록 파일 권한을 제한하며 키를 출력하지 않는다. CODEF_STORAGE_KEY_FILE에 이 파일의 절대 경로를 지정한다. 이번 로컬 환경에는 생성·경로 설정을 완료했고 CODEF 외부 연결은 꺼져 있다.

키 파일 형식은 activeKeyId와 keys.<키ID>다. 키 교체 시 새 키를 추가하고 activeKeyId를 변경하되 기존 암호문/요청 지문이 남아 있는 이전 키를 지우지 않는다. 키 유실 시 복호화할 수 없으므로 저장소·채팅 외의 안전한 경로로 보관해야 한다. 키를 찾지 못하거나 암호문이 변조되면 금융 요청을 전송하지 않는다.

한 연결은 개인카드1개 기관/ID-PW 인증 단위를 관리한다. Connected ID를 여러 연결에 공유하지 않는다. 기관별 인증서·OTP·간편인증 지원이 확인되기 전 해당 입력을 임의로 추가하지 않는다.

UNKNOWN은 성공으로 간주하지 않으며 자동 재실행도 하지 않는다. 원래 요청의 결과 대조/복구는 후속 작업이다. API의 연결 조회는 저장된 상태 조회이며 금융기관 계정 상태를 새로 확인한 결과가 아니다. 활성/확인 필요 연결이 남아 있으면 DELETE /me는409로 보호한다.

통합 검사: PostgreSQL 실행 후 `./scripts/backend.ps1 finance-test`. 실제 HTTP API와 PostgreSQL을 사용하고 CODEF 쪽은 합성 응답만 쓴다. [이번 검증 결과](../verification/2026-10-19-codef-connections.md).

## Android 연결 화면
로그인 → 설정 → 금융 연결 관리. 공급자/저장 키 준비 여부와 합성 응답 여부를 표시한다. syntheticProvider는 테스트 전용 loopback 어댑터에서만true이며 SANDBOX라는 환경명 자체를 합성 데이터의 증거로 쓰지 않는다. 정식 설정은 공식 HTTPS 호스트만 사용한다.

생성/재인증은 기관 코드와 ID/PW, FINANCE_LINK_V1 동의가 필요하다. 기관별 인증 방식이 미확인이라 KB/하나 코드를 자동 선택하지 않는다. 입력은 요청 직후/백그라운드/화면 종료 시 폐기하고 저장하지 않는다. 금융 화면은 캡처 보호를 적용한다. 금융기관 로그인 정보는 채팅이나 로컬 설정 파일에 쓰지 않는다.

수정 요청은 매 제출1개 UUID를 사용하고 자동 재전송하지 않는다. 통신 실패·응답 검증 실패·버전 충돌 뒤 전체 재조회 전 변경을 막는다. UNKNOWN은 재조회 뒤에도 재인증/해제를 금지한다. API의202/FAILED를 성공으로 표시하지 않는다. 최신 연결 조회는 저장된 상태 확인이며 UNKNOWN 공급자 대조/복구 기능이 아니다.

합성 시연: `./scripts/android-finance-test.ps1`. [Android 검증 결과](../verification/2026-10-19-android-finance.md). 이 검사는 연결 관리에 한정한다. 승인 원장 반영은 아래 명시적 동기화 API에서 수행하며 Android 동기화 화면은 합성 응답으로 검증했다.

## 승인 동기화 API (V10/V11)

명시적 기간 조회로 확인된 KB·하나 승인 거래를 원장에 반영한다. Android 동기화 버튼/보류 확인 화면을 연결하고 합성 응답으로 검증했다. 이전 연결 화면의 실기기 검증과 구분한다. API/필드/보류 처리/한계는 [승인 정규화 계약](CODEF-APPROVALS.md), 검사 근거는 [동기화 검증](../verification/2026-10-19-codef-sync.md)을 참조한다. `./scripts/backend.ps1 sync-test`는 인증정보 없이 합성 공급자와 실제 PostgreSQL로 검사한다.

Android 동기화 진입: 설정 → 금융 연결 관리 → 연결별 기간별 거래·보류 확인. 저장된 결과 조회는 외부 재호출하지 않는다. 최근 작업20개는 `GET /v1/finance/connections/{id}/sync-runs`로 조회한다. 상세 [Android 동기화 검증](../verification/2026-10-19-android-sync.md).
