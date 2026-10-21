# 2026-10-20 가계부 카드·가맹점·취소 표시

## 변경
- GET /v1/transactions?month=YYYY-MM에 merchant_name, display_card_name, card_last_digits, financial_status, financial_source 추가. ledger_entries와 승인 증거/사용자 확인 상품 관계를 동일 user_id로 LEFT JOIN. 금융 거래의 ledger_id는 사용자별 유일하므로 취소 전표로 행을 늘리지 않는다.
- display_card_name은 금융 거래 존재 시 연결 상품→공급자 카드명, 금융 거래가 없는 수동 기록에만 기존 계산 카드명을 사용한다. 수동-금융 대조 뒤 남은 가상 카드명을 실제 사용 카드로 표시하지 않는다. 표시 정보와 card_id/혜택·실적 연결은 별개다.
- card_last_digits는 검증된 마스킹 형식에서 끝3/4자리만 추출한다. 전체 카드번호·마스킹 번호·Connected ID·인증정보를 새 메타데이터로 반환하지 않는다.
- 기존 순지출/카드순승인액의 취소 합산 SQL은 보존. 저장/분류/예산/실적/혜택 도메인과 DB 스키마 변경 없음. 목록/단건조회에 동일 표시 정보 제공.
- LedgerRow: 가맹점·날짜·카테고리·카드/끝자리·지출·예산 포함을 표시. 정상/부분 취소/전체 취소를 구분하고, 취소가 있으면 원래 금액/취소 반영액/취소 후 지출 표시. 금융 원본 재확인 상태에서는 기존 가계부 금액임을 명시.
- 수동 가맹점 미입력/기관 미제공/카드 미확인 값은 임의로 채우지 않는다. 실제 가맹점이 없는 수동 기록은 수동 구매로 표시. 클릭·분류·예산포함·부분/전액취소·카드변경의 기존 동작 유지.

## 검증
- PostgreSQL/HTTP3개: 사용자격리·조회 revision/계산 불변·같은 증거 재수신 중복방지·상품명 표시/원장 card_id 미변경·금융 부분/전체취소·수동 구매→부분취소→전체취소의 예산/실적/현재자격/혜택사용/잔여한도 기대값·금융연결 수동기록의 가상카드 오표시 방지. `.run/ledger-display-pg.log`.
- Android 빌드 및 LedgerDesignTest2개: 정상/부분/전체 취소 상태, 카드/가맹점/끝자리, 미제공값, 실제 클릭 동작. `.run/ledger-display-build.log`, `.run/ledger-display-ui.log`.
- AndroidSyncPostgresTest 에뮬레이터→Spring API→loopback 합성 공급자→PostgreSQL 전체흐름 통과. `ledger_display` 단계에서 목록4건·고유ID4개, 가맹점/연결상품, 부분취소50,000→30,000·할부90,000→70,000 UI표시 확인, 표시 전후 실적 응답 동일·예산170,000 유지. 기존 중복/취소후보/상품연결충돌/보류/응답유실/재로그인도 통과. `.run/ledger-display-flow.log`, `ledger-display-flow.json`. 합성계정삭제, 실제 CODEF 요청0.
- 합성 가계부 PNG `.run/ledger-design-preview/ledger-cancellations.png` 직접 확인. 스크롤 위치의 화면이며 개인 거래/기기 화면을 캡처하지 않았다.
- 최종 backend JAR 갱신/로컬 API 재시작/health200, SM-F741N install -r/USB18080/새 프로세스 시작 Status ok 확인. 기존 개인 데이터·암호화 로그인 보존.

## 검증 경계
앱→API→DB는 에뮬레이터와 별도 합성계정의 실제 로컬 API/PG 근거다. 개인 금융 거래를 변경하거나 취소1건을 자동 연결하지 않았다. 휴대폰은 설치/새 프로세스 실행이며 이번 기능의 실기기 전체 거래 조작 및 CODEF 재조회는 별도다. 가맹점을 제공하지 않은 수동 기록의 가맹점 입력 기능은 이번 요청에 포함되지 않는다.

로컬 반영: PID기록29212 외에 동일 프로젝트 구Java48156이 JAR를 잠근 것을 확인, 전체명령 노출 없이 경로 검증 후 해당 프로세스만 종료. 최종 패키징 성공, API PID35732/health200. 휴대폰 설치 Success, MainActivity 시작 Status ok(LaunchState UNKNOWN), 앱 PID16656. 별도 APK/DB 데이터 삭제 없음.
