# Tailscale 비공개 Android 연결 — 2026-10-28

## 결과와 범위
사용자가 선택한 Tailscale 비공개 연결을 구성하고, 디자인 개선이 포함된 동일 APK에서 실제 서버 API→전용 PostgreSQL 흐름을 에뮬레이터로 검증했다. 실기기는 현재 연결 불가이므로 미검증. 실제 금융/AI/메일 호출과 인터넷 공개는 수행하지 않았다. main e548a20 기반.

## 서버 연결과 보존 증거
- 주소: `https://life-infra-map-db.taile29cc8.ts.net:19443`.
- Tailscale Serve 전용 HTTPS19443 → 서버 loopback `https+insecure://127.0.0.1:19443`. 로컬 self-signed TLS 구간만 서버 프록시가 허용하고, Android/PC→Serve 구간은 정상 인증서/호스트 검증을 사용한다. 앱 TrustManager/HostnameVerifier/네트워크 보안 XML 변경 없음.
- 카드19443에 AllowFunnel 없음. 기존443 Serve/Funnel 항목은 원래 설정이며 그대로 보존했다. 전체 Serve JSON에서 새 TCP19443/Web 항목만 제외하면 변경 전과 동일. 기존 공개 설정을 카드 공개로 확대하지 않았다. 보안그룹·DNS·기존 프록시 변경 없음.
- 전체16컨테이너 ID·이미지·기동시각·실행상태·재시작 횟수·메모리·CPU 한도 전후 동일. 카드 API768MiB/0.50CPU, PG384MiB/0.25CPU 유지. 기존 여기일지도 API200. 기존 DB SQL 실행 없음. 데이터 내용 해시나 장시간 공동 부하 검증은 아님.
- CODEF_CONFIG_FILE/CODEF_STORAGE_KEY_FILE/OPENAI_CONFIG_FILE/ACCOUNT_MAIL_CONFIG_FILE 공백 재확인. 비밀 값은 출력/저장하지 않음.
- 서버 증거: `/opt/card-advisor/evidence/tailnet-20261028/{before,result}.json`, `android-3381fd96-82c5-431d-a294-d2c6fd14276c.json`.

## APK 및 Android 증거
- `advisor.apiUrl` HTTPS 호스트/포트만 debug 빌드에 명시 주입. 미지정 기존 로컬 기본값 유지. release 동작 변경 없음.
- 패키지 `dev.cardadvisor.android.designreview`: 원래 앱/로그인/개인 기기 데이터와 분리. debug 테스트용이며 스토어 배포 아님.
- 결과: debug + androidTest 빌드 성공, JVM15 실패0, lint 오류0/경고16. 생성 BuildConfig에서 실제 HTTPS 주소 확인.
- `BlueDeviceFlowTest` 1개 전체 흐름 성공: 가입→가상 카드2개→추천→구매50,000원→과다취소 차단→부분 취소20,000원→예산 재계산→월별 값 저장→로그아웃/재로그인/서버 재조회. 비동기 UI 완료/HTTP 재개 MAIN 검사 통과. 새 디자인에 맞춰 접기 버튼과 설정 내 로그아웃 접근을 검사 코드에 반영했다.
- 새 카드 DB 대조: 구매50,000/취소20,000/순지출30,000, 연결혜택300/잔여9,700, 실적300,000, 다음 구매 후 예산40,000. 두 검사에서 만든 정확한 합성 이메일만 삭제 후 사용자/카드/원장/금융거래0.
- 에뮬레이터5564의 별도 앱만 설치/초기화했다. 기존 기본 패키지와 개인 DB는 조작하지 않았다. USB reverse/로컬 API/모의 응답 없음. 에뮬레이터 NAT가 Windows MagicDNS를 상속하지 않아 검사 동안 loopback DNS로 해당 이름을 확인된 Tailscale IP에만 해석했으며 HTTPS 인증서 검증은 그대로다. 실기기는 자체 Tailscale VPN/MagicDNS로 접속해야 한다.
- 최초 SDK 환경 누락은 실행 환경을 지정해 수정했다. 최초 UI 검사는 접기 버튼 문구 불일치로 중단됐고, 앱 본체를 재빌드하지 않고 검사 APK만 수정해 최종 통과했다. 실패 증거/합성 계정 정리는 별도로 보존.
- 로컬 결과 `.run/private-ui-result.json`, `.run/private-server-database.json`; 합성 앱 캡처/trace `outputs/private-android/3381fd96-82c5-431d-a294-d2c6fd14276c/`.
- APK `outputs/private-android/card-advisor-private.apk`, SHA256 `f62e5a4dd8df9558e1f66de7e31797634e9a362997735d4a87cb6b68ef2b0290`.

## 휴대폰에서 사용하는 방법
1. Tailscale 앱을 설치하고 서버와 같은 tailnet에 가입한 뒤 VPN을 켠다. 필요한 기기 승인/접근 권한은 해당 tailnet 정책을 따른다.
2. 위 비공개 주소의 `/v1/health`가 열리는지 확인한다.
3. `card-advisor-private.apk`를 설치한다. 기존 앱과 별도로 설치되며 새 서버의 테스트 계정으로 사용한다. 실제 금융 연결은 진행하지 않는다.

서버 주소는 APK에 포함되므로 PC/USB 터널이 필요하지 않다. 실제 폰의 VPN·DNS·권한·터치/레이아웃은 아직 확인하지 않았다.

## 재현과 해제
빌드: JAVA_HOME(JDK21), ANDROID_HOME(SDK)을 지정한 후 `./scripts/build-private-android.ps1 -ApiUrl https://life-infra-map-db.taile29cc8.ts.net:19443`. APK/테스트APK/build.json을 outputs/private-android에 보존한다. 이 스크립트는 서버 설정·폰 설치·DB를 변경하지 않는다.

최초 연결 스크립트 `scripts/enable-cohost-tailnet.py`는 지정 인스턴스·기존 포트 부재를 확인한 뒤 새 포트만 추가한다. 이미 구성된 서버에 재실행하면 변경 전에 중단한다. 카드 연결만 해제하려면 서버에서 `sudo tailscale serve --https=19443 off`. `serve reset`은 기존 서비스를 지우므로 사용하지 않는다.

Tailscale 공식 근거: [Serve 명령과 HTTPS 프록시](https://tailscale.com/docs/reference/tailscale-cli/serve), [Serve는 tailnet 내 연결](https://tailscale.com/docs/features/tailscale-serve).

## 남은 작업
폰 연결 가능 시 동일 APK로 자체 Tailscale을 통한 실제 기기 검사. 장시간 공동 부하/백업·복구/호스트 재부팅 복구/실금융/공개/스토어 배포는 이번 증거 범위에 포함하지 않는다. 원격 Git push/CI 미실행.
