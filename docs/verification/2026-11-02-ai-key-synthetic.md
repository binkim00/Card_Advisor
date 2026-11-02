# AI 키 인증·합성 호출 확인 — 2026-11-02

## 목표·결정
사용자 승인으로 로컬 키 인증과 합성 데이터 AI 호출만 검사. 실금융 OFF 유지, 서버 설정·앱 배포·DB 변경 없음. 원본 main 5928109 기준.

## 완료·증거
OpenAI gpt-4.1-mini 모델 접근 GET 200, Responses POST EXPLANATION·SPENDING_ANALYSIS 각 200/completed. 허용 ID만 선택·JSON 형식·중복 없음 검증, 3개 검사 성공/종료0. 응답시간 3.281초/2.250초, 총 입력259·출력24토큰. store=false, 가상 금액만 전송. 키는 .local 제외 파일에서 프로세스가 읽고 원문·헤더·응답 원문을 기록하지 않음. 값 없는 결과 .run/ai-key-probe.json.

## 남은 범위
직접 공급자 호출 검사이며 앱→백엔드 전체 AI 성공 증거는 아님. 현재 추천 설명의 서비스 작업명 EXPLAIN_RECOMMENDATION과 게이트웨이 허용명 EXPLANATION 불일치 및 체험 프로필의 OPENAI_CONFIG_FILE 차단이 남음. 이번 검사에서 코드 수정·서버 AI 활성화·키 업로드·APK 재빌드·원격 push 없음.

## 다음 행동 하나
앱 추천 설명 연결 불일치를 수정하고 로컬 합성 통합 검사로 검증한다. 서버 활성화는 별도 범위로 둔다.

공식 요청 형식 참고: https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses
