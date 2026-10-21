# 카드 이용내역 화면 이해도 개선

문제: 기본 화면이 보류 거래만 보여주고 카드/가맹점명이 없으며 내부 처리 용어로 설명해 실제 지출 내역을 구분하기 어려웠다.

변경: 공식 승인 응답 resMemberStoreName/resCardName 두 필드만 길이120/제어문자·장문숫자 차단 후 merchant_name/card_label에 저장(V18). 값은 로그/AI에 전달하지 않는다. 원장 식별·금액 계산·혜택 상품 매핑에는 표시 이름을 쓰지 않는다. 정보 재조회는 동일 거래의 표시만 갱신하고 추천 버전이나 원장 수를 변경하지 않는다.

Android는 전체 내역이 기본, 최신 날짜 우선. 가맹점/날짜/카드명/카드 끝자리/결제 금액과 가계부 기록 상태를 표시한다. 수신·반영·보류·원장 대신 조회한 내역·가계부에 기록·확인 필요로 표현한다. 취소1건은 어떤 결제를 취소했는지 선택하도록 안내. 미확인 취소 금액은 숫자를 만들지 않고 확인 필요로 표시한다.

검증: 정규화9+HTTP/PostgreSQL17 총26개 통과. 표시 필드 선별/원문번호 차단/타사용자 조회 거부/표시 추가 시 원장 중복없음 및 계산 버전 유지 포함. Android debug/test 빌드 성공. 운영자 실제 재조회1회로 merchant_name33/card_label33 채움, 적용32/확인1 유지. 실제 표시 이름 원문은 진단 출력하지 않았다. 실제 앱에서 가독성 체감 확인은 설치 후 사용자 확인이 필요하다.

근거 .run/finance-readable-tests.log, finance-readable-live.log, finance-readable-android.log. 공식 필드 https://developer.codef.io/products/card/common/p/approval.
- 에뮬레이터 전체 금융 흐름1개 통과(.run/finance-readable-ui.log). 첫 검사는 기본 필터가 전체로 바뀌었는데 테스트가 과거처럼 토글해 실패했고, 전체기본을 명시 검사하도록 수정 후 통과. 최종 문자열 수정 debug빌드 및 SM-F741N 설치 성공. 실사용자 표시 확인은 로그인 후 필요.
