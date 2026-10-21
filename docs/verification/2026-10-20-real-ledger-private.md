# 2026-10-20 실제 계정 가계부 비공개 조회 검증

## 대상과 결과
- 사용자 요청에 따라 휴대폰에 저장된 본인 로그인으로 정상 로컬 API를 조회. 실기기 SM-F741N, 기존 API18080/PostgreSQL 사용. 합성계정/공급자로 바꾸지 않았다.
- 저장된 승인 날짜의 월 범위를 기기 메모리 안에서 파악해 본인 월별 가계부 GET 조회. 저장187건의 카드명·가맹점·금액이 모두 제공되고 중복0, 누락0/부적합금액0.
- 현재 달32건은 같은 AdvisorApp을 실행해 가계부로 이동, 각행의 가맹점/카드명·끝자리/순지출/취소상태를 기기 내부의 실제 API값과 대조. 표시됨 assertion과 문자열 일치 검사 모두 통과. 과거월187건 전부를 휴대폰 화면에서 확인한 것으로 확대 주장하지 않는다.
- 가계부 전체행187/고유행187, 현재월32/실금융증거187/가맹점·표시카드·공급자카드명·금액누락0/유효마스킹187을 DB 집계로 별도 확인.
- 직접 확인해 저장한 금융카드↔상품 관계는0건. 현재 표시명은 카드사 제공 이름이다. 원천 제공 값은 정상이며 미연결 상품을 추정/자동 연결하지 않았다. 남은 미연결 취소1건 유지.

## 보존·비공개 경계
- PrivateLedgerAuditActivity는 debug 전용/비공개 Activity이며 FLAG_SECURE 적용. 정상 AdvisorApp/개인 persistent ApiClient 사용. 비밀번호 입력/로그아웃/세션슬롯 교체/앱데이터 삭제 없음.
- 토큰은 Android 암호화 저장소에서 기존 경로로 복원, 기기·서버 인증 흐름 안에서만 사용. 정상 토큰갱신 외 GET만 허용하는 검사 코드이며 금융 동기화/외부CODEF 요청/구매/취소/분류변경/상품매핑 저장은 수행하지 않았다.
- 실제 카드명/가맹점/날짜/금액/ID/토큰/전체 카드번호를 도구출력·문서·스크린샷·파일로 내보내지 않음. 비교값과 semantics는 기기내 메모리에서만 사용. 원시 instrumentation 출력은 호스트프로세스 메모리에서 일시 수신해 정해진 단계/건수/코드위치만 추출하고 저장·출력하지 않았다.
- 검사 전후 app_users/cards/card_month_states/budgets/ledger_entries/cancellations/financial_transactions/financial_card_mappings/financial_connections/financial_sync_runs 전체 행 집계해시 동일. 해시 자체는 출력/저장하지 않고 기존DB불변 boolean만 기록. 정상 인증 토큰 회전은 금지하지 않으며 검사후 저장세션/인증상태 유지 확인.
- 종료 후 일반 MainActivity/USB18080으로 복귀. 기존 로그인과 거래 보존. 조회에서 제품 표시·정보 누락 결함이 재현되지 않아 상품코드를 임의 수정하거나 데이터를 보충하지 않음. 추가된 것은 비공개 debug검사와 재현 가능한 감사 도구다.

## 근거
- `.run/ledger-real-metadata.json`: 민감값 없는 DB누락 집계.
- `.run/ledger-real-private-audit.json`: actualAccount=true/physicalDevice=true/passed=true, stored_rows187/visible_rows32/누락·중복0/existingDataUnchanged=true/login_retained.
- `.run/ledger-real-audit-build.log`: debug/검사APK 빌드 및 실기기 설치.
- `PrivateLedgerAuditTest.kt`, `scripts/verify-real-ledger-private.ps1`, `scripts/audit-real-ledger-metadata.ps1`.
- 재현은 사용자 동의하에 휴대폰을 연결/잠금해제하고 실제 API18080을 유지한 뒤 debug/검사APK가 설치된 상태에서 위 비공개 감사 스크립트를 실행. 원문로그/스크린샷은 추가하지 않는다.
