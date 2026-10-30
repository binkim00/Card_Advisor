# 비공개 체험 서버 반영·설치 APK — 2026-10-30

## 실제 반영
사용자 승인 범위에서 카드 API만 이미지 `sha256:e21da534e1f5833a3b2b17e3b83cd8cb4d27a39e23fd6a025c30893f4c11c97e`(소스 `f06ccc4549ce27a5341fdb4ecb4503639674f8a7`)로 교체했다. 원본 프로젝트 main에서 개발/커밋. 카드 전용 DB에만 V30 적용, 사전 custom dump의 독립 PG 복원 성공과 암호화 보관 확인. 기존 여기일지도 DB SQL 접근 없음. 기존 컨테이너와 카드 DB 프로세스 ID/이미지/기동시각/재시작/자원 한도는 최초 사전 검사 대비 동일, 기존 API200. 카드 API만 재생성했으며 카드 DB는 재시작하지 않았다.

- 메모리/CPU/swap 제한 유지: API768MiB·0.50CPU, DB384MiB·0.25CPU, swap 추가0. 최종 API healthy/OOMfalse/restart0, 실제 사용량 API206.3MiB/DB38.95MiB.
- 기존 Tailscale19443 루트 API 프록시와443 설정 보존.19443에 APK 파일 경로만 추가, 카드 Funnel 비활성. 방화벽/보안그룹/DNS/기존 프록시 변경 없음.
- `synthetic-preview`를 명시 활성화했다. CODEF/실제 금융 암호화/OpenAI/메일 설정 경로는 공백 유지. 체험 공급자와 OAuth 주소는 컨테이너127.0.0.1로 고정, org9999 가상 기관만 응답. 실설정이 주어지면 시작을 거부하며 별도 체험 키 파일(서버 생성·UID10001·0400)을 사용한다. `realInstitutionAccessEnabled=false`, `syntheticProvider=true`. 체험 기능 enabled는 실제 CODEF 활성화를 뜻하지 않는다.
- 사용자별 명시 체험 연결 준비 버튼을 추가했다. 반환 카드는 `[체험]` 표시와 미확인 계산 보호를 유지한다. 실제 금융기관의 본인 카드 자동 조회가 성공했다는 뜻이 아니다. 기존 지원 상품 직접 등록은 가능하다.

## 검증
체험 전용 PG/소유자/중복 등록·실설정 차단2, 기존 CODEF계약51/정규화4/API계약5 =62 로컬 검사·package 성공. Android JVM15/debug/test 빌드, lint오류0/경고16. 실제 서버에서 합성 가입→체험연결→조회→확인등록→중복방지→정상API삭제 통과.

제공할 APK를 HTTPS 다운로드해 SHA256 `ef94cf1df7903e038ef11a2d865b71add32468d63426a4c0847ccb55aad3a774` 일치를 확인한 뒤, 같은 파일을 별도 designreview 에뮬레이터에 설치했다. 실제 Tailscale HTTPS 서버로 가입→체험 연결→보유 카드 조회→확인 전 저장 차단→등록→목록 재조회/표시1 흐름 최종 통과. 실제 금융 호출/개인 폰/기본 앱 조작 없음. 서버 DB에서도 각 검사 계정에 카드1/조회카드2/미확인규칙1을 대조하고 검사 계정3개만 삭제했다. 다른 사용자의 카드를 조회·삭제하지 않았다.

최초 복원 확인은 임시 DB 초기화 중 Unix socket 준비를 최종 서버 준비로 판단해 실패했다. 최종 TCP/대상DB SELECT1을 기다리도록 수정 후 통과. 첫 API 교체 후 blank 환경 검사에서 출력 끝 빈 줄을 잘못 세어 카드 API만 자동 원복했고, 공백 제거 후 재적용 성공했다. V30은 additive라 원복시 DB를 파괴/되돌리지 않았다. 앱 검사의 첫 두 번은 목록 재조회 대기/고정 정렬 문구 확인에서 멈췄으나 서버 등록은 완료됐고, 검사 파일에 수정이 정확히 반영됐음을 확인 후 최종 통과했다. 앱 본체/이미지 재빌드 없이 검사 APK만 갱신했다.

## 설치와 증거
[폰 APK 다운로드](https://life-infra-map-db.taile29cc8.ts.net:19443/download/card-advisor-preview.apk) — Tailscale VPN 필요. 앱 이름 `카드 어드바이저 체험`, 패키지 `dev.cardadvisor.android.designreview`. 설치법은 `outputs/private-preview/설치방법.md`, 파일은 `card-advisor-preview.apk`. 직접 폰 테스트는 아직 하지 않았다.

서버 근거 `/opt/card-advisor/evidence/preview-20261030T012129Z/result.json`, `postcheck.json`. 로컬 `.run/private-preview-{backend,android,image}.log`, `private-preview-server.json`, `private-preview-phone-result.json`, `private-preview-postcheck.json`, outputs/private-preview/verification.json. 최초 사전 메타데이터 대비 모든 비API 컨테이너 보존, 백업 평문0 확인. 이 확인은 기존 DB 내용 해시나 장시간 공동 부하/재부팅/재해복구 보장을 뜻하지 않는다.

체험 키와 DB/TLS 키는 서버에만 보관하며 APK·Git에 넣지 않았다. 암호화된 사전 백업과 키의 서버 내 보호는 수행했지만 별도 호스트 재해복구 검증은 아니다. 코드·기록 커밋 후 원격 반영/CI 상태는 Git과 `.run/push-receipt.json`을 확인한다.
