# 실행·검증·운영 준비

## 현재 로컬 구성
Windows PowerShell 7, JDK 21.0.11, Maven 3.9.5, Spring Boot 3.5.16, PostgreSQL 17.6, Gradle 8.14.3, Android SDK/target 35. 라이브러리 버전은 저장소에 고정한다. 호환성 검증 없이 일괄 최신화하지 않는다.

개인 데이터 DB는 Docker의 127.0.0.1:54329, 정상 API는 127.0.0.1:18080이다. `.env`를 읽는 `scripts/start-backend.ps1`은 빌드된 JAR을 숨김 프로세스로 시작하고 PID를 `.run/backend.pid`에 기록한다. 이미 같은 API가 실행 중이면 중복 시작하지 않는다. 프로세스 종료 전 실행 파일·JAR 경로를 확인한다. 기동 시 Flyway가 순서대로 마이그레이션하며 기존 계정·거래를 초기화하지 않는다.

```powershell
./scripts/configure.ps1
docker compose up -d db
./scripts/backend.ps1 package
./scripts/start-backend.ps1
./scripts/android.ps1 build
```

첫 구성에서만 configure를 실행한다. API 상태는 `/v1/health`, 공급자 준비 여부는 로그인 후 `/v1/finance/codef/status`다. health의 UP은 금융기관·AI·메일 성공 증거가 아니다.

## 기기 없이 재현하는 검사
현재 PC의 `.local/checkout-pg.properties`는 별도 PostgreSQL 54330 클러스터를 가리킨다. 이 파일을 다시 만들거나 개인 DB 54329로 바꾸지 않는다. 새 PC라면:

```powershell
./scripts/prepare-test-db.ps1
docker compose --env-file .local/test-db.env -f compose.test.yaml up -d db
./scripts/verify-device-free.ps1
```

`verify-device-free.ps1`은 외부 공급자 설정을 비우고, 합성용 54330만 허용한다. 서버·DB 테스트와 Android 단위 검사·lint·debug/androidTest/release 빌드를 수행한다. 실행 결과는 `.run/device-free-verification.json`, 단계별 비공개 로그는 `.run/verify-*.log`다. 이전 결과가 남을 수 있으므로 passed, sourceCommit, workingTreeDirty를 함께 본다.

Android Studio에서 API 35 이상 AVD를 `emulator-5554`로 실행한 뒤 `./scripts/verify-device-free.ps1 -Emulator`를 실행하면 원장/취소/실적/혜택, 분할 예산/고정비 배분, 오프라인, 주문서 후보→추천, 금융 UNKNOWN 대조, 가맹점 분류 기억을 실제 HTTP와 격리 DB로 대조한다. 실제 쇼핑몰 앱의 화면을 읽는 검사는 아니다. 개인 휴대폰과 개인 앱 세션은 조작하지 않는다. 검사 계정은 종료 시 삭제한다.

GitHub Actions 구성은 `.github/workflows/verify.yml`이다. 비밀 없는 합성 PostgreSQL과 Android 빌드만 실행하며 배포 단계가 없다. 원격 저장소를 연결하거나 원격 CI를 실행한 상태는 아니다.

## 백업·삭제·복원
`./scripts/backup-local.ps1 -VerifyRestore`는 원 DB를 읽어 Windows 현재 사용자 DPAPI로 암호화한 파일을 `.local/backups`에 생성한다. 격리 DB에 복원하고 8개 주요 테이블 건수를 비교한 뒤 격리 DB만 삭제한다. 원 DB에 복원하지 않는다.

`-RestoreFrom <.local/backups의 파일 절대경로>`는 과거 백업을 격리 복원한다. 현재 DB의 삭제 등록부를 적용해 삭제된 계정을 다시 살리지 않는지 검사한다. 기존 파일을 덮어쓰지 않는다. `scripts/test-backup-restore.ps1`은 새 합성 DB에서 과거 백업→계정삭제→복원제외를 검증한다.

현재 삭제 등록부를 읽을 수 없는 재해 복구에서는 이 절차만으로 이전 백업을 운영 DB로 승격하면 안 된다. 삭제 등록부와 암호화 저장 키의 독립적인 보관·복구 경로, 다른 호스트 복구는 운영자 결정이 필요하다. DPAPI 백업은 같은 Windows 사용자에 묶인다. 삭제 등록부는 사용자 UUID와 시각만 보존하며 해당 사용자가 포함된 모든 백업이 폐기되기 전에는 지우지 않는다.

백업 보존 기간은 임의로 정하지 않았다. 운영자가 결정한 일수를 `scripts/prune-backups.ps1 -RetainDays <일수>`로 미리 확인하고, 같은 옵션에 `-Apply`를 붙여 명시적으로 정리한다. 최신 백업 한 개와 삭제 등록부를 보존한다. 이번 작업에서 기존 개인 백업을 삭제하지 않았다.

## 외부 설정과 릴리스
- CODEF: `.local/codef.properties`, Connected ID 암호화 키 `.local/finance-storage.properties`. 실제 기관 인증은 앱에서 사용자가 입력한다. 기관별 추가 인증 방법은 공식 지원이 확인되기 전 활성화하지 않는다. UNKNOWN 대조는 사용자가 버튼을 누를 때만 호출한다.
- OpenAI: `.local/openai.properties`. 꺼져 있어도 결정적 추천·로컬 분석이 작동한다. 합성 계약·로컬 분류 평가와 실제 유료 모델 응답 품질은 별개다.
- 메일: `.local/mail.properties`. TLS 기반 SMTP 전송과 토큰/계정복구 코드는 구현했다. 발송 서비스·계정은 아직 정하지 않았고 실제 발송 검증은 남아 있다.
- 운영 서버: `SPRING_PROFILES_ACTIVE=prod`, `TLS_KEYSTORE`, `TLS_KEYSTORE_PASSWORD`, DB와 공급자 설정 경로를 호스트의 비공개 환경으로 주입한다. prod는 HTTPS만 사용한다. 로컬 자체 서명 TLS 계약 검사는 공개 도메인 인증서 검증을 대신하지 않는다.
- Android release: `android-signing.properties.example`을 `.local/android-signing.properties`로 복사해 로컬에서만 값을 채운다. 서명키는 새 배포의 소유자가 관리한다. 파일이 없으면 미서명 APK가 생성된다. `-Padvisor.apiUrl=https://운영호스트`를 지정하며 기본 `unconfigured.invalid` 빌드는 실서비스에 배포하지 않는다. HTTP·개발용 진단/시험 Activity는 release에 포함하지 않는다.

릴리스 서명·공개 서버·스토어 계정/접근성 정책 심사·개인정보 고지·공식 카드 이미지 배포 권한을 확인해야 공개 서비스로 배포할 수 있다. 이 문서의 준비 코드가 공개 배포나 스토어 승인을 뜻하지 않는다.

공식 참고: [Android 백업 제외](https://developer.android.com/identity/data/autobackup), [앱 서명](https://developer.android.com/studio/publish/app-signing), [CODEF 연동 근거](integrations/CODEF.md).
