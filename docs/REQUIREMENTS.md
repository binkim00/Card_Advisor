# FR-01~FR-24 현재 추적표 — 2026-10-22

지원 범위 구현은 모든 기관·결제 조건을 지원한다는 뜻이 아니다. 실제 금융/휴대폰의 이전 증거와 이번 합성/에뮬레이터 증거를 구분한다. 최신 결과: [STATE.md](STATE.md).

| FR | 상태 | 구현 위치 | 관련 검사 | 남은 경계 |
|---|---|---|---|---|
| FR-01 | 구현·로컬 검증 | AuthController, SessionService, AccountRecoveryService, SecureSessionStore | ServiceCompletionPostgresTest, AccountRecoveryPostgresTest, SmtpTlsContractTest | 실제 메일·운영 보호 확인 |
| FR-02 | 지원 범위 구현 | CodefClient, FinancialConnectionService/ReconciliationService, FinanceEditor | FinancialConnectionsPostgresTest, FinancialReconciliationPostgresTest, ReconciliationFlowTest | 기관별 추가인증 지원 확인·실대조·하나 실연결 |
| FR-03 | 구현·로컬 검증 | CardCatalogService, FinancialCardMappingService, CardProductsEditor | FinancialSyncPostgresTest, LedgerDeviceFlowTest | 상품 정책 개정·실명세 대조 |
| FR-04 | 구현·로컬 검증 | card_month_states, AdvisorService, MonthlyStateEditor | ServiceCompletionPostgresTest, MonthlyStateTest | 공식 월자격 수치 대조 |
| FR-05 | 구현·로컬 검증 | BudgetAllocation, AdvisorService, BudgetEditor | BudgetAllocationPostgresTest, DeviceFreeBudgetFlowTest | 실사용 관찰 |
| FR-06 | 구현·로컬 검증 | FixedCostService/Calculator, FixedCostEditor, V24 | FixedAllocationPostgresTest, DeviceFreeBudgetFlowTest | 자동 연결은 하지 않으며 후보를 사용자가 선택 |
| FR-07 | 지원 범위 구현 | CodefApprovalNormalizer, FinancialSyncService, FinanceSyncEditor | CodefClientContractTest, FinancialSyncPostgresTest | 하나 실기관·추가인증·외화 조건 |
| FR-08 | 구현·로컬 검증 | AdvisorService, ClassificationEditor, MerchantRuleService, V23/V27 | BudgetAllocationPostgresTest, MerchantMemoryPostgresTest, MerchantMemoryFlowTest | 실제 데이터 분류 품질 |
| FR-09 | 지원 범위 구현 | cancel, FinancialSyncService, PerformanceCalculator | FinancialSyncPostgresTest, LedgerDeviceFlowTest | 실제 상품 취소 정산·미매칭 1건 보류 |
| FR-10 | 지원 범위 구현 | FinancialPerformanceService, FinancialPerformanceEditor, V20 | FinancialSyncPostgresTest, PerformanceCalculatorTest | 공식 실적·다른 상품 취소 귀속 |
| FR-11 | 구현·합성 검증 | CheckoutAccessibilityService, CheckoutDetection | CheckoutRecommendationFlowTest, CheckoutTextParserTest / CheckoutCandidateStoreTest | 네이버 실제 주문 경로 |
| FR-12 | 구현·합성 검증 | 쇼핑몰별 감지 어댑터, DetectionEditor | CheckoutRecommendationFlowTest, CheckoutTextParserTest / CheckoutCandidateStoreTest | 무신사 실제 주문 경로 |
| FR-13 | 구현·로컬 검증 | Checkout, CheckoutConditions, MainActivity | CheckoutAllocationPostgresTest, DeviceFreeCheckoutInputTest | 미확인 결제 조건은 보류 |
| FR-14 | 구현·로컬 검증 | CheckoutCandidateStore, AdvisorService, ApiClient | CheckoutRecommendationFlowTest, RecommendationEngineTest | 실제 쇼핑몰 이벤트 대조 |
| FR-15 | 지원 범위 구현 | RecommendationEngine, ActualCardRules, ActualBenefitCalculator | RecommendationEngineTest, HanaGoBenefitsTest, LedgerDeviceFlowTest | 공식 근거 없는 정산/한도/적용순서 보류 |
| FR-16 | 구현·로컬 검증 | Budget/Result, categoryAfterByCode, 고정비/예산 요약 | CheckoutAllocationPostgresTest, DeviceFreeBudgetFlowTest | 실사용 관찰 |
| FR-17 | 구현·합성 평가 | MerchantCategoryClassifier, MerchantRuleService, OpenAiGateway | CategoryEvaluationTest, OpenAiGatewayContractTest, MerchantMemoryFlowTest | 실제 AI 응답 품질·동의 후 호출 |
| FR-18 | 구현·합성 검증 | AiAssistanceService, InsightsEditor | ServiceCompletionPostgresTest, OpenAiGatewayContractTest | 실제 AI 호출 품질 |
| FR-19 | 구현·로컬 검증 | UserSettingsService, AccountSettingsEditor, DetectionEditor | ServiceCompletionPostgresTest, 감지 상태 단위검사 | 실제 권한 흐름·운영 고지 |
| FR-20 | 지원 범위 구현 | ActualCardRules, CardCatalogService, V13/V14/V21 | ActualCardRulesTest, ActualBenefitCalculatorTest, HanaGoBenefitsTest | 정책 개정 운영·공식 이미지 배포 권한 |
| FR-21 | 구현·로컬 검증 | UserSettingsService, 삭제 등록부 V26, backup-local.ps1 | ServiceCompletionPostgresTest, test-backup-restore.ps1 | 백업 보존 기간·재해시 삭제 등록부 복구 |
| FR-22 | 구현·로컬 검증 | SecureDashboardStore, ApiClient, RequestGuardFilter | OfflineDashboardTest, RequestGuardFilterTest, debug 진단 검사 | 실기기 장애 경로 |
| FR-23 | 구현·로컬 검증 | AuthFilter, owner filters, ConnectedIdCipher, backup rules | 사용자 격리 PG, ErrorPrivacyTest, RequestGuardFilterTest | 공개 환경 보안 검토 |
| FR-24 | 운영 준비 구현 | verify-device-free.ps1, CI, OPERATIONS.md, prod TLS | ProductionTlsPostgresTest, SmtpTlsContractTest, lint, 빌드 | 서명·도메인·원격 CI·공개 배포·스토어 승인 |

과거 날짜별 검증은 `docs/verification/`에 보존한다. 요구사항 원문 `docs/specifications/`의 일정·테이블 수·제안 스택은 고정 제약이 아니다. 명시 제외인 결제 실행·자동 카드 선택·모든 쇼핑몰 지원은 추가하지 않는다.

2026-10-22 FR 금융 인증 보강: 거래 조회 추가 인증 API/Android·암호화 만료·합성 계약/PG/에뮬레이터 검증은 [추가 인증 근거](verification/2026-10-22-codef-additional-auth.md) 참조. 실제기관 인증과 계정 생성 인증은 별도 미검증.
