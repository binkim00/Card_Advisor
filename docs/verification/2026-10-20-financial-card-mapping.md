# 금융 카드와 실제 상품의 사용자 확인 연결

구현: FinancialCardMappingService/Controller, V19, Android FinancialCardMappingEditor 및 FinanceEditor 진입. 조회 연결의 마스킹·공급자 표시명 그룹을 서버에서 만들고 sourceKey로 본인 등록 실제 상품과 연결한다. 이름/부분번호로 상품을 자동 확정하지 않는다. KB0301은 KB검수상품, 하나0313은 하나검수상품만 허용하며 가상 상품·타사용자 카드 거부. 연결/변경/해제는 직접 확인과 매핑·기관 연결의 기대 버전을 요구한다. 해제는 버전 tombstone을 남겨 ABA 충돌을 막는다.

API: GET/PUT /v1/finance/connections/{id}/card-mappings. 입력 sourceKey,cardId(null 해제),expectedVersion,expectedConnectionVersion,confirmed=true. 조회는 groups/targets/connectionVersion과 association-only 정책을 반환. 카드 미등록 시 같은 화면에서 기존 공식 상품 등록 API를 호출해 등록한 뒤 다시 직접 선택할 수 있다.

계산 경계: 매핑 관계만 저장하며 financial_transactions/ledger_entries/card_id/금액/취소를 복제·수정하지 않는다. 연결된 금융 거래는 mapped_card_name으로 표시. 해당 월 실제 카드의 미검수 금융거래 실적/혜택은 null·미확인으로 표시하고, 새 추천은 FINANCIAL_CARD_CALCULATION_REVIEW_REQUIRED로 보류한다. 현재 자격은 별도 입력을 보존한다. 거래별 제외·실적/혜택 룰을 실제 금융거래에 적용하는 후속 구현은 이번 완료 범위가 아니다. 연결만 하고 가상 규칙으로 계산하지 않는다.

검증:
- FinancialSyncPostgresTest22개 통과: 매핑 직접 확인·미선택 차단, 저장/재조회, 중복 요청·버전충돌·해제/ABA, 사용자/기관/가상규칙 격리, 중복 원장·환불 없음, 혜택null/실적 미확인/추천 보류 포함.
- 에뮬레이터→Spring API→실 PostgreSQL AndroidSyncPostgresTest1개 전체 흐름 통과. 상품 등록→선택·확인→저장→재조회→저장충돌→새로고침→해제→재연결(version4), 기존 구매/부분취소/중복/오류복구/재로그인 포함. 공급자는 테스트 공백설정/loopback으로 고정. 합성계정 삭제.
- debug/test APK 빌드 및 SM-F741N 수정APK 설치 성공. 기기 화면 Dozing으로 새 기능의 실기기 조작 검사는 잠금해제 요청 후 대기 중. 에뮬레이터 성공을 실기기 성공으로 주장하지 않는다.
- 실행 서버 PID48156/health200/V19. 실제 금융190/원장187/중복원장연결0/실사용자 상품연결0/미연결취소1 확인. CODEF 추가 호출·실상품 자동 매핑·남은 취소 연결 없음.

근거 .run/card-mapping-pg.log, card-mapping-ui.log, card-mapping-android-build.log, card-mapping-ui-build.log, card-mapping-real-state.json. OpenAPI 및 DECISIONS 갱신. 원문카드번호/인증정보/실제 표시명을 진단 출력하지 않았다.
## 추가 실기기 검증 완료
사용자 잠금해제 후 SM-F741N에서 합성 카드연결/충돌/해제/재연결 및 기존금융전체흐름 통과. card_mapping 단계 포함, 합성계정 삭제. 실제 금융190/원장187/중복0/실사용자매핑0/미연결취소1 보존. USB18080 복원 및 테스트 세션 제거를 위해 일반앱 재시작. 실제 사용자 로그인·상품선택은 사용자가 직접 수행. 근거 .run/card-mapping-physical.log, card-mapping-physical.json, card-mapping-post-physical-state.json. 위의 실기기 대기 상태는 이 결과로 해소됨.

