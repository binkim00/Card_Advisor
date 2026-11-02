# 비공개 체험 AI 서버 반영 완료 — 2026-11-02

## 목표·결정
사용자가 OpenAI 키를 카드 앱 전용 비밀 파일로 서버 전송·설치하도록 명시 승인. 실금융 OFF와 기존 여기일지도 서비스/DB·리소스 한도·비공개 연결 보존. 원본 main, 배포 소스 dfebc36.

## 완료·증거
- 로컬 일회용 PG/loopback 공급자 68개 검사 및 이미지 빌드 성공. 서버 이미지 sha256:f495ce7ca0bad34b, API만 교체. 실제 서버의 앱 동일 API→OpenAI gpt-4.1-mini 호출로 추천 설명·소비 분석 OPENAI_VALIDATED/READY·문구 출처 검증 성공. 동의 전/철회 후 CONSENT_REQUIRED, 체험 보유 카드2개·실금융 false 확인. 검사 합성 계정 삭제/잔존0.
- 키는 서버 카드 전용 secret(uid10001/mode0400)으로 설치, 이미지/APK/Git·로그에 원문 미포함. CODEF·금융키·메일 설정 공백 유지. 기존 비API 컨테이너의 ID/이미지/기동/실행/재시작/한도 동일, 기존 API200, 기존 DB SQL 없음. 카드 DB 재시작/스키마 변경 없음.
- API768MiB/0.50CPU·DB384MiB/0.25CPU·추가swap0 유지, APIhealthy/OOMfalse/restart0. Tailscale 구성 동일·카드 Funnel없음. .run/ai-deployment-result.json, .run/ai-postcheck.json 및 서버 /opt/card-advisor/evidence/ai-20261102T013855Z/result.json.
- 최초 서버 AI 검사는 통과했으나 가상 연결 해제 전 계정 삭제가 기존 안전 규칙409에 막혀 카드 API 자동 복귀. 검사 전용 합성 계정1개를 범위 확인 후 정리하고 연결 해제→계정 삭제 순서로 배포 스크립트 수정 후 재검증. 최초 시도 이전 비API 컨테이너/Tailscale 스냅샷과 최종 상태까지 동일 확인. 기존 사용자 데이터 변경 없음.

## 남은 범위
기존 APK의 동의·설명·분석 경로로 사용 가능. 실제 폰에서 터치/UI 검증·장시간 부하·실금융·원격 push는 미실행. AI는 계산·순위·금액 생성 대신 검증된 설명 항목만 선택. 사용자당 AI 하루20회 한도 유지.

## 다음 행동 하나
폰에서 Tailscale 연결 후 기존 체험 APK의 동의·계정 관리에서 AI 사용에 동의하고 소비 분석/추천 설명을 확인한다.
