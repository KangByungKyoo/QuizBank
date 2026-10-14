# QuizBank AI 답안 검토

Firebase 프로젝트 `englishtalk-ceccf`에 등록한 QuizBank Android 앱을 사용합니다.
지역은 `asia-northeast3`, 함수는 `gradeQuizAnswer`, Firebase Functions codebase는 `quizbank`입니다.
EnglishTalk의 `default` codebase와 `createRealtimeSession` 함수는 변경하지 않습니다.

2026-10-14(KST): QuizBank 함수 배포 완료 후 사용자가 실제 AI 검토 성공을 확인했습니다.
10줄 표시 제한을 추가하고 앱 단위 테스트 23개, 서버 테스트 8개,
연결된 Android 기기 테스트 10개를 통과했습니다. 로컬/기기 테스트는 가짜 검토 서비스를 사용합니다.

## 동작

시험 설정의 **AI로 의미와 부족한 용어 검토**를 켜면 정확히 일치하지 않는 단답형 답안을 서버에서 검토합니다.
정확히 일치하는 답은 기존 로컬 채점을 사용하므로 API를 호출하지 않습니다.
기본 설정은 꺼짐입니다. 제출 후 **AI로 답안 검토** 버튼으로 개별 답안만 검토할 수도 있습니다.
검토 사용 시 질문, 등록 정답, 해설, 사용자 입력 답안을 Firebase 서버와 OpenAI에 전송합니다.
전체 문제은행이나 CSV는 전송하지 않습니다. OpenAI Responses 요청은 `store: false`입니다.

판정은 정답 / 일부 맞음 / 오답입니다. ‘Docker 이미지’에 대한 ‘도커 이미지’는 같은 개념으로 인정하도록,
‘Docker’ 또는 ‘도커’는 일부 맞음으로 평가하고 빠진 ‘이미지’의 뜻과 필요성을 설명하도록 지시합니다.
AI 판단이 틀릴 수 있으므로 기존 수동 정답/오답 변경을 유지합니다.
일부 맞음은 통계에서 오답이며, 부분 점수는 부여하지 않습니다.

실패 시 제출한 답은 기기에 보존됩니다. 다음 문제로 진행하기 전에 재시도, 수동 판정 또는
**기본 채점으로 진행**을 선택해야 합니다. 검토 완료를 가장한 오답으로 자동 처리하지 않습니다.
진행 중 시험 JSON에는 판정과 피드백을 저장합니다. 완료 후에는 기존 해설에 별도 AI 검토 항목을 덧붙여
시험 상세 이력 및 JSON 백업에 보존합니다. 원본 문제의 해설은 변경하지 않습니다.

## 설정

1. Firebase 설정 파일은 `app/google-services.json`에 둡니다. Git에서 제외됩니다.
2. QuizBank의 Firebase Android 앱 ID는 `1:582164154238:android:077b5493f7329c08df552f`입니다.
3. 서버의 `functions/.env.englishtalk-ceccf`는 `.env.example`을 복사해 만듭니다.
4. 장기 키는 기존 Secret Manager의 `OPENAI_API_KEY`를 바인딩합니다. 키 값을 앱이나 코드에 넣지 않습니다.
5. Authentication의 익명 로그인이 켜져 있어야 합니다.
6. Debug APK 실행 후 Android Studio Logcat의 `DebugAppCheckProvider`에서 토큰을 확인합니다.
   Firebase 콘솔 → App Check → QuizBank → 디버그 토큰 관리에 등록합니다.
   이 토큰은 비밀이며 채팅·문서·Git에 남기지 않습니다.
7. Release는 Play Integrity를 사용합니다. 출시 전 QuizBank의 서명 SHA-256 및 해당 공급자 설정이 필요합니다.
8. 앱 인증 검증과 App Check 재사용 방지는 항상 활성화합니다.
9. 런타임 서비스 계정에는 Firebase App Check Token Verifier 역할과 기존 Secret 접근 권한이 필요합니다.

## 검증과 배포

```powershell
npm.cmd --prefix functions ci
npm.cmd --prefix functions test
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileUiTestAndroidTestKotlin
firebase.cmd deploy --only functions:quizbank --project englishtalk-ceccf
```

마지막 명령은 실제 서버 배포입니다. 로컬 테스트는 가짜 API 응답으로 인증, 스키마, 부분 정답,
호출 실패, 호출 한도를 검증합니다. 실제 모델 채점 정확도와 App Check 성공은 배포·기기 실행 후 확인해야 합니다.
연결 테스트는 ‘도커 이미지’, ‘도커’, ‘Docker 컨테이너’를 각각 제출해 판단과 보완 설명을 확인합니다.

기본 모델은 서버 설정 `QUIZBANK_GRADING_MODEL=gpt-4.1-mini`이며 서버에서만 변경합니다.
AI 검토는 판정·보완할 용어·피드백·이유를 합쳐 화면에서 최대 10줄로 표시합니다.
서버는 피드백 80자와 이유 140자 이내의 간결한 문장을 요청하며 호출당 출력은 최대 350토큰, API 시간 제한은 20초입니다.
화면 폭이나 글꼴 크기로 10줄을 넘으면 말줄임표로 표시합니다. 저장된 검토 원문은 유지합니다.
서버는 UID별 분당 10회 / UTC 일별 100회 / 3초 간격, 전체 분당 30회 / UTC 일별 500회로 제한합니다.
실패 요청도 한도에 포함합니다. 익명 UID 한도만으로 개인별 절대 상한을 보장하지 않으며 전체 한도가 함께 적용됩니다.
호출 제한은 `_quizbankGradeLimits`에 저장합니다. 해당 컬렉션은 기존의 클라이언트 접근 거부 규칙을 사용하고,
클라이언트에서 문제·답안·키를 Firestore에 저장하지 않습니다.
Firebase·OpenAI 사용료는 기존 프로젝트에 합산됩니다. 배포는 `functions:quizbank`만 대상으로 합니다.

공식 문서:
- https://developers.openai.com/api/docs/guides/structured-outputs
- https://firebase.google.com/docs/functions/callable
- https://firebase.google.com/docs/app-check/android/debug-provider
