# 요구사항 추적표

부분 구현은 전체 기능 완료가 아닙니다. 테스트 실행 결과는 STATE.md를 기준으로 합니다.

2026-10-16: scripts/api_smoke.py를 실제 PostgreSQL 17.6과 실행해 16개 검사를 통과했습니다. 인증·사용자 격리(FR-01/23), 가상 카드 설정(FR-03/04), 예산·버전 충돌(FR-05/16), 거래 중복·부분 취소(FR-08/09), 주문·추천 무효화(FR-13/14/15), 계정삭제·세션폐기(FR-21), 로컬 실행(FR-24)의 일부 경로를 검증했습니다. 각 FR의 남은 범위와 부분/미구현 상태는 유지합니다. 세부 근거: docs/verification/2026-10-16-local-api.md.

Android 16 에뮬레이터의 LocalFlowTest와 scripts/android-ui-smoke.ps1로 가입·가상 설정·추천·수동 구매·부분 취소·예산 재계산·재로그인 유지 및 실제 DB 대조를 통과했습니다. FR-01/03/04/08/09/13/14/15/16/24 관련 화면 경로의 근거이며 FR 전체 완료를 뜻하지 않습니다. 상세: docs/verification/2026-10-16-android-ui.md.

2026-10-16 카드 연결 단계: 계산25, 실제 API27, 최종 Android 화면1 시나리오 통과. 기초270,000→승인 후320,000→부분 환불 후300,000과 현재 자격 유지 확인. 상세 근거: docs/verification/2026-10-16-card-performance.md.

| FR | 상태 | 구현 위치 | 검증 대상 | 남은 범위 |
|---|---|---|---|---|
| FR-01 | 부분 | Passwords, AuthController/Filter, SessionService, RequestLimits | 인증·격리·세션 회전·속도제한 API/실기기 | 이메일 소유 확인·비밀번호 복구·운영 보호 |
| FR-02 | 부분 | CodefClient, FinancialConnectionService, ConnectedIdCipher, FinanceEditor | 실제 KB DEMO 연결·암호화 저장·실제 동기화, 합성 UNKNOWN/격리 | 추가 인증 이어하기·UNKNOWN 공급자 대조·하나 실제 연결 |
| FR-03 | 부분 | CardCatalogService/Controller, ActualCardRules, CardProductsEditor | 실제3상품 제한 규칙·수동 등록/추천 | 실거래 계산 검토 |
| FR-04 | 부분 | card_month_states V4, saveMonthState, MonthlyStateEditor | month_state_smoke.py 7·MonthlyStateTest·DB 대조 | 공식 조회값·실제 상품 기간 확장 |
| FR-05 | 부분 | AdvisorService.saveBudget/budgetSummary, BudgetEditor | budget_smoke.py·BudgetFlowTest·DB 대조 | 복수 분류 지출 배분·AI 예산 제안 연계 |
| FR-06 | 부분 | FixedCostCalculator, FixedCostService, FixedCostEditor, V8 | 고정비 단위11·fixed_smoke.py·FixedCostFlowTest·DB 대조 | 다른 월 납부/부분 금액 배분·자동 매칭 후보 |
| FR-07 | 부분 | CodefApprovalNormalizer, FinancialSyncService, FinanceSyncEditor | 실제 KB 월별 조회·기존행 보존·중복 방지 | 외화·다른 기관·추가 인증·실제 카드 실적 연결 |
| FR-08 | 부분 | classify/budgetSummary, ClassificationEditor, FinancialSyncService, FinanceSyncEditor | 분류 API/화면·금융 수동 중복 확인 앱→API→DB | 자동 분류 제안·분할 분류·실금융 |
| FR-09 | 부분 | cancel API/UI, FinancialSyncService, FinanceSyncEditor | 누적/별도 취소·중복/과다 환불·할부 총액 PostgreSQL 및 Android | 실제 상품 취소정책·청구월 배분·실기관 누락/미매칭 |
| FR-10 | 부분 | PerformanceCalculator, FinancialPerformanceService/Controller, V20, FinancialPerformanceEditor, CardCatalogService | PG HTTP28·계산11·SM-F741N→API→DB 인정/제외/미확인·접수월/부분전체취소/충돌 | 공식 실적 대조·다른상품 취소정책·공식 전월자격 |
| FR-11 | 부분 | CheckoutAccessibilityService, CheckoutDetection, DetectionEditor | 합성 관측값 상태전이·빌드 | 네이버플러스 스토어 실제 주문 경로·버전별 감지 |
| FR-12 | 부분 | CheckoutAccessibilityService, CheckoutDetection, DetectionEditor | 합성 관측값 상태전이·빌드 | 무신사 실제 주문 경로·버전별 감지 |
| FR-13 | 부분 | Checkout record, Android 비교 화면 | 혼합수단·합계불일치 | 다양한 결제수단·분할·할부 UI |
| FR-14 | 부분 | checkout version/user revision/expiry/calculation_month | API snapshot·월경계 invalidation | 화면이탈·복귀·다른기기변경 처리 |
| FR-15 | 부분 | RecommendationEngine, /recommendations | 가상 규칙 16 tests | 실제 상품·조건 복합 엔진 |
| FR-16 | 부분 | Budget/Result, Android 비교 | 전체/카테고리/고정비 검산 | 복수 카테고리 배분 |
| FR-17 | 부분 | AiAssistanceService, OpenAiGateway, ClassificationEditor | 사용자 정정 우선·제한 분류 제안·실패 대체 | 실제 가맹점 분류 연결·다양한 분류 평가·실 AI 호출 |
| FR-18 | 부분 | AiAssistanceService, OpenAiGateway, InsightsEditor | 계산 fact 기반 설명·분석·예산안 명시 적용 | 실제 AI 호출·기간 비교·추천 품질 평가 |
| FR-19 | 부분 | UserSettingsService, AccountSettingsEditor, 접근성 고지 | 목적별 동의·철회·버전 검사 | 실제 접근성 권한 흐름·공개 운영 동의 검토 |
| FR-20 | 부분 | ActualCardRules, CardCatalogService, V13/V14 | 실제3상품 제한된 국내 쇼핑 규칙 | 기관 거래 연결·다른 혜택 영역·규칙 갱신 운영 |
| FR-21 | 부분 | DELETE /me (로컬만) | 계정삭제·세션폐기 | 설정UI·외부해제·백업삭제 |
| FR-22 | 부분 | Android 오류 처리, FixedCostEditor 진단 지점 | DebugDiagnosticsTest 조회 실패·복구·종료 | 오프라인조회·재시도·quota |
| FR-23 | 부분 | 토큰hash·PBKDF2·user_id 필터, debug Diagnostics | 교차사용자API·진단 비밀문자열 누출 검사 | 전범위 보안·동의·로그 누출 검증 |
| FR-24 | 부분 | Diagnostics, export_debug_diagnostics.py, scripts, docs | DebugDiagnosticsTest·release 제외·읽기 전용 추출 | CI·복구·스토어·다른 기종/OS |





2026-10-19: 월별 미기록 실적·현재 자격·출처·확인일 편집, 저장·재조회·버전 충돌, 미확인·월 격리·재로그인 유지 완료. FR-04/10/14/23/24 일부 경로의 추가 근거: docs/verification/2026-10-19-month-state.md. 전체 FR 상태는 부분 구현을 유지한다.

2026-10-19 혜택 한도 연결: BenefitCalculator, V5/V6, AdvisorService.performance/cards, Android PerformanceSummary·MonthlyStateEditor. FR-04/09/10/14/15/23/24 관련 부분 구현. 신규 혜택 계산16개 + 기존25개, 혜택API12개 + 실적10개 + 월별7개, Android 구매/취소/월별 입력 경로를 검증한다. 실행 근거는 docs/verification/2026-10-19-benefit-usage.md. 실제 카드사 규칙·전체 FR 완료를 뜻하지 않는다.

2026-10-19 분류 수정 단계: FR-05/08/14/15/23/24 관련 카테고리·예산 포함 수정, 버전 충돌, 순지출 예산 갱신, 카드 실적·혜택 불변, 재로그인 유지 검증. 실행 근거: docs/verification/2026-10-19-ledger-classification.md. 전체 FR 완료는 아니다.

2026-10-19 월별 예산 편집: FR-05/14/16/22/23/24의 저장값 조회·한도 추가/수정·동시 저장 충돌·월별/사용자 격리·추천 갱신을 검증한다. 명시적0과 미설정 구분, 순지출 반영, 카드 실적/혜택 불변. 상세 근거: docs/verification/2026-10-19-monthly-budget.md. 전체 프로젝트 완료는 아니다.

2026-10-19 반복 고정비: FR-06/08/09/14/16/23/24의 월별 일정·수동 거래 연결/해제·부분 취소 후 잔액 복원·모드 전환·월 격리·충돌을 검증한다. 상세: docs/verification/2026-10-19-recurring-fixed-costs.md. 전체 프로젝트 완료는 아니다.


2026-10-19 종료 안정성: FR-06/22/24의 FixedCostEditor 취소 후 상태 갱신 결함 재현·수정, FixedCostLifecycleTest 2개(지연 취소·생명주기12회), 실제 FixedCostFlowTest 및 BudgetFlowTest API/DB 대조 통과. 근거 docs/verification/2026-10-19-fixed-cost-stability.md. 원래 SlotTable 예외의 정확한 원인은 미확정이며 FR 상태는 부분 구현을 유지한다.


2026-10-19 새 프로세스 안정성: FR-06/14/22/24 관련 FixedCostResponse·FixedCostEditor의 응답 검증/안전한 오류·재조회, FixedCostColdStartTest와 로컬 장애 프록시7경로 및 기존 FixedCostFlowTest/BudgetFlowTest 앱→API→DB 통과. 상세 docs/verification/2026-10-19-fixed-cost-cold-start.md. 최초 SlotTable 원인 및 검사 대기 초과1회는 미확정이며 전체 FR 완료는 아니다.

2026-10-19 debug 진단: FR-06/22/23/24 관련 최초 관측 예외·종료 예외 분리, 고정비/Activity 수명, 메시지/본문/화면/인증정보 제외, 제한된 보존·원자적 기록·읽기 전용 추출을 검증했다. 구현: android/app/src/debug, main/DiagnosticEvent.kt·FixedCostEditor.kt·ApiClient.kt, release/Diagnostics.kt. 테스트: DebugDiagnosticsTest와 scripts/android-diagnostics-smoke.py. 상세 docs/verification/2026-10-19-debug-diagnostics.md. 원래 SlotTable 원인·전체 FR 완료와 구분한다.

2026-10-19 CODEF: FR-02/07/22/23/24의 통신 기반 구현. 조회 wire 계약만 있으며 거래 정규화·저장·동기화는 아직 없어 FR-07은 미구현을 유지한다. 공식 SDK/샘플 근거·제한: docs/integrations/CODEF.md. 계약31+상태API1, 기존 계산52 통과: docs/verification/2026-10-19-codef-contract.md.

2026-10-19 사용자별 CODEF 연결: FR-02/21/22/23/24 관련 소유권·암호화·중복 요청·버전·불명확 처리·계정 삭제 보호 구현. 실제 HTTP/PostgreSQL16개 및 기존 수동 API16개 통과. 근거 docs/verification/2026-10-19-codef-connections.md. FR-07 거래 동기화는 계속 미구현이며 금융 연결 전체 완료로 표시하지 않는다.

2026-10-19 Android 금융 관리: FR-02/21/22/23/24 관련 앱 화면·동의·중복/보류·세션 만료 처리를 API/DB와 검증. docs/verification/2026-10-19-android-finance.md. 거래 동기화(FR-07)는 계속 미구현.

2026-10-19 실기기: FR-01/02/04/08/09/10/13/14/15/16/21/22/23/24의 회원가입·추천·수동 원장·합성 금융 관리 관련 가상 데이터 흐름을 SM-F741N Android17 실기기에서 API/DB와 대조했다. LocalFlowTest 및 FinanceFlowTest → 실제 Spring API → PostgreSQL 통과. FR 전체 완료나 실금융 연결 완료로 승격하지 않는다. 근거 docs/verification/2026-10-19-physical-device.md.


## 2026-10-19 UI 공통 적용
기존 FR 상태를 승격하지 않는다. AdvisorDesign/MainActivity에 파란색 테마와 홈·하단 탐색·카드 이미지를 적용했다. 기존 기능/API 연결을 보존했고 별도 패키지 DesignUiTest 3개를 통과했다. 이번 APK의 실제 API/DB·실기기 회귀는 남아 있다. 상세: verification/2026-10-19-blue-ui.md.


## 2026-10-19 새 UI 실기기 회귀
가입·추천·수동 구매·부분 취소·월별 저장·재로그인 흐름의 새 디자인 APK 실기기→API→DB 확인 완료. 탐색 disabled 잔류 수정. 실제 금융 검증으로 FR 상태를 승격하지 않는다. 근거 verification/2026-10-19-blue-device.md.

## 2026-10-19 CODEF 거래 동기화

이전 단계의 FR-07 미구현 기록을 갱신해 현재 표는 부분 구현으로 변경했다. FR-02/05/07/08/09/14/15/16/21/22/23/24 관련 KB·하나 정규화·멱등 동기화·수동 중복/취소 연결·추천 보류/무효화·사용자 격리·계정 삭제를 검사했다. 실제 카드 실적/혜택 연결(FR-10)은 미완료이며 가상 규칙으로 대체하지 않는다.

정규화7+동기화 PostgreSQL14+기존 통신31+연결16=68개 및 기존 수동 API16개 통과. 신규 Android 동기화/실기기/실금융은 미검증. 구현 위치와 상세 검증: docs/verification/2026-10-19-codef-sync.md, docs/integrations/CODEF-APPROVALS.md.


### 2026-10-19 화면 구조 재적용
홈/추천/가계부 요약 우선·상세 펼침으로 변경. 기존 API 요청14개와 금융 갱신 보존. 에뮬레이터→API→DB 및 UI3개 근거: [검증 기록](verification/2026-10-19-blue-ui-v2.md). 새 APK 실기기 확인은 남음.

## 2026-10-19 Android 금융 동기화

FR-02/05/07/08/09/14/15/16/22/23/24 관련 기간 입력·저장 결과 복구·수동 중복 확인·취소 원거래 선택·오류/보류 및 예산/추천 갱신을 앱→API→PostgreSQL로 검증했다. 신규 실기기/실금융은 미검증이며 각 FR의 전체 완료를 뜻하지 않는다. 근거 docs/verification/2026-10-19-android-sync.md.


## 2026-10-20 서비스 확장 최신 보완
위의 이전 단계별 미구현 표는 다음 범위에서 갱신된다. 전체 FR 완료로 승격하지 않는다.
- FR-01/19/21/23: UserSettingsService/Controller·SessionService·RequestLimits·AccountSettingsEditor·V12, PostgreSQL4개 통합 경로에 동의 충돌/격리/철회·세션 회전 재사용·삭제 검사 포함. 신규 Android 통합 검증은 남아 있다.
- FR-03/09/10/15/20: ActualCardRules·CardCatalogService/Controller·V13/V14·CardProductsEditor. 실제3상품의 국내 일반 상품/네이버·무신사 단일 일시불만 검수. ActualCardRulesTest와 ServiceCompletionPostgresTest의 추천/접수월 취소 경로 통과. 기타 영역·공식 자격/실기관 매핑은 남음.
- FR-11/12/13/14/19: CheckoutDetection·CheckoutAccessibilityService·DetectionEditor 구현/빌드. 실제 주문 경로 및 신규 합성 Android 상태전이 검사는 미실행. 실험 상태로 부분 구현이다.
- FR-05/08/17/18: OpenAiGateway·AiAssistanceService·InsightsEditor·ClassificationEditor. 최소 fact 선택형 설명/분석·명시적 예산 배분 적용·사용자 정정 우선의 제한적 분류 제안. 합성 wire/변조 결과와 PostgreSQL 버전 충돌 검사 통과. 실제 OpenAI 호출·광범위 분류/기간비교 평가 미완료.
- FR-22/24: 운영/복구/CI는 작업 공간 초안만 있으며 적용/검증 미완료. 상세 검증 범위와 다음 행동: verification/2026-10-20-service-checkpoint.md.


## 2026-10-20 신규 실기기 증거
FR-01/03/05/09/10/13/14/15/17/18/19/21/23/24의 제한된 실제 상품 규칙·목적별 동의·수동 구매/취소·AI 기본 설명·계정 관리 화면을 실기기→API→PostgreSQL로 검증했다. FR-02/07/08/09/22의 기존 합성 금융 동기화도 같은 APK 실기기 회귀 통과. FR-11/12는 합성 관측값 상태전이 검사 통과에 한하며 실제 주문 경로 성공으로 승격하지 않는다. 전체 FR 완료/실금융/유료 AI/배포 승격 없음. 근거: verification/2026-10-20-service-device.md.


2026-10-20 FR-02/21/22/23/24 보완: 확정 실패 최초 연결의 명시적 재시도/버전·중복 방지/작업 이력 보존 soft archive/UNKNOWN 보호 및 OAuth 인증 단계 분리. PostgreSQL·계약57개와 실패 연결 수정·삭제 실기기→합성 공급자 API→DB 통과. 실제 금융은 OAuth401로 미완료. 근거 verification/2026-10-20-failed-financial-connection.md.

- 금융 연결 오류 진단: provider_code 제한 저장/API 조회/Android 표시 및 사용자 격리 합성 PG 검사63개 통과. 실 CODEF 로그 CF-12803 확인, 사용자 KB 로그인 교정 대기. 근거 verification/2026-10-20-codef-rejection-diagnostics.md. 전체 FR 완료 상태는 유지.

- 실제 DEMO KB0301 연결/암호화 CID/거래 조회 성공: 공급자33건 수신, 전건 MISSING_IDENTITY 보류로 가계부 반영0. 근거 verification/2026-10-20-live-codef-first-sync.md. 전체 동기화 완료로 간주하지 않음.

- 실제 KB 마스킹 차이 복구:33 UUID보존/원장32/취소확인1/중복0, 합성 Android API PG 회귀 통과. 실기기 재조회 대조는 대기. 근거 verification/2026-10-20-kb-mask-repair.md.

- 금융 내역 가독성: 제한된 공식 카드명/가맹점명 표시33건 보완, 전체내역기본/쉬운안내. 26개 backend검사 및 에뮬레이터 API PG1흐름 통과, 실기기APK설치. 근거 verification/2026-10-20-finance-readable.md.

- 취소 비교·명시적 연결: 카드/가맹점/날짜/금액 비교, 미선택 저장 차단, 미래·초과환불 API검사 및 합성 에뮬레이터→API→PG검증 통과. 실제 취소1건은 사용자 원결제 확인 대기. 근거 verification/2026-10-20-cancellation-comparison.md.

- 이전달조회/취소후보확장: 실제9월65건/63반영/외화2보류, 기존33UUID보존/반복중복0. 후보35개·구취소미연결. 월선택/비교유지 Android 회귀통과, 실기기설치. 근거 verification/2026-10-20-previous-month.md.

-8월 실제조회44건/44기록, 기존98UUID보존/동일기간재조회중복0. 전체후보52/자동연결없음/원결제미확정. 근거 verification/2026-10-20-august-search.md.

-7월조회48건/48기록 및 구버전복구 검사 범위결함수정, 실제동일기간재조회중복0/기존142UUID보존. 원취소미연결/후보71. 근거 verification/2026-10-20-july-search.md. 다음 기능 단위는 NEXT_FEATURES.md의 실제 금융카드→상품 확인 매핑.

- FR03/04/10/14/15/23: 금융카드↔실상품 사용자확인 연결API/Android 구현, 계산미확인·추천보류 보호. HTTP/PG22+에뮬레이터전체흐름1 통과/실기기설치, 새실기기조작은잠금해제대기. 실제거래룰적용은후속. 근거 verification/2026-10-20-financial-card-mapping.md.

- FR03/04/10/14/15/23/24: 사용자확인 금융상품 매핑의 SM-F741N→API→합성공급자→PostgreSQL 흐름통과(등록·선택·저장·재조회·충돌·해제·재연결). 실제데이터보존/합성계정삭제. 후속 실제거래 조건별 계산은 미완료. 근거 verification/2026-10-20-financial-card-mapping.md.

- FR01/21/22/23: Keystore 암호화 토큰 유지·프로세스/Activity복원·단일refresh·오프라인보존·로그아웃/계정삭제/서버폐기 시 제거. 에뮬레이터5단계 및 실기기 격리슬롯4단계 통과. 일반가입 이메일확인/복구는후속. 근거 verification/2026-10-20-session-persistence.md.

2026-10-20 주요화면 디자인 개편: FR-01/03/08/09/13/14/16/24의 기존 동작을 보존하고 합성 Compose3개 및 에뮬레이터 API/PG 전체흐름으로 회귀 확인. 관련 구현 AdvisorDesign/AdvisorScreens/MainActivity/CardProductsEditor, 테스트 DesignUiTest/FinanceSyncFlowTest. 요구사항 전체 상태를 승격하지 않음. 근거 docs/verification/2026-10-20-service-design.md.

2026-10-20 금융 연결/이용내역 디자인: FinancePresentation/FinanceEditor/FinanceSyncEditor, FinancialDesignTest2개/FinanceSyncFlowTest API·PG 회귀. FR-02/03/08/09/24의 기존 동작 유지, 범위 전체 완료 상태 승격 없음. docs/verification/2026-10-20-finance-design.md.

2026-10-20 가계부 표시 연결: FR-03/04/08/09/16/24, AdvisorService.entries/AdvisorScreens.LedgerRow/MainActivity; FinancialSyncPostgresTest3개/LedgerDesignTest2개/FinanceSyncFlowTest ledger_display API·PG 검증. 계산·전체FR 상태 승격 없음. docs/verification/2026-10-20-ledger-display.md.

2026-10-20 실기기 가계부: LedgerDeviceFlowTest/AndroidLedgerDevicePostgresTest로 FR-01/03/04/08/09/16/24 관련 카드·가맹점표시/수동·합성금융 부분·전체취소/예산·실적·혜택/개인세션·DB보존 검증. SM-F741N→실API→PG, 외부CODEF0. docs/verification/2026-10-20-ledger-physical.md. 전체FR상태 승격없음.

2026-10-20 실제 계정 비공개 가계부 조회: PrivateLedgerAuditTest로 본인API187건/현재월실기기32행 카드·가맹점·순지출·취소상태 표시대조, DB10표불변/로그인유지/원문비출력. FR-03/08/09/24 표시검증 근거이며 실제상품매핑/계산전체완료 아님. docs/verification/2026-10-20-real-ledger-private.md.

2026-10-20 상품 연결 UX: 등록/연결 상태 분리·직접 진입·선택창 유지/고정저장/PUT후GET대조/충돌GET복구. CardProductsEditor/FinancialCardMappingEditor/FinanceEditor, FinanceSyncFlowTest/실기기LedgerDeviceFlowTest에 등록→선택→저장→재시작검증. 실제선택 대기와 실제금융 실적/혜택 미완료는 SERVICE_GAPS.md에 명시.

2026-10-20 사용자 실제상품연결1건 저장 확인: 본인실기기→정상API GET/등록화면 연결저장됨/매핑새로고침·Activity재시작 유지, DB불변·로그인유지. 원장187불변, 실금융 실적·혜택 자동계산은 미완료. .run/mapping-real-saved-audit.json.

2026-10-20 금융 실적: FR-03/04/07/09/10/14 관련 V20 검토 저장·기존 합산 연결·실기기 검증 완료. `docs/verification/2026-10-20-financial-performance.md`의 검증 경계와 남은 공식명세/혜택 검증을 따른다.

2026-10-20 전체 미완료 구현 진행: FR-03/04/09/10/15/20 V21 금융 혜택·월한도·취소 예상 복원·추천 연결/합성 실기기 통과. FR-01/21/23 V22 이메일확인·재설정·세션폐기 PG4개 통과, 실제 메일 미정. FR-17/18 가맹점 비식별 분류단서·기간비교 추가. FR-11/12/19 감지설정 재시작 보존/비밀번호화면 차단·비식별 진단 추가, 실제 주문서 검증 진행 중. FR-24 암호화 로컬백업·별도DB복원 통과. 전체 FR/서비스 완료는 아니며 STATE.md의 남은 범위를 유지한다.

2026-10-20 FR 쇼핑감지 추가근거: 네이버2.8.10 실제 주문서에서 최종금액 후보1/사용자확인대기 관측. 결제버튼 후보 파서5개·합성감지2개 통과. 실제 사용자확인/API추천·금액변경·무신사 실경로 미검증. STATE 최신 경계 참조.

2026-10-21 FR-11/12/13/14/19 관련: CheckoutCandidateStore·DetectionEditor·AdvisorApp에서 감지 세대/전체 관측 변경·만료 무효화, 미확인 입력 보존, 사용자 선택 결제수단, 확인 버튼→추천API 자동 1회 연결. JVM15개와 CheckoutRecommendationFlowTest/AndroidCheckoutRecommendationPostgresTest 에뮬레이터→실HTTP→격리PG 통과. 실제 네이버/무신사 기기 검증은 기존 범위 유지, docs/verification/2026-10-21-checkout-recommendation.md 참조.

2026-10-21 최종 FR-08/11/12/13/14/19 회귀: 늦은 추천응답 폐기·네이버/무신사 문맥·명시적 별도 수동구매 확인 검증, 기존 수동/금융 취소·실적·혜택·예산 에뮬레이터→API→격리PG 회귀 통과. 휴대폰/실쇼핑·정상Docker API 재가동 경계는 STATE 최신 기록 참조.
