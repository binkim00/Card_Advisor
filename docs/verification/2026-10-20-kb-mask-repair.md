# KB 실제 카드 마스킹과 기존33행 복구

공식 근거: https://developer.codef.io/products/card/common/p/approval 의 resCardNo(String, 필수) 예시는 앞4/뒤4 마스킹이다. 해당 페이지는 KB가 항상 이 형식이라고 보장하지 않는다. 실제 DEMO 조회를 별도 명시 실행하여 카드 필드 원문이 아닌 형식별 건수만 확인: 앞13별표/뒤3숫자33건. 기존 앞4/뒤4 전용 파서가 모두 null로 바꾼 것이 MISSING_IDENTITY의 원인이다.

수정: KB0301에서 확인한 형식 추가, 다른 기관/미확인 형식은 기존 보류. V17 DB 제약·Android 파서 동시 수정. 사용자별 HMAC과 승인번호/일시/마스킹으로 동일성을 계산하고, 구버전 미반영 보류의 이전 지문을 정확히 대조해 UUID 그대로 갱신한다. 과거 후보가 모호하거나 원장이 이미 연결된 경우 실패 처리하며 신규 구매로 우회하지 않는다.

검증:
- 정규화8개 + 실제 HTTP/PostgreSQL16개 총24개 통과. 구버전 합성 레거시 행 복구/반복 조회/모호한 이전 식별 충돌 차단 포함.
- Android debug 빌드 및 SM-F741N 설치 완료. 에뮬레이터 KbMaskedCardParserTest2개 통과: 양 마스킹 허용, 전체번호 및 미지원 형식 차단.
- 명시적 실제 서비스 재조회2회(앱/API를 우회한 운영자 검증): 첫 요청32반영/1보류, 두 번째 동일. 원거래33개 UUID 집합 보존, 가계부/취소 수 증가 없음, MISSING_IDENTITY0.
- 실제 DB 집계: APPLIED32/HELD1(CANCELLATION_MATCH_REQUIRED), 금융33행/원장 연결32/중복 원장 연결0. 취소1건은 원거래를 사용자 확인해야 하므로 지출이나 환불을 임의 적용하지 않는다.
- 실제 사용자 앱에서 같은 기간2회 조회 검증은 요청 후 대기 중. 운영자 서비스 검증을 앱→API 증거로 대신하지 않는다.

민감정보: CID/키/카드번호 원문/승인번호/거래 원문을 출력·보관하지 않았다. 실제 응답은 메모리에서 구조만 집계. 합성 검사 공급자는 코드로 loopback 및 빈 설정파일에 고정했다.
근거: .run/codef-safe-shape.log, .run/kb-mask-tests.log, .run/kb-mask-live-repair.log, .run/kb-mask-live-state.json, .run/kb-mask-android.log. 실기기 수정 APK가 로그인 상태를 보존하지 않아 사용자 직접 로그인·조회가 필요하다.
- KB3자리 마스킹을 강제한 AndroidSyncPostgresTest: emulator5554→합성 API/loopback→실 PostgreSQL 전체 흐름1개 통과. 가계부4건/취소2건/금융5건, 반복 조회 중복 없음·응답 유실·사용자 중복 확인·명시적 취소 원거래 연결 포함. 정확한 합성 계정 삭제 및 에뮬레이터 reverse18080 복원. 근거 .run/kb-mask-android-flow.log 및 .run/android-sync-verification.json.
