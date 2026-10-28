# Android 주요 화면·버튼 배치 개선 — 2026-10-28

## 변경 결과
기존 main f681ea6을 기준으로 앱 표시 계층만 개선했다. 화이트·블루 톤, 회색 배경/흰 정보 카드, 일정한 간격·글자 위계·입력창/버튼 모서리를 사용한다. 기능·금융 계산·예산 의미·API/서버 주소는 유지했다.

- 홈: 브랜드/월 소비 제목을 구분하고 남은 예산→카드 비교→내 카드→관리 기능 순서를 유지하면서 정보 카드와 주요 행동의 대비를 정리했다. 개발 초기 기본 문구 한 개만 제외하고 사용자 안내·오류는 2줄/펼치기로 보존했다. 미확인 값은 0으로 바꾸지 않는다.
- 추천: 기존 compareCards 동작/testTag를 그대로 Scaffold 하단 고정 영역으로 이동했다. 긴 내용 스크롤과 관계없이 버튼이 표시되며 busy일 때 버튼/탐색이 비활성화된다. IME insets는 Scaffold 전체에 반영해 입력 중 레이아웃 공간을 확보한다.
- 설정: 카드·금융/소비 관리/앱 설정으로 메뉴를 그룹화했다. 기존 이동 대상·문구·로그아웃 동작은 보존하고 로그아웃은 내용 아래로 옮겼다. 상세 화면에는 설정 복귀 경로를 표시한다.
- 로그인: 중복 소개 문구를 줄이고 이메일 키보드와 둥근 입력창을 적용했다. 로그인은 전체 폭 기본 버튼, 가입은 보조 버튼, 비밀번호 찾기는 별도 링크로 배치했다. 기존 인증/길이 규칙·오류 표시는 유지했다.

## 실제 검증
- 별도 debug 속성 `-Padvisor.previewSuffix=.designreview`를 추가했다. 기본 패키지/서명/API 주소는 바꾸지 않으며, 허용한 suffix 값 이외에는 거절한다. 기존 실제 앱 대신 `dev.cardadvisor.android.designreview`/test 패키지만 emulator-5564에 설치했다. 기존 AVD를 데이터 초기화 없이 headless/no-snapshot-save로 사용했고 개인 휴대폰에는 접근하지 않았다.
- debug/androidTest 빌드와 일반 기본 debug 빌드 성공. 후자의 APK는 빌드만 했고 설치하지 않았다.
- Android JVM15/실패0/오류0/skip0. lint Error/Fatal0, Warning16은 남아 있으며 lint 전체가 경고까지0이라고 주장하지 않는다.
- Compose 디자인 검사6 통과: 로그인 버튼/입력 접근, 하단 탐색·처리 중 비활성화·공식 카드 이미지, 추천/가계부 표시, 예산 미확인의 0 오표시 방지, 긴 내용에서 고정 CTA 도달·중복 조작 차단, 그룹 메뉴 이동/로그아웃. 기본 탐색·캡처 관련1개를 추가 재실행해 통과했다.
- 합성 composition을 실제 에뮬레이터에서 PNG로 캡처한 뒤 홈/설정/추천/로그인 레이아웃을 시각 점검했다. 숫자와 카드 자격은 예시다. 이미지 편집/생성 목업이 아니라 앱의 Compose UI 렌더링이지만 실제 금융·계정 값은 아니다.

## 증거·미리보기
로컬 전용 `outputs/design-review-20261028/`에 home-top.png, home-cards.png, recommendation.png, ledger.png, login.png, settings.png, compare-action-docked.png를 보관한다. 마지막 파일의 긴 조건 목록은 CTA 위치를 검사하기 위한 테스트 fixture이며 실제 상품 화면으로 제시하지 않는다. 스크린샷/빌드 결과는 기존 규칙대로 커밋하지 않는다.

원시 결과 `.run/design-review-result.json`, build `.run/design-review-build.log`, 기본 패키지 `.run/design-review-default-build.log`, UI 검사 `.run/design-review-instrumentation.log`. 기본 debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`. 미리보기 패키지의 앱 데이터/인증은 기존 앱과 분리된다.

## 검증 경계
이번에는 서버 접속·배포·DB 조회·금융/유료 AI·메일 호출을 하지 않았다. 여기는 표시·조작 검사이며 앱→새 서버 연결 테스트, 실제 사용자 계정/실금융/실기기/큰 글꼴·다른 화면 크기의 검증을 대신하지 않는다. 개별 금융/예산 편집 폼의 전체 시각 재설계는 이번 변경에 포함하지 않았다. 서버의 기존 비공개 컨테이너 배포 상태는 이번 턴에 변경하거나 재조회하지 않았다.

## 다음 행동 하나
사용자에게 합성 화면으로 실제 UI 변경을 보여 주고 디자인 피드백을 반영한다. 서버 연결 검증은 사용자가 지정하는 단계에서 별도로 진행한다.
