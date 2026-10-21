# 로컬 Git 작업 규칙

- 기본 브랜치는 `main`. 원격 저장소는 아직 연결하지 않는다.
- 현재 구현 전체는 최초 기준 커밋으로 남긴다. 이전 변경 이력을 복원한 커밋은 아니다.
- 이후 기능·수정 단위로 관련 검사 후 커밋한다. 예: `feat: ...`, `fix: ...`, `docs: ...`, `chore: ...`.
- 커밋 전 `git diff`, 필요한 파일만 `git add -- 경로`, `git diff --cached --stat`, `git diff --cached --check`를 확인한다. 다른 작업의 변경은 섞지 않는다.
- `.env`, `.local`, `.run`, `outputs`, 빌드 결과, 인증서/서명키, DB 파일/백업은 커밋하지 않는다. 제외는 파일 삭제가 아니다.
- 공식 카드 이미지는 앱 리소스의 일부로 보존하며 사용 출처/배포 권한은 별도 관리한다.
- 예제 설정에는 자리표시자만 쓴다. 키·금융 인증정보·전체 카드번호·실제 거래 원문·스크린샷을 문서나 테스트 fixture로 옮기지 않는다.

## 로컬 설정

현재 저장소에 `core.hooksPath=.githooks`, `core.autocrlf=false`, `core.safecrlf=warn`, `core.filemode=false`, `pull.ff=only`, `fetch.prune=true`, `push.default=simple`을 설정한다. 전역 Git 설정은 변경하지 않는다. 줄바꿈은 `.gitattributes`, 편집기 기본값은 `.editorconfig`를 따른다. 기존 소스의 일괄 재포맷은 하지 않는다.

새로 복제한 경우 저장소에서 아래 설정을 다시 적용한다. Git은 복제 시 hooksPath를 자동으로 가져오지 않는다.

```powershell
git config --local core.hooksPath .githooks
git config --local core.autocrlf false
git config --local core.safecrlf warn
git config --local core.filemode false
git config --local pull.ff only
git config --local fetch.prune true
git config --local push.default simple
```

## 커밋 전 검사

Python 3 표준 라이브러리만 사용한다. 설치된 Python이 PATH에 있어야 한다. `pre-commit`은 작업 파일이 아닌 실제 Git 인덱스의 파일을 검사한다. 훅은 `--audit-local`도 실행한다. 금지 경로, 개인키, 알려진 서비스 키/토큰, 예제 외 환경 설정, 의심 바이너리를 거부하며 오류에는 경로·행·규칙명만 출력한다. 모든 개인정보·비밀을 탐지하는 보증은 아니므로 diff 검토를 함께 한다. 검사를 우회하지 않는다.

```powershell
python scripts/check-staged-secrets.py
python scripts/check-staged-secrets.py --audit-local
python scripts/check-staged-secrets.py --self-test
```

`--audit-local`은 비공개 설정의 비밀 필드와 커밋 후보의 정확한 값 일치를 로컬 메모리에서만 검사한다. 비밀 원문은 출력하지 않는다. 테스트는 별도 임시 Git 저장소에서 합성 문자열만 사용한다. 이 검사는 앱·백엔드 테스트나 배포 검증을 대신하지 않는다.
