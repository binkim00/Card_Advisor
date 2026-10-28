# 여기일지도 서버 동거 구성 검토안 — 2026-10-28

현재 단계는 구성 준비와 Compose 정적 검증이다. 서버 파일 전송, 이미지 빌드/실행, 배포, 기존 컨테이너/DB/네트워크/프록시/방화벽 변경은 하지 않았다. 외부 공개용 구성이 아닌 비공개 검증 시작점이다.

## 구성과 한도

| 구성 요소 | 메모리 최대 | CPU 최대 | 접속/저장 |
|---|---:|---:|---|
| 카드 API | 768MiB | 0.50 vCPU | 호스트 `127.0.0.1:19443` → 컨테이너 HTTPS `18443` |
| 카드 전용 PostgreSQL 17.6 | 384MiB | 0.25 vCPU | 호스트 포트 없음, 신규 전용 `pgdata` 볼륨 |
| 합계 | 1152MiB = 1.125GiB | 0.75 vCPU | 기존 여기일지도 리소스 참조 없음 |

CPU 값은 최대 사용량이며 예약량이 아니다. 두 컨테이너의 `memswap_limit`은 `mem_limit`과 같아 새 스왑 사용을 허용하지 않는다. API의 Java heap은 384MiB이며 컨테이너 768MiB 한도에는 native memory, 스레드, tmpfs 등도 포함한다. 이 수치는 실부하 검증을 마친 보장값이 아니다.

호스트의 최근 7일 가용 메모리 최저 2.06GiB, 디스크 여유 최저 21.88GiB, CPU 5분 평균 최고 75.4%를 바탕으로 초기 한도를 정했다. 추가 후 여유는 측정해야 한다. 디스크에는 컨테이너별 로그 회전(각 10MB × 3)을 설정했지만 DB 볼륨의 강제 용량 상한은 없다. 데이터·이미지·백업 증가를 감시하고 호스트 여유 15GiB 미만이면 확대/정리를 검토한다. 전체 파일 시스템 삭제나 Docker prune은 제안하지 않는다.

Compose 프로젝트명은 `card-advisor-cohost`이다. 기본 생성 이름은 `card-advisor-cohost-api-1`, `card-advisor-cohost-db-1`, `card-advisor-cohost_pgdata`이다. 기존 여기일지도 Compose 파일과 병합하지 않으며, 외부 네트워크·기존 DB·Docker socket·호스트 경로를 공유하지 않는다. DB는 internal 네트워크만 사용하고 API에는 별도 egress 네트워크를 둔다. 컨테이너 분리는 같은 호스트의 디스크 I/O·커널·장애까지 완전히 분리하지 않는다.

## 외부 접속과 비밀 설정

API는 기존 prod HTTPS 설정을 사용한다. 80/443 포트, 기존 리버스 프록시, 도메인/DNS, 보안 그룹은 건드리지 않는다. 첫 접속은 승인된 배포 이후 SSH 터널로만 검증한다. 공개 URL과 Android release 연결은 별도 결정이며 현재 구성으로 휴대폰의 일반 인터넷 접속이 가능한 것은 아니다.

DB 비밀번호와 TLS PKCS12/비밀번호는 `CARD_SECRETS_DIR`의 파일을 Compose secrets로 읽는다. API는 Spring Boot 3.5의 `configtree:/run/secrets/`로 비밀번호 파일을 읽고, DB는 `POSTGRES_PASSWORD_FILE`을 사용한다. 이 기능이 파일을 자동 암호화하지는 않는다. 이미지/저장소/예제에 실제 값은 넣지 않는다.

Linux에서 API는 UID/GID 10001로 실행하므로 비밀 파일의 소유권과 읽기 권한을 별도 준비해야 한다(예: 파일 소유자 10001, 모드 0400, 부모 디렉터리 root 소유 0700). 파일 기반 Compose secrets는 uid/gid/mode 재매핑을 보장하지 않는다. PKCS12의 private key entry 비밀번호는 keystore 비밀번호와 맞춰야 하며, 도메인/SAN·만료·체인·신뢰 검증은 실제 배포 전 수행한다.

CODEF·암호화 키·OpenAI·SMTP 설정 경로는 빈 값으로 고정했다. 실제 금융/유료 호출을 시작하거나 개인 PC의 계정·거래를 복사하지 않는다. 금융/메일 기능 전체를 검증할 수 있는 구성이라고 주장하지 않는다. `/v1/health`는 앱의 HTTP 응답만 확인하며 DB 정합성이나 외부 연동 성공을 증명하지 않는다. healthcheck의 `--insecure`는 컨테이너 내부 loopback 점검에만 사용하고 사용자/API 클라이언트의 인증서 검증을 끄지 않는다.

## 빌드와 적용 경계

이미지는 PC/CI에서 현재 소스를 package한 JAR로 빌드한다. Docker build context는 이 폴더 하나이며 `.dockerignore`가 `Dockerfile`·`app.jar`만 허용한다. `.local`, 설정/비밀, 실제 거래는 build context에 넣지 않는다. 서버 Compose에는 `build` 항목이 없고 사전 빌드 이미지의 고유 태그/확정 digest만 전달한다. `preview.env.example`의 태그는 아직 존재하지 않는 검토용 이름이다. 실제 배포 전 base/PostgreSQL/API 이미지 digest·소스 commit·JAR/image 해시를 기록한다.

검토용 렌더링(컨테이너 생성/기동 없음):

```powershell
docker compose --env-file deploy/cohost/preview.env.example -f deploy/cohost/compose.yaml config --quiet
```

실제 `up` 명령은 이 단계에서 실행하지 않는다. 승인 후 제안할 서버 작업 위치는 `/opt/card-advisor`이며, 아직 만들지 않았다. 기존 `/home/ubuntu/life-infra-map` 경로를 사용하지 않는다. 기존 로그인/금융/거래는 PC에 남고, 이 구성은 빈 카드 전용 DB를 전제로 한다. Flyway는 그 DB에만 실행된다.

신규 컨테이너의 재시작은 실패 시 최대 3회다. 호스트 재부팅 후 자동 복구를 보장하는 운영 정책은 이 비공개 검토안에 포함하지 않았다. 이미지 빌드/제한된 메모리 기동/HTTPS/DB 마이그레이션/재시작/백업 복구/부하 검증은 아직 미실행이다. Linux용 암호화 백업과 별도 호스트 보관·복원 절차도 운영 전 준비해야 한다. 기존 Windows DPAPI 백업을 서버에서 그대로 복구할 수 있다고 가정하지 않는다.

## 실제 배포 승인 전 확인할 내용

1. 이 구성의 메모리·CPU 한도와 SSH 터널 전용 검증 경로 검토.
2. PC/CI에서 이미지 빌드와 합성 전용 환경에서 resource limit·TLS·API→DB 검증. 기존 개인 DB/서비스 및 기존 운영 서버에서는 실행하지 않는다.
3. 새 TLS/DB 비밀 파일 준비, 저장/백업 정책과 이미지 digest 확정. 새 설정은 나중에 승인된 범위에서만 준비한다.
4. 배포 직전 기존 컨테이너 상태/디스크/가용 메모리와 19443 포트 미사용을 읽기 전용으로 재확인.
5. 실제 적용은 사용자 승인 후 카드 프로젝트의 두 컨테이너만 대상으로 한다. 중단도 그 두 컨테이너만 대상으로 하며 `down -v`, 광역 stop/restart, 네트워크/볼륨/이미지 prune은 하지 않는다. 여기일지도 상태와 응답을 전후 대조하고 악화되면 새 카드 컨테이너만 중단한다.

근거: [Compose 자원/서비스 설정](https://docs.docker.com/reference/compose-file/services/), [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/), [Spring Boot 3.5 configtree](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html#features.external-config.files.configtree).
