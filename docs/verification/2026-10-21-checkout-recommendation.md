# 주문서 확인에서 추천까지 연결 검증

## 구현
- 후보는 메모리에만 저장. 모든 관측 금액/버전 변화·만료·인증 화면·로그아웃 등은 재확인 대상이다.
- 결제 버튼 금액과 주문 합계를 별도로 표시한다. 미확인 주문합계/포인트/머니는 빈 값이고 0을 대신 넣지 않는다.
- 사용자가 금액·구매 조건·결제수단을 확인하면 비교 화면으로 이동하면서 checkout 생성→recommendation 조회를 한 번 실행한다. 이전 소스 세대의 응답은 표시하지 않는다.

## 현재 통과 근거
- CheckoutCandidateStoreTest6개, CheckoutTextParserTest5개, CheckoutHandoffPolicyTest4개 및 debug/androidTest 빌드.
- CheckoutRecommendationFlowTest/AndroidCheckoutRecommendationPostgresTest 전체1개: 합성 계정 생성·동의·실제 상품 규칙 등록→합성 주문서 관측→미확인 빈 입력/명시확인→추천→같은 최종금액에서 포인트 변경→이전 결과 제거→재확인/재추천→후보 폐기 시 결과 제거.
- DB 대조: 주문62000/머니10000/포인트2000→3000/카드승인50000→49000, 예상혜택150→147, 예산지출60000→59000. 실제 전송 결제수단 DIRECT_CARD/NAVER_PAY_CARD 각각 보존. 추천2/가계부0/검증계정삭제.
- ServiceCompletionPostgresTest HTTP/PG4개도 통과. `.run/checkout-recommendation-pg.log` 및 최종 재검증 `checkout-recommendation-pg-retry.log`, `checkout-recommendation-flow.json`.

## 검증 환경/한계
- 휴대폰 없음. 사용자 요청에 따라 emulator-5554에서 합성 주문서만 주입했다. 실제 네이버/무신사 최신 주문 경로·기기·쿠폰 조작/결제 실행 검증이 아니다.
- CODEF·OpenAI·SMTP 설정을 테스트 컨텍스트에서 공백으로 고정, 외부 공급자 호출 없음. 실제 금융 자격/한도를 임의 확정하지 않았다.
- Docker는 Inference 런타임 오류로 미복구. 런타임 run 폴더만 보존 격리하고 기존 DB 디스크/볼륨은 건드리지 않았다.
- PostgreSQL17.6 별도 Windows 합성 클러스터를 127.0.0.1:54330에 사용하고 Flyway22개 적용. 기존 Docker DB/개인 로그인/거래 검증으로 주장하지 않는다. 정상18080 API는 아직 미복구다.
- 바이너리는 [PostgreSQL Windows 안내](https://www.postgresql.org/download/windows/)에서 안내하는 [EDB 배포처](https://www.enterprisedb.com/download-postgresql-binaries)의 `postgresql-17.6-1-windows-x64-binaries.zip`. 로컬 다운로드 SHA256 `d378882abd001a186735acd6f6ba716bca6ccd192e800412d4fd15ed25376b3e`은 재현 기록이며 별도 공급자 서명 검증을 뜻하지 않는다. 바이너리/DB/비밀 설정/실행 로그는 Git에서 제외된다.

## 로컬 재실행
1. Android SDK/JDK21에서 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 실행 후 두 debug APK를 emulator-5554에 설치.
2. 로컬에만 있는 `.local/checkout-pg.properties`에서 DB_URL/DB_USER/DB_PASSWORD를 현재 프로세스 환경으로 읽는다. 값은 출력하거나 채팅에 붙이지 않는다. 격리 DB가 정지돼 있으면 같은 파일의 PG_BIN/PG_DATA를 사용해 pg_ctl start로 실행한다.
3. backend에서 RUN_CHECKOUT_ANDROID_PG_TEST=true, ANDROID_TEST_DEVICE=emulator-5554 후 Maven `-Dtest=AndroidCheckoutRecommendationPostgresTest test` 실행. 테스트가 USB reverse→별도 임시 API 포트 연결 및 종료 시 reverse 제거/합성계정 삭제를 담당한다.
4. 실제 기기에 이 테스트를 실행할 경우 현재 검증 호스트가 앱 프로세스를 종료할 수 있으므로 기존 개인 세션 보존 검증을 먼저 준비해야 한다. 현재 근거는 에뮬레이터만 해당한다.
