# CODEF 통신 계약 검증 — 2026-10-19

## 결과
- Maven BUILD SUCCESS. 전체84개: CODEF HTTP 계약31개 + 인증 필터 포함 상태 API1개 + 기존 계산52개. 실패/오류/건너뜀0.
- 실제 Java HttpClient→로컬 HttpServer로 요청 경로·헤더·인코딩을 검사했다. 공식 CODEF 서버나 금융기관에는 호출하지 않았다.
- OAuth Basic/form/Bearer, 토큰 재사용·유효기간,401/CF-00401 갱신 한도 확인.
- create/add/update/delete 요청과 비밀번호 RSA 복호화 대조, 성공/부분 성공/전체 실패/기관 불일치 구분 확인.
- 변경 요청의 시간초과/500/401에 재실행 없음. CF-03002 중단,403/429/500/302, 잘못된 JSON/누락/null 데이터, 응답 크기 제한 검사.
- URL 인코딩 응답과 일반 JSON을 구분하고 일반 JSON의 +/% 문자를 보존한다.
- card-list/approval-list 요청 경로·기간 형식과 임의 옵션 차단, 미확인 필터 기본값 생략 확인.
- 비활성 설정에서 네트워크 요청0, 설정 승인/자격 누락 거절, 객체·예외·상태 출력에 합성 비밀 시험문자열 미노출.
- 상태 API는 미인증401, 인증 후200이며 enabled/realConnectionVerified/userConnectionApiReady=false와 비밀 필드 부재 확인. DB는 Mockito 대역이며 실제 사용자 격리 DB 검증과 구분한다.

근거: `.run/codef-tests.log`, `backend/target/surefire-reports/TEST-dev.cardadvisor.finance.CodefClientContractTest.xml`, 같은 폴더 FinanceStatusControllerTest.xml.

## 구현 위치
backend/src/main/java/dev/cardadvisor/finance의 CodefClient/CodefSettings/CodefFailure/CodefConfiguration/FinanceStatusController. 설정 예시 config/codef.example.properties. 테스트 backend/src/test/java/dev/cardadvisor/finance.

## 남은 범위
인증정보 미보유 외에도 사용자 연결 저장/암호화/동의/실제 연결 API·Android 화면, 추가 인증 이어하기, 기관별 응답 정규화·취소/중복/할부·원장 반영·동기화 상태 관리가 남아 있다. 이번 조회 결과는 UnmappedData이며 실제 거래나 완전 동기화로 취급하지 않는다. 현재 실행 중인 백엔드 프로세스를 교체하지 않았다. 실금융 연결·실기기·배포 준비/공개 배포는 미검증이다.

확인한 공식 소스와 사용자 준비 절차: [CODEF 연동 안내](../integrations/CODEF.md).
