# 동거 컨테이너 로컬 합성 검증 — 2026-10-28

## 범위와 결론
사용자가 승인한 로컬 Docker 시작, 이미지 빌드, 메모리/CPU 제한 기동과 짧은 합성 부하 검증을 완료했다. 실제 여기일지도 서버에는 접속/배포하지 않았다. 해당 자원 한도에서 소규모 합성 흐름이 동작함을 확인했으며 실제 서버 공동 운영 성능이나 장시간 안정성을 보장하는 결과는 아니다.

검증 앱 소스는 `a5d7bc30d5f7bde034849ab57ace555019cd4a89`이다. 이후 변경은 검증 스크립트와 문서뿐이다. fresh Maven package는 개인 .env/공급자 설정을 읽는 backend.ps1 대신 직접 `mvn -f backend/pom.xml -B -ntp -DskipTests package`로 수행했고 BUILD SUCCESS를 확인했다. 이 명령의 skipTests는 이번에 백엔드 전체 단위/회귀 suite를 재실행하지 않았음을 뜻한다. 아래 HTTP/DB 통합 검사는 별도로 실제 실행했다.

## 격리·보존
- 새 Compose 프로젝트 `card-advisor-synthetic-20261028-115314-84d217`와 새 전용 DB 볼륨만 사용했다. 포트는 loopback HTTPS19443, DB 호스트 포트 없음. API768MiB/0.50CPU·DB384MiB/0.25CPU, 새 swap 허용0을 Docker inspect 및 cgroup v2에서 직접 검증했다.
- 새 합성 DB/TLS 비밀번호와 2일짜리 localhost/127.0.0.1 자체서명 인증서만 생성했다. 별도 인증서를 신뢰 저장소로 전달한 클라이언트에서 hostname/CA 검증을 켰고, 신뢰되지 않은 기본 CA 접근은 거절됨을 확인했다. 시스템 신뢰 저장소는 변경하지 않았다.
- CODEF·금융 암호화키·OpenAI·SMTP 설정은 모두 공백. 로컬 가상 카드/거래만 사용했다. 실제 인증정보·금융 데이터의 복사/조회/전송 없음. 빌드 시 공개 이미지/패키지 다운로드는 수행했다. 최종 테스트는 검토안과 같은 API egress 네트워크를 사용하며 외부 통신 전체가 차단됐다고 주장하지 않는다.
- 기존 컨테이너의 ID/이미지/실행 여부/시작 시각/재시작 횟수/마운트 메타데이터를 검사 전후 비교해 동일함을 확인했다. 개인 `card-advisor-db-1`은 계속 종료 상태다. 기존 DB에 SQL을 실행하지 않았으므로 기존 테이블 내용 해시를 대조했다고 주장하지 않는다.
- Docker 시작 시 기존 restart 정책에 따라 로컬 여기일지도 storage/pgadmin/db가 자동 시작됐다. 이 컨테이너에 별도 시작/중지/재생성/데이터 명령을 실행하지 않았다. 운영 서버의 컨테이너와는 별개다.
- 테스트 완료/실패 시 새 프로젝트 컨테이너만 stop한다. 첫 실패와 최종 성공의 합성 컨테이너/볼륨/이미지 및 비공개 증거는 보존했고, 테스트 컨테이너는 모두 종료했다. reset/prune/down -v/WSL unregister 없음.

## 실제 검사 결과
HTTPS API→PostgreSQL 기능 검사 **16개 통과**: 인증, 가상 데이터 멱등성, 사용자 격리, 카드 추천/예산 golden, 거래 중복, 부분취소·초과취소 거절, 추천 무효화, 버전 충돌, 계정 삭제/세션 폐기.

부하는 dashboard, 카드 실적, 예산 요약, 합성 거래 저장, 주문서 생성/추천, 거래 조회를 섞었다. 각 요청은 HTTPS로 전송했고, 성공 응답과 실제 DB 저장 건수를 확인했다. 사용자 수는 병렬 테스트 클라이언트 수이며 가입자 수/서비스 수용 인원 추정치가 아니다.

| 병렬 클라이언트 | 실제 시간 | 성공 HTTP 요청 | 오류 | 초당 요청 | p50 ms | p95 ms | 최대 ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2명 | 10.0초 | 352 | 0 | 35.2 | 73.0 | 100.09 | 106.92 |
| 8명 | 30.11초 | 1760 | 0 | 58.45 | 104.28 | 202.46 | 398.54 |

합계 **2112개 성공 요청, 오류0**. 테스트 DB는 사용자8/가상카드16/원장301/금융수집거래0, Flyway V29. 원장301건은 클라이언트에서 성공한 쓰기301건과 일치했다.

| 컨테이너 | 메모리 상한 | cgroup memory.peak | OOM/kill | 재시작 |
|---|---:|---:|---:|---:|
| API | 768MiB | 242.29MiB | 0/0 | 0 |
| PostgreSQL | 384MiB | 97.98MiB | 0/0 | 0 |

두 컨테이너는 종료 직전 healthy였다. resource samples 16회, sampling error0. 메모리 최고치는 표본 최대가 아니라 cgroup memory.peak이며 시작 단계도 포함한다. CPU quota/period는 API50000/100000, DB25000/100000으로 직접 확인했다.

## 발견·처리
1. Docker Desktop 4.82.0이 기존 0바이트 AF_UNIX 임시 endpoint를 열지 못해 시작에 실패했다. 실패한 Desktop/backend 프로세스만 종료하고, ordinary directory/정확한 경로/0바이트 endpoint 목록을 확인한 `Docker/run`, `docker-secrets-engine` 두 임시 폴더를 `.cohost-preserved-20261028-114815` 이름으로 보존·재생성했다. Docker settings-store.json SHA256 전후 동일, VHDX/WSL 배포/이미지/컨테이너/볼륨 삭제/초기화 없음. 그 뒤 엔진 정상 시작. 이것은 기동 복구 한 번의 결과이며 재발하지 않는다는 보장이 아니다. [동일 유형 공개 이슈](https://github.com/docker/desktop-feedback/issues/554).
2. 첫 합성 테스트에서 API egress까지 internal로 바꾸자 컨테이너 healthy임에도 Windows published loopback 포트가 거절됐다. 이 테스트 전용 override를 제거하고 원래 검토안 그대로 다시 실행해 성공했다. 첫 실패 보고서/로그도 보존했다. 운영 Compose/앱 소스 수정은 필요하지 않았다.

## 재현·증거
원시 보고서: `.run/cohost-local/20261028-115314-84d217/report.json`. 첫 실패: `.run/cohost-local/20261028-115021-33d309/report.json`. package 로그: `.run/cohost-local-20261028/package.log`. 실제 비밀/PKCS12/DB/원시 실행 로그는 커밋하지 않는다.

JAR SHA256: `18651d933791d14a89ffa48ef0fbf972cfc64fa3b4f7b93c3ea8c9ada142383a`

API image ID: `sha256:25f26a6d0fb1bc9ee2e6daa5ad2718b07ffe753daf95f64013d39e1a26cf5663`

Base: `eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16`

DB: `postgres@sha256:00bc86618629af00d2937fdc5a5d63db3ff8450acf52f0636ec813c7f4902929`

재현은 최신 source를 fresh package한 뒤 `python scripts/verify-cohost-local.py`로 수행한다. 스크립트는 현재 빌드된 backend/target/backend-0.1.0.jar를 사용하며 JAR 해시와 source HEAD를 기록한다. 기존 JAR이 현재 소스와 일치하는지는 자동 추정하지 않는다. Windows 로컬 Docker Desktop(npipe)·JDK21 keytool·빈19443포트가 필요하고 원격 DOCKER_HOST를 거절한다. 기존 환경파일을 읽지 말고 공급자 설정 공백을 유지한다. 새 임시 프로젝트만 만들고 종료 시 그 프로젝트만 stop한다. 컨테이너/볼륨 삭제나 Docker 자동 복구는 재현 스크립트에 넣지 않았다.

## 남은 작업·다음 행동 하나
실제 운영 호스트의 동시 수집 부하, 장시간/대용량 데이터, Linux secret 파일 권한, 운영 인증서·백업/복구·공개 URL·재부팅 복구 정책은 미검증이다. 로컬 Docker는 22CPU·약31GiB 환경이며 cgroup 제한을 적용해도 실제 2vCPU/4GB 호스트와 동일한 경쟁 상태가 아니다.

다음 행동: 이 로컬 합성 결과와 기존 구성으로 실제 서버 적용 전 검토를 받는다. 사용자가 실제 배포를 승인하기 전에는 서버 전송/설정/기동을 하지 않는다.
