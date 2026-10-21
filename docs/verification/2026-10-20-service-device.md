# 새 APK 실기기 통합 검증 — 2026-10-20

## 결과
- SM-F741N/Android17, USB R3CX701TE5J. 새 debug APK 설치 성공, 설치본/빌드본 SHA256 일치: `4a78cac5d1c3299c1ee10a74aaefc2fed2436101e0dc02a836bfd5db4ab65753`.
- 실제 카드 상품 규칙에 **합성 구매 입력**을 사용했다. 실제 카드사 금융 조회/결제/유료 AI 호출은 하지 않았다.
- 신규 앱→실행 중 Spring API18080→PostgreSQL 검증: `3188afaa-6804-412c-8b88-2670d4f4db11`. 가입, 실제 상품3종 등록, 월 예산 저장, 목적별 동의/철회, 세션 갱신, 약관 예상 추천, AI 미설정 기본 설명, 수동 구매·부분 취소, 재로그인 보존, 비밀번호 확인 계정 삭제 통과.
- DB 대조: 카드3, 구매50,000원, 취소20,000원, 순지출30,000원, NAVER_PLUS_STORE 확인 정보, 트래블로그 규칙 버전, 취소 접수일 저장. 앱의 연결 구매 순예상적립90원과 다음5만 원 구매 후 전체 예산40,000원 확인. AI 동의 false/동의 이벤트3개, 삭제 후 해당 합성 계정0.
- 감지 상태전이 검사 CheckoutDetectionTest 통과. 두 쇼핑몰별35회 금액 변경·모호한 총액·인증화면·제목 없음·허용하지 않은 패키지·세션 종료를 합성 관측값으로 검증했다. **실제 주문 경로 검증이 아니다.**
- 기존 금융 동기화 실기기 회귀: `c0d92bf6-7275-4f50-ad8b-481d54a78c85`, AndroidSyncPostgresTest1개 통과. 실기기→실제 API→합성 공급자→PostgreSQL. 공급자 호출6/작업5/원장4/취소2·40,000원/예산 순지출170,000원/보류0. 충돌·조회 실패·응답 유실·UNKNOWN·재로그인·만료 세션 포함. 검사 합성 계정 정리.
- 종료 후 USB18080 전달과 일반 앱 시작을 복원하고 API UP 확인.

## 발견·수정
- 최초 기동 시 Docker Desktop4.82.0의 기존 dockerInference 런타임 소켓 오류 재발. 공식 `docker desktop stop --force` 후 확인한 소켓 전용 두 폴더만 같은 부모의 보존 폴더로 이동하고 원래 위치에 빈 폴더를 생성했다. 보존 경로는 `.run/docker-runtime-preserved-20261020.json`에 기록. DB 디스크/이미지/볼륨 삭제나 공장 초기화 없음. 기존 card-advisor-db-1 healthy, V12/V13/V14 적용 유지. 다른 기존 볼륨 내부 전체 무결성이나 재부팅 후 재발 방지는 이번 증거 범위 밖이다.
- ServiceCompletionFlowTest가 동의/취소 대화상자의 버튼·입력에도 항상 performScrollTo를 호출해 AssertionError 발생. 표시된 노드는 바로 조작하고, 화면 밖 노드에만 스크롤하도록 검사 helper 수정. 첫 실패 위치33, 두 번째39 이후 전체 통과. 앱 제품 코드는 이번에 바꾸지 않았다. 원시 메시지/입력 화면은 저장하지 않았으며 최초 예외 문구 자체는 보존되지 않았다.
- runner는 원시 instrumentation 출력을 저장하지 않고 실패 타입/소스 위치/허용한 reasonCode만 기록. 두 실패 실행의 정확한 합성 계정만 정리했다.

## 근거
- `.run/service-device/3188afaa-6804-412c-8b88-2670d4f4db11/verification.json`, database.json, evidence.json, request-trace.jsonl, runtime.json
- `.run/android-sync/c0d92bf6-7275-4f50-ad8b-481d54a78c85/verification.json`, `.run/service-resume-finance.log`
- 소스: android/app/src/androidTest/java/dev/cardadvisor/android/ServiceCompletionFlowTest.kt, CheckoutDetectionTest.kt; scripts/android-service-test.ps1.
- CODEF 신청·발급 안내: [CODEF-SETUP](../integrations/CODEF-SETUP.md). 공식 공개 멤버십/가입/로그인 화면과 공식 Java SDK를 현재 확인. 회원가입·본인인증·데모 신청·실제 호출 활성화는 수행하지 않았다.

## 완료와 미완료의 경계
이번 요청의 새 APK 설치와 신규/기존 합성 금융 흐름 실기기→API→DB 검증은 완료했다. 전체 서비스, 실제 금융 연결, 실제 네이버플러스 스토어/무신사 주문 감지, 실제 OpenAI 호출, 운영·백업/CI 초안 적용, 공개 배포/스토어 승인은 미완료다.
