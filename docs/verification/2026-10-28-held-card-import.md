# 보유 카드 조회·내 카드 등록 — 2026-10-28

## 구현과 사용자 흐름
내 카드 관리 → 보유 카드 불러오기 → 연결 카드사 선택 → 명시 조회 → 카드 선택 → 본인/상품 확인 → 내 카드 등록 → 서버 저장 상태 재조회.

- 새 API: `GET /v1/finance/connections/{connection}/held-cards`(저장 목록만), `POST .../refresh`(명시 조회), `POST .../{id}/register`(확인 등록). 소유자·ACTIVE 연결·연결 버전·조회 버전·본인 확인을 검사한다. 조회만으로 내 카드/거래를 만들지 않는다.
- `V30__held_card_inventory.sql`에 사용자/연결별 마스킹된 조회 결과와 등록 관계를 보관한다. 카드번호 앞자리도 버리고 끝3/4자리만 유지하며 소유자명/유효기간/원응답/CID는 저장·반환하지 않는다. 명세는 OpenAPI0.19.0과 실제 경로/record body 필드 계약으로 검증했다.
- 공식 SDK의 card-list/inquiryType0 규격을 적용했다. [CODEF 공식 예제](https://github.com/codef-io/easycodef-java). 기관마다 다른 미검증 마스킹·추가 인증은 정상 카드로 추정하지 않고 실패/보류한다.
- 상품을 직접 선택·동의하면 기존 검토된 상품 프로필로 등록한다. 같은 상품은 기존 프로필을 재사용할 수 있다. 자동 상품명 매칭은 없다. 상품 미선택 카드는 `IMPORTED_UNVERIFIED`로 등록하고 rate/자격/실적/목표/혜택/한도는 null, 계산·추천·수동 구매/연결·월별 편집을 차단한다. 다른 카드의 규칙을 복사하거나 UNKNOWN을0으로 만들지 않는다.
- 동일 조회 카드 재시도는 기존 등록을 반환하며 중복 생성하지 않는다. 오래된 조회·연결 변경·카드사/상품 버전 불일치·동일 마스킹의 모호한 이름 변경을 거부한다. 누락된 등록 카드를 삭제하지 않는다. 200개를 넘는 응답/목록도 부분 적용하지 않는다.
- 실패한 조회는 이전 저장 목록을 보존한다. 저장 응답 유실 시 GET으로 확인한다. 이미 등록한 카드의 상품 변경은 이 등록 API가 자동 수행하지 않는다. 기관별 추가 인증 이어가기·실기관 성공/모든 상품 혜택 지원을 이번 구현 완료로 확대하지 않는다.

## 합성 검증 근거
- 새 일회용 PostgreSQL17.6 컨테이너의 v2 DB만 사용. Flyway30, 사용자/카드/조회카드/원장 검사 후0. 기존 개인DB/여기일지도/실제 서버는 접근·변경하지 않았다. CODEF/저장키/OpenAI/메일 설정 공백을 실행 환경에 강제하고, 테스트의 provider/cipher만 인프로세스 loopback 합성 구현으로 대체했다. 실금융/유료 호출0.
- 관련 정규화/추천/실제 규칙/HTTP·PG 51통과(실행 대상52 중 Android1은 별도 게이트로 보류). CODEF 계약51 통과. 추가 연결 경합/금융 비활성 및 미확인 편집 차단2 통과. 기존 서비스·API 계약5 통과(문서 경로 수정 후 해당1개만 재실행, 기존4개 재사용).
- 실제 에뮬레이터5564 → HTTP API → loopback CODEF → PostgreSQL 전체 흐름1 통과: 두 가상 카드 조회, 확인 전 저장 차단, 미지원1개 등록, 재조회 중복 방지, 두번째 카드의 상품 선택/확인 등록, 내 카드2개 재조회·표시, 다른 사용자 카드0. HTTP IO 재개와 onChanged 콜백이 Android MAIN임을 확인했다.
- Android debug/androidTest 빌드, JVM15, lint 오류0/기존 경고16. 앱 본체는 마지막 성공 빌드를 재사용했으며 최종 UI 재검증은 검사 APK만 수정했다. 개인 폰/원래 앱을 사용하지 않았고 독립 designreview 패키지만 초기화·설치했다.
- 첫 합성 DB의 계정 삭제 참조 오류는 card→inventory cascade로 수정하고 새 v2 DB에서 재검증했다. 확인창 고정 버튼의 scroll 검사 및 비동기 완료 대기를 검사 코드에서 수정해 최종 통과했다. 실패 원문/자격증명은 기록하지 않는다.
- 증거: `.run/held-backend-tests.log`, `held-final-checks.log`(CODEF51/추가2), `held-android-e2e.log`(최종 성공), `held-api-regression*.log`, `held-android-build.log`. 감사·push 근거는 기존 `.run/history-audit.json`/`push-receipt.json` 형식을 사용한다.
- APK `outputs/held-card-review/card-advisor-held-review.apk`, SHA256 `e9445ee8d0523aaac3840497256b90f9eef2718930ec56410ebb8fa94ffa7d4c`. 이 APK의 새 API는 아직 기존 사설 서버에 배포하지 않았다. 에뮬레이터 합성 서버 왕복과 운영/실기기 검증을 구분한다.

## 재현
개인 데이터가 없는 별도 PostgreSQL DB의 `DB_URL/DB_USER/DB_PASSWORD`를 지정하고 금융/메일/AI 설정 파일 경로를 공백으로 지정한다. `RUN_FINANCE_PG_TESTS=true` 후 backend에서 `mvn -Dtest=HeldCardNormalizerTest,HeldCardsPostgresTest,RecommendationEngineTest,ActualCardRulesTest,CodefClientContractTest test`. 기존 API 계약은 `RUN_SERVICE_PG_TESTS=true`와 `mvn -Dtest=ApiContractPostgresTest test`.

Android 검사는 독립 `.designreview` 앱과 test APK를 에뮬레이터5564에 설치하고 `RUN_HELD_CARD_ANDROID=true`로 `HeldCardsPostgresTest#androidLookupAndRegisterUsesRealHttpAndPostgres`를 실행한다. 이 메서드가 임시 reverse만 생성/제거하며 합성 토큰은 프로세스 메모리 안에서만 전달한다. 테스트는 실제 설정/실제 기관을 사용하지 않는다.

## 반영 경계
로컬 기능 커밋과 GitHub main push가 이번 요청 범위다. V30은 폐기 가능한 합성 DB에만 적용했다. 기존 사설 서버 이미지/DB/설정·실금융 비활성은 유지하며, 서버 반영/실기기/실기관 테스트는 별도다.
