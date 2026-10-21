# Android 디자인 상태
- 현재 목표: API 처리 뒤 완료/탐색 대기 실패 수정 — 완료.
- 확정 결정: UI 작업 scope는 Dispatchers.Main.immediate, HTTP는 ApiClient의 IO. 실제 IO와 Compose 검사 실행 문맥이 섞여도 UI 상태/콜백을 Main에서 직렬화한다. 단순 시간 연장으로 통과시키지 않는다.
- 완료: 모든 관련 편집 화면 scope 수정. 원본 debug/androidTest/release 빌드. 실기기 12회 전환+구매/부분취소/월별 저장/재로그인 및 합성 금융 전체 흐름 통과. 진단 개인정보·용량 검사 통과. 계정 정리·설치 지문 일치·정상 앱 시작·API UP.
- 검증 근거: docs/verification/2026-10-19-wait-root-fix.md, outputs/wait-root-fix-20261019. 제품 SHA 954387c908a7e46b43ca478f33004a222874846ff9233708cfe644988f75ca9d. 구매 520f3824-e87b-43e6-8bae-3d9c13a7ec71, 금융 4f641cfd-89d5-4804-a8ab-33ea93e1698d.
- 남은 범위: 이번 대기 실패 작업 없음. 실제 금융 인증·배포 준비·스토어는 별도 미검증. 과거 최초 IllegalArgumentException 원래 스택 미확보 사실은 보존하며 원인 확정을 주장하지 않음.
- 다음 행동 1개: 현재 대기 실패 수정 범위는 완료했으며, 다음 사용자 요청에서 지정한 작업만 진행한다.
