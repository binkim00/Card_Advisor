# 최초 GitHub push 전 점검 — 2026-10-28

사용자가 지정한 `https://github.com/binkim00/Card_Advisor.git`는 이력 없는 공개 저장소였다. main에서 승인된 최초 push를 준비했다. 기존 로컬 커밋 이력은 보존하며 다른 브랜치/태그/실행 파일/DB를 전송하지 않는다.

## 검사·수정
- 최초 기준 HEAD8e2977d: 29커밋, 추적372파일, 고유551blob. 현재 파일만이 아니라 HEAD의 모든 과거 파일 버전을 비밀 패턴/로컬 비밀값과 대조했다. 검출0. 금지 경로/미허용 바이너리/대용량 파일 검출0.
- `.gitignore`에 비공개 환경파일, 인증서, DB 압축 백업, heap/성능덤프, 캡처/아티팩트, Python/Android 캐시, AWS/SSH/Kubernetes 설정을 보강했다. 동일 항목을 pre-commit 금지 경로에도 반영해 강제 stage로 우회하지 못하게 했다. 로컬 `.env`와 `.local`의 properties/env 비밀 필드 값은 메모리 안에서만 대조한다.
- 제외 검증12개: 비밀/DB/빌드/캡처/인증서/클라우드 설정 차단, `.env.example`·Flyway SQL 유지. 이미 추적된 제외 대상0. 관련 금융 검증 문서도 직접 읽어 실제 거래 원문/카드번호/인증정보 대신 집계·검증 범위만 기록됐는지 확인했다.
- 비밀 검사 자체5시나리오 + 전체 이력 감사의 과거 커밋 내 삭제된 합성 비밀 검출 회귀 통과. `scripts/audit-git-history.py`로 최종 커밋을 push 직전 다시 검사한다.
- 민감값/일치한 원문은 검사 출력/보고서에 기록하지 않는다. 검사 결과에는 경로/행/규칙/blob/커밋만 저장한다. 바이너리 예외는 기존 공식 카드 이미지 리소스와 Gradle wrapper뿐이다.

## 증거와 반영 상태
`.run/ignore-verification.json`, `.run/history-audit.json`에 최종 감사 근거를 로컬로 보관한다. 실제 push 결과/로컬·원격 커밋 일치는 `.run/push-receipt.json`, `origin/main`으로 확인한다. GitHub CI 결과와 로컬 Android UI/단위/lint 검증을 구분한다. 이 문서는 push 실행 전 검토 기록이며 CI 성공이나 공개 앱/API 배포를 뜻하지 않는다.
