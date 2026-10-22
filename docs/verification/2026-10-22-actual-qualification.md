# 실제 카드 현재 자격 — 2026-10-22

근거: [KB Easy all 티타늄 공식 안내](https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?cooperationcode=09256&mainCC=a)의 AUTO 전월 구간과 최초 사용 등록 유예, [하나 트래블GO 계좌형 공식 설명서](https://m.hanacard.co.kr/leaflet/15/15252_20250115.pdf)의 전월 기준과 신규 등록 유예, 기존 검증된 트래블로그 국내 적립 무실적 조건. 일정 추정이나 앱 등록일을 카드사 최초 등록일로 사용하지 않는다.

V29 `actual_qualification_facts`: 전월 마감 인정액·출처·확인일·최초 사용 등록일·근거 버전과 현재 월 자격 버전. `/v1/cards/{id}/qualification` GET/PUT. 새 입력으로 현재 자격과 혜택 구간은 계산하지만 다음 달용 미기록/거래 실적은 변경하지 않는다. 월 전체 혜택 포함 확인은 초기화한다. 확정된 자격 구간과 모순되는 혜택 구간 저장도 거절한다.

전월 미확인 + 신규 유예이면 기본 구간 자격은 충족해도 더 높은 구간 가능성은 미확인이다. 전월 미확인·유예 미확인이면 자격null. 직접 입력/추정값을 공식 자동 조회값으로 표시하지 않는다. UI는 월별 실적 설정·금융 거래 혜택 화면 양쪽에서 접근한다.

검증: 연말·다음 달 말 경계, 정확한 구간, 미확인/음수 실적, 유예 중 상위 구간의 단위4 검사. 합성 PostgreSQL 저장/재조회·다른 사용자·구버전·출처·미래일·마감 전 확인일·다음 달 실적 보존·구간 모순 검사. 첫 검사에서 nullable Boolean 자동 언박싱 오류를 발견해 명시적 Boolean 반환으로 수정, 실패 검사6 재통과. 최종 관련45 검사 통과 기록은 `.run/qualification-final-test.log`.

Android 빌드/JVM 통과. 에뮬레이터에서 최초 입력→유예1구간 저장→재시작 재조회→다른 요청으로2구간 저장→기존 화면 충돌/입력 보존을 HTTP와 DB로 확인. `.run/qualification-android.json`, `.run/qualification-emulator.log` 통과. 실제 금융 호출0, 휴대폰 조작0, 개인 DB 수정0.
