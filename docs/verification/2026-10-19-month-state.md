# 월별 실적·현재 혜택 자격 입력 검증

2026-10-19, 프로젝트: `C:\Users\SSAFY\Documents\card-advisor`.

## 구현 완료 범위
- Android 설정 → 월별 실적·자격 수정 화면: 카드/월 조회, 미기록 실적(0원과 미확인 구분), 자격 충족/미충족/미확인, 기준 월, 값별 출처·확인일 수정.
- USER_INPUT(사용자 확인), USER_ESTIMATE(사용자 추정), UNKNOWN을 구분한다. 카드 자체는 가상이며 공식 금융 조회값으로 표시하지 않는다.
- 가상 규칙의 현재 자격은 직전 월을 기준으로 한다. 이번 달 미기록 실적과 연결 거래는 다음 달용 실적으로 계산하며 현재 자격을 자동 변경하지 않는다.
- V4 마이그레이션으로 값별 출처·확인일·자격 기준 월을 보존한다. 기존 V1~V3와 가상 데이터는 유지하고, 기존 값의 확인일을 임의 생성하지 않았다.
- PUT 저장 후 GET 재조회. 상태 버전 충돌409 시 내 초안을 유지하고 자동 덮어쓰기를 차단한다. 명시적 최신 값 조회 후 재수정한다.
- 잘못된 기준 월·미래 확인일·음수 금액·공식 출처 사칭을 거절한다. 미확인은 null/UNKNOWN/확인일null 조합으로 보존한다.
- 저장 성공 시 추천 무효화, 저장 중 탭 이동 방지, 네트워크 오류 안내를 구현했다. 화면 검토 후 저장 결과·충돌 안내를 저장 버튼 가까이 옮겼다.

## 로컬 검증 완료
| 검사 | 결과 | 근거 |
|---|---|---|
| 백엔드 빌드·계산 테스트 | 25개 통과 | .run/month-state-backend-build.log |
| Android 앱·테스트 APK 빌드 | 성공 | .run/month-state-android-build.log, month-state-android-final.log |
| 신규 월별 API | 7개 검사 통과, 부정 입력 7조합 포함 | .run/month-state-api.json |
| 기존 실적·취소 API 회귀 | 10개 검사 통과 | .run/card-performance-api.json |
| 월별 Android→API→PostgreSQL | 최종 1시나리오 통과, 12.185초 | .run/android-month-state/0b55e0be-b730-4ceb-8167-a455d22fd95e/verification.json |
| 기존 가입→추천→구매→부분취소 | 1시나리오 통과, 8.760초 | .run/android-ui/eed1ff60-2abd-476f-bd19-c66ac856952c/verification.json |
| PostgreSQL 마이그레이션 | V4 적용 성공 | .run/backend.log |

월별 Android 시나리오: 가입·가상 카드 준비 → B카드 310,000원/현재 자격false 저장 → 다른 실제 API 세션이 330,000원/true로 변경 → 앱에서 옛 버전으로 320,000원 저장 시 충돌 → 입력320,000원 보존 확인 → 최신330,000원 조회 → 340,000원/false 재저장 → 미확인 저장 및 null 조회 → 알려진 값 복구 → 이전 월 미확인 확인 → 현재 월 재조회 → 재로그인 유지.

최종 DB 대조: `untracked_performance_won=340000`, `current_qualified=false`, 두 출처 `USER_INPUT`, 두 확인일 `2026-10-19`, 자격 기준 월 `2026-09-01`, 적용 월 `2026-10-01`, `version=6`, 잔여한도 `10000` 보존. 정확히 해당 실행의 합성 계정만 삭제하고 잔존0을 확인했다.

기존 Android 경로: 카드 B 기초270,000 + 승인50,000 − 부분환불20,000 = 실적300,000, 순지출30,000, 다음5만 원 구매 후 전체예산40,000. 현재 자격true 유지, 재로그인 후 동일.

## 실행 방법
1. Docker Desktop을 실행하고 프로젝트 루트에서 `docker compose up -d --wait db`.
2. 백엔드가 꺼져 있으면 `./scripts/start-backend.ps1`. 이미 실행 중이면 중복 시작하지 않는다. 변경 후 빌드가 필요하면 해당 프로젝트 서버를 종료하고 `./scripts/backend.ps1 package` 후 시작한다.
3. Android 에뮬레이터를 실행하고 `./scripts/android.ps1 monthly-test`. 앱/검사 APK 빌드와 설치, 앱→API→DB 검증, 합성 계정 정리까지 수행한다.
4. API만 확인하려면 `python scripts/month_state_smoke.py`, 실적/취소 회귀는 `python scripts/card_performance_smoke.py`.
5. 직접 사용: 앱 가입/로그인 → 설정 → 가상 데이터 준비 → 월별 실적·자격 수정. 원장에 이미 기록한 거래를 제외한 미기록 금액만 입력한다.

API: `GET /v1/cards/performance?month=YYYY-MM`, `PUT /v1/cards/{id}/months/{month}`. 상세 계약은 docs/api/openapi.json. 토큰·비밀값을 보고서나 채팅에 기록하지 않는다.

## 상태 구분과 제한
- 요청한 월별 입력 기능 구현 및 로컬 연결 검증: 완료. 전체 FR-01~24: 미완료.
- 실제 금융 연동 확인: 미실시. 공식 상품 약관/복합 기간/금융 조회 근거는 별도 구현 필요.
- 실제 기기 확인: 미실시. 이번 검증은 emulator-5554(Android 16).
- 배포 준비: 미완료. 공개 배포·스토어 제출·승인 없음.
- 오프라인 오류 처리 코드는 있으나 기기 네트워크 차단/프로세스 종료/회전 후 초안 복원은 이번 시나리오에서 검증하지 않았다. 로그인 토큰과 초안은 앱 메모리에 유지한다.
- Docker 런타임 소켓 오류가 재발했다. 런타임 폴더를 보존 이동한 후 정상 시작했고 기존 DB 볼륨은 유지했다. .run/docker-recovery-20261019.json. 재부팅 후 재발 방지·기존 볼륨 전체 무결성 검증은 별도다.

다음 행동 하나: 가상 규칙의 카드별 혜택 사용액·잔여 한도를 수동 구매와 부분 취소에 연결해 자동 재계산하고 앱→API→DB로 검증한다.
