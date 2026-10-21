# 이메일 확인·복구 설정

Spring Boot3.5.16 메일 어댑터를 사용한다. [공식 문서](https://docs.spring.io/spring-boot/3.5/reference/io/email.html). 현재 발송 서비스는 미정이며 실제 발송하지 않는다.

1. 발송 서비스를 정한 뒤 config/mail.example.properties를 .local/mail.properties로 복사한다. 기존 파일은 덮어쓰지 않는다.
2. host/port(587 STARTTLS 또는465 TLS)/username/password/from을 로컬 파일에만 입력하고 파일 ACL을 현재 사용자와SYSTEM으로 제한한다. 채팅/로그/저장소에 비밀값이나 확인 코드를 보내지 않는다.
3. 준비되면 enabled=true, 환경변수 ACCOUNT_MAIL_CONFIG_FILE에 절대 경로 설정 후 정상 API를 재시작한다. SMTP 서버 인증서/호스트 검증과 타임아웃을 해제하지 않는다.
4. 계정 설정의 이메일 확인 또는 로그인 화면의 비밀번호 찾기를 사용한다. 확인 코드는 받은 이메일에서 앱에 직접 입력한다. 코드15분/1회, 재요청 시 이전코드 폐기. 복구 완료 시 모든 access/refresh 세션을 폐기하며 거래/카드는 보존한다.

DB에는 코드SHA256만 저장한다. 확인과 복구의 용도/소유자를 분리한다. 존재하지 않는 이메일과 메일 전송 실패를 구분해 공개하지 않는다. enabled=false일 때 기존 로그인은 유지된다. 실제 발송·수신 성공은 아직 검증하지 않았다.
