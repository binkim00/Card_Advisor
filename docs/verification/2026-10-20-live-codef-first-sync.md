# 실제 KB 연결·조회 및 거래 보류 확인

- 사용자 앱 조작 후 DEMO KB0301 연결 ACTIVE/version8, 최근 CREATE SUCCEEDED, last_error/provider_code null.
- Connected ID 암호화 컬럼 존재 및 nonce12byte/ciphertext>=16byte/ownership hash32byte/key id 조건 만족. 실제 승인 조회 성공은 해당 CID 복호화 및 공급자 요청을 통과했음을 확인한다. CID/암호문/해시/인증정보 원문은 조회하지 않았다.
- 실제 동기화 2026-10-20 10:25:36~10:25:40 KST: PARTIAL, received33/applied0/held33, 오류 없음. PROVIDER_REPORTED33/SYNTHETIC0. 금융 연결 및 공급자 조회 성공과 가계부 적용 완료를 구분한다.
- 모든 거래 HELD/MISSING_IDENTITY. 누락 집계만 확인: card_mask33/date0/time0/currency0/installments0/amount1. resCardNo 부재 또는 현재 마스킹 규칙과 형식 불일치의 어느 쪽인지는 아직 구분되지 않았다. 실제 카드번호·거래 금액·상호 등은 읽거나 출력하지 않았다.
- 금융 거래와 CODEF 가계부 연결0, 사용자 불일치 연결0. 보류 거래를 강제 반영하지 않음. 이전 CF-12803 로그인 거절은 현재 ACTIVE 연결에 대해 해소된 상태.
- 휴대폰 USB reverse tcp18080 유지 확인. 사용자 앱 조작으로 실제 연결 생성·조회·동기화·DB 저장이 발생했으나, 최신 조회 버튼 자체의 HTTP200 추적은 메모리 진단을 추출하지 않아 별도 증거 없음.
- 근거 .run/codef-live-state.json 및 민감값 없는 집계 쿼리. 다음 작업은 공식 응답 규격과 실제 resCardNo 필드의 존재/마스킹 형식을 원문 없이 대조하여 보류 원인을 구분하는 것이다.