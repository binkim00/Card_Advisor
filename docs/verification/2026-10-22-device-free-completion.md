# 2026-10-22 기기 없는 구현·검증

## 검증 범위와 결과
- Spring/실제 PostgreSQL 17.6: 전체275 검사, 실패0/오류0/skip0. `.run/verify-backend.log`.
- Android JVM15 검사 통과, lint 오류0(라이브러리 업데이트·스타일 등 경고 잔존), debug/androidTest/release 빌드 성공. `.run/verify-android.log`.
- 에뮬레이터→실제 HTTP→격리 PG 5개 서버 통합 검사(내부 Android 7흐름): 주문서 후보 추천/지연무효화/수동구매, 분할 예산/고정비 배분, 오프라인, 원장·금융 실적/혜택 회귀, UNKNOWN 대조, 가맹점 분류 기억/해제. 실패0/skip0. `.run/verify-emulator.log`와 각 JSON 보고서.
- 합성 SMTP TLS 전송/인증서 검증, prod HTTPS 서버/미인증 차단, OpenAI 구조화 응답/실패 대체 계약 통과. 실제 외부 발송·유료 AI·새 금융기관 호출0.
- 분류 합성120건 개발80/평가40 분리: macro-F1 0.8533, 정확도0.825, 제안정밀도1.0, 커버리지0.725. 로컬 키워드 분류 성능이며 실모델 성능이 아니다. 자동 채택하지 않는다.
- API 문서의 중복 /v1 경로·누락 경로/본문 수정, 실제 등록 라우트/경로 인자와 대조. 세부 입력 필드 계약 추가검사 결과는 `.run/api-documentation-final.log`.

## 정상 실행환경과 보존
Docker Desktop의 runtime 디렉터리만 같은 부모로 보존 이동해 원 엔진과 원 DB를 복구했다. 볼륨·컨테이너 데이터 삭제/초기화 없음. 정상 API18080 health UP, 원 DB54329 Flyway V27 적용. 계정·access/refresh세션·카드·예산·원장·취소·금융 연결·거래·상품매핑10표 전체행 해시 대조 일치. 원장187/금융190/상품매핑1 유지. `.run/service-final-runtime.json`.

현재 원본 DB의 암호화 DPAPI 백업과 격리 복원8표 건수 대조 통과. 별도 합성 DB의 과거백업→계정삭제→삭제등록부 적용 복원도 통과해 삭제 계정이 되살아나지 않음을 확인했다. `.run/backup-restore.json`, `.run/backup-deletion-test.json`. 개인 백업 삭제 없음. 전체 재해 복구·다른 Windows 사용자/호스트 복구는 별도 결정이 필요하다.

## 수정한 실패
전체 합성 가입 검사가 같은 제한 버킷을 공유하던 테스트를 분리했다. 서비스의 실제 요청 제한은 유지한다. lint의 LocalContext→Activity 강제 형변환과 개발 domain 설정을 수정했다. 테스트 가맹점 승인번호의 잘못된 밑줄을 공식 계약의 영숫자 형식으로 고쳤다. SMTP MIME 본문 검사는 실제 디코딩 본문으로 정정했다. Windows에서 실행 JAR의 재패키징 잠금은 해당 API 프로세스 경로를 검증한 뒤 정지/패키징/재기동해 해결했다.

## 결과를 확대하지 않는 경계
physicalDevice=false. 합성 Android 주문서 후보는 실제 네이버/무신사 주문서 인식 성공 증거가 아니다. 과거 KB DEMO 조회의 성공 이력은 이번 UNKNOWN 대조·하나 기관 성공을 의미하지 않는다. 미확인 취소1건은 보류했다. 공개 배포·운영 인증서·서명키·실제 SMTP/OpenAI·원격 CI·스토어 승인은 미검증이다.

공식 근거: [하나 트래블GO 은행계좌형 약관](https://m.hanacard.co.kr/leaflet/15/15252_20250115.pdf), [KB Easy all](https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?cooperationcode=09256&mainCC=a), [CODEF 공통 SDK](https://github.com/codef-io/easycodef-java), [OpenAI 구조화 응답](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [Android 백업 제외](https://developer.android.com/identity/data/autobackup).
