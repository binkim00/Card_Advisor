# CODEF 조회 추가 인증 — 2026-10-22

## 구현과 근거
- [CODEF 추가 인증 공통 가이드](https://developer.codef.io/common-guide/add-auth): CF-03002, data.method, 기본 약 3분 제한, 동일 endpoint·기본 입력 유지, N차 인증. 현재 공식 사이트 공개 콘텐츠 API의 같은 문서도 대조했다.
- [CODEF 공식 Java SDK](https://github.com/codef-io/easycodef-java/blob/master/src/main/java/io/codef/api/EasyCodef.java): is2Way=true 및 twoWayInfo(jobIndex/threadIndex/jti/twoWayTimestamp) 재요청 검증. [공식 예제](https://github.com/codef-io/easycodef-java#readme)의 simpleAuth=1 사용자 완료 방식.
- 거래 조회가 실제로 반환한 smsAuthNo/emailAuthNo/simpleAuth만 재개한다. 기관이 그 인증 방식을 제공한다고 미리 가정하지 않는다. 알 수 없는 method·필드 모양, 보안카드·이미지·전자서명 등 별도 프로토콜은 제출하지 않는다. 계정 생성/수정의 추가 인증은 이 조회 세션을 재사용하지 않는다.
- V28 인증 상태, 소유자·연결 버전·조회 기간 고정, run과 용도를 분리한 AES-GCM 암호화, 3분 보수적 로컬 만료, 한 번에 한 인증 시도. 인증번호는 DB·로그에 저장하지 않으며 응답에는 method/version/만료 시각만 보낸다. 완료·실패·만료 조회 시 암호문 제거.
- Android 문자/이메일 번호 또는 간편인증 완료 확인 → 같은 조회 재개. 입력은 비저장 상태, 화면 정지/이탈 시 삭제. 응답 유실은 AUTH_UNKNOWN, 자동 재전송 없음. 외부 인증 원문·연락처·jti는 앱에 보내지 않는다.

## 검증
- CodefClientContractTest + FinancialSyncPostgresTest: 85 통과, 실패/오류/skip 0. 문자→간편인증→성공, 사용자 격리, 버전충돌, 중복 요청, 암호문, 만료, 연결 변경, 응답 유실 검사.
- Android JVM 검사 및 debug/androidTest 빌드 통과.
- AndroidSyncAuthenticationPostgresTest: 에뮬레이터 화면 문자 입력→간편인증 확인→HTTP API→PG54330 가계부 1건, 인증 요청 4회(연결/최초조회/인증2회), 중복 원장0, 종료 암호문null. API 문서 검사 포함 2 통과. `.run/sync-auth-android.json`, `.run/two-way-emulator.log`.
- 실제 금융 호출0, 물리 휴대폰 검사0. 원 개인 DB54329·계정은 이 단계에서 변경하지 않았다. CODEF 샌드박스 자체 추가 인증 성공을 뜻하지 않으며 loopback 합성 공급자 계약 검사다.

## 남은 외부 검증
기관의 실제 추가 인증 발생·지원 방식과 추가 제한시간은 실제 계정 또는 기관별 공식 상세 규격으로 확인해야 한다. 로컬 코드는 인증을 요구하는 응답이 없는데 임의로 문자 발송/인증을 시도하지 않는다.
