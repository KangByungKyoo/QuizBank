# 문제 은행 · QuizBank

사용자가 직접 등록한 문제로 공부하는 한국어 오프라인 Android 앱입니다. 현재 문제 등록과 새 시험 출제는 단답형만 활성화되어 있으며, 객관식·O/X형 선택은 비활성화되어 있습니다. 등록된 문제 내용을 그대로 출제하고 등록된 정답으로 채점합니다. 기존 다른 유형의 문제와 학습 기록, CSV/JSON 호환성은 보존합니다. 기존 다른 유형의 문제는 수정 화면에서 단답형을 선택하고 저장해 전환할 수 있습니다.

## 시작하기

프로젝트 폴더: `E:\CODE\QuizBank`

Android Studio에서 이 폴더를 열고 Gradle Sync를 실행합니다. JDK 17 이상과 Android SDK 36이 필요합니다. `local.properties`에는 자신의 Android SDK 경로를 설정합니다.

```powershell
cd E:\CODE\QuizBank
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedUiTestAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

전달용 APK: `output/QuizBank-0.1.0-debug.apk`

기기 테스트에는 연결된 Android 기기 또는 에뮬레이터가 필요하며 별도 앱 ID `com.minipapa.quizbank.uitest`를 사용합니다. 단위 테스트는 `testDebugUnitTest`, 기기 테스트는 `connectedUiTestAndroidTest`로 실행합니다.

## 사용 순서

1. **카테고리 관리**에서 상위 카테고리와 하위 카테고리를 추가합니다.
2. **문제 등록**에서 단답형 문제 내용, 정답, 선택 해설을 입력합니다.
3. **랜덤 시험 시작**에서 카테고리·문제 수·출제 방식을 선택합니다. 단답형이 기본 선택됩니다.
4. 문제마다 답안을 제출해 정답과 해설을 확인하고 다음 문제로 진행합니다.
5. 마지막 문제의 **시험 완료 및 결과 저장**을 누르면 시험과 오답 기록이 저장됩니다.

빈 문제은행으로 시작합니다. 홈의 **샘플 문제 5개 추가**를 선택하면 ‘샘플 문제’ 카테고리에 샘플 표시가 붙은 문제를 추가합니다. 샘플은 자동으로 사용자 데이터에 섞어 넣지 않습니다.

## 구현 기능

- 카테고리 생성·수정·부모 변경·삭제, 깊이 제한 없는 계층 구조, 순환 및 같은 위치의 이름 중복 방지.
- 상위 카테고리를 선택할 때 모든 하위 카테고리의 문제 포함.
- 삭제 전 하위 카테고리·문제 개수 안내와 확인. 삭제 후에도 이전 시험의 내용 및 통계 보존.
- 객관식 보기 2~5개 및 정답 보기 선택, O/X형, 단답형 문제 CRUD.
- 카테고리 및 키워드 검색, 정답·해설·등록/수정 일시 확인.
- 복수 카테고리와 복수 문제 유형, 문제 수 5/10/20/전체, 전체 랜덤 또는 오답 우선 출제.
- 문제 중복 출제 방지, 실제 출제 가능 개수 안내, 등록된 보기의 순서와 원문 보존.
- 단답형의 앞뒤 공백 및 영문 대소문자 무시, 답안 제출 후 수동 정답/오답 변경.
- 시험 결과, 카테고리별·문제별 통계, 완료한 시험의 상세 풀이 이력.
- 오답 횟수 누적, 카테고리별 오답 검색, 오답만 재시험.
- 진행 중 시험과 제출 답안을 Room에 저장해 앱 재실행 후 이어하기. 아직 제출하지 않은 입력은 화면 상태 복원에 의존합니다.
- UTF-8 CSV 검증·미리보기·확인 후 가져오기, 잘못된 행 표시, 계층 카테고리 자동 생성.
- 버전이 포함된 UTF-8 JSON 백업과 복원. Android 파일 선택기를 통한 저장과 불러오기.
- 중복 건너뛰기·같은 ID 덮어쓰기·전체 교체 선택 및 교체 전 추가 확인. 복원 실패 시 트랜잭션으로 기존 데이터 보존.

‘오답 문제 중심’은 틀린 문제를 먼저 무작위로 출제하고 남는 자리는 다른 문제로 채웁니다. ‘오답 문제만 다시 풀기’는 한 번이라도 틀린 문제만 출제합니다. 이후 정답을 맞혀도 기존 오답 횟수는 유지됩니다.

## CSV 형식

단답형의 `question`이 `PoC란 무엇인가?`처럼 `용어란/이란/은/는 무엇인가(요)?` 형태이면 기존 CSV를 그대로 사용해 설명을 보고 용어를 답하는 문제로 가져옵니다. `answer`의 설명이 출제 내용이 되고, 질문에서 추출한 용어 `PoC`가 정답이 됩니다. `explanation`은 줄바꿈을 포함해 유지하며 답안 제출 후 표시합니다. 변환된 내용과 정답은 CSV 미리보기에서 확인할 수 있습니다. 다른 형태의 질문은 원문 그대로 가져옵니다.

이미 등록된 같은 형태의 단답형도 새 시험에서 위 방식으로 출제됩니다. 저장된 원본과 기존 시험 기록, 진행 중 시험은 유지합니다.

[examples/questions.csv](examples/questions.csv)에 세 가지 유형의 예제 5개가 있습니다.

```csv
categoryPath,type,question,option1,option2,option3,option4,option5,answer,explanation
IT/Python,MULTIPLE,리스트 길이 함수는?,len(),size(),,,,1,길이를 구합니다.
일반 상식,OX,1km는 1000m이다.,,,,,,O,
IT/웹,SHORT,HTML의 영문 약자는?,,,,,,HTML,
```

- 헤더는 위 순서와 이름을 그대로 사용합니다.
- `type`: `MULTIPLE`, `OX`, `SHORT`. 한국어 `객관식`, `O/X형`, `단답형`도 허용합니다.
- 객관식 `answer`는 보기 번호 **1~5**입니다. 숫자가 아닌 유일한 보기 내용도 사용할 수 있습니다.
- O/X형 정답은 `O` 또는 `X`, 단답형은 정답 문자열입니다.
- 내용에 쉼표나 줄바꿈이 있으면 큰따옴표로 감쌉니다. 필드 안의 큰따옴표는 `""`로 씁니다.
- UTF-8 및 UTF-8 BOM, LF/CRLF, 따옴표 안의 줄바꿈을 지원합니다.
- 오류 행은 등록하지 않으며, 유효한 문제만 확인 후 등록합니다. CSV 반복 가져오기는 새 문제를 추가합니다.

## 백업과 데이터 정책

JSON 형식 `QuizBank`, 데이터 버전 `1`에 카테고리·문제·보기·완료한 시험·풀이 기록이 들어갑니다. 진행 중 시험은 백업 대상에 포함하지 않습니다.

병합은 같은 경로의 카테고리를 합치고, 문제 중복은 ID로 판단합니다. 내용이 같아도 ID가 다르면 별도 문제입니다. 동일한 시험 ID의 기록은 기존 기록을 유지합니다. 전체 교체는 현재 문제와 학습 기록 및 진행 중 시험을 백업 파일의 내용으로 교체합니다.

문제/카테고리를 삭제하면 현재 문제은행과 오답 목록에서 제외합니다. 완료한 시험에는 당시 문제·정답·해설·카테고리 경로의 사본이 남습니다. 삭제된 카테고리의 기록도 전체 정답률에는 포함됩니다. 정답률은 완료한 시험의 최종 판정 기준이며, 단답형 수동 판정이 반영됩니다.

## 기술 구성

| 항목 | 설정 |
| --- | --- |
| 앱 이름 / 프로젝트 | 문제 은행 / QuizBank |
| Application ID | `com.minipapa.quizbank` |
| 최소 / 대상 SDK | API 26 / API 36 |
| UI / 구조 | Jetpack Compose + Material 3 / MVVM |
| 빌드 | AGP 8.10.1, Gradle 8.11.1, Kotlin/Compose Compiler 2.0.21, KAPT |
| 라이브러리 | Compose BOM 2025.09.01, Room 2.7.2, Activity 1.11.0, Lifecycle 2.9.4, Core 1.17.0 |
| 데이터 저장 | Room SQLite, 외부 파일은 Android Storage Access Framework |

현재 Android Studio의 지원 상한인 AGP 8.10.1에 맞춘 버전 조합입니다. [AGP 8.10 공식 호환성 안내](https://developer.android.com/build/releases/agp-8-10-0-release-notes)에 따라 Gradle 8.11.1, SDK 36, JDK 17 이상을 사용합니다.

```text
app/src/main/java/com/minipapa/quizbank/
  MainActivity.kt             앱 진입점과 ViewModel 생성
  QuizBankApplication.kt      Room 및 Repository 생성
  domain/
    Models.kt                 계층 탐색, 문제 검증, 채점, 출제
    CsvImport.kt              CSV 파싱 및 행별 검증
    JsonCodec.kt              백업 검증, 병합 계획, 시험 직렬화
  data/
    Database.kt               Room Entity 6개와 DAO
    BankRepository.kt         트랜잭션 및 영구 저장
  navigation/
    BankViewModel.kt           화면 상태와 비동기 작업
  ui/                         홈, 카테고리, 문제, 시험, 통계, 데이터 관리
app/schemas/                  Room 스키마
app/src/test/                 도메인 단위 테스트
app/src/androidTest/          Room 및 실제 화면 통합 테스트
examples/questions.csv        일괄 등록 예제
requirements.md               원본 요구사항
```

Category, Question, QuestionOption, ExamSession, ExamAnswer 엔터티에 진행 중 시험 저장용 ExamDraft를 추가했습니다. 외래 키, 트랜잭션, 스냅샷으로 데이터 무결성을 관리합니다. 카테고리 삭제는 잎부터 처리해 SQLite의 재귀 트리거 제한을 피합니다.

## 현재 제한

- CSV/JSON 가져오기 파일은 10MB까지 지원합니다.
- 파일 선택기의 모든 제조사·클라우드 제공자를 검증하지는 않았습니다. 앱은 인터넷 권한과 외부 서버, AI API를 사용하지 않습니다.
- 매우 큰 문제은행에서는 검색과 통계를 메모리에 올려 계산하므로 별도의 SQL 기반 최적화가 필요할 수 있습니다.
- 개발용 서명 APK이며, 스토어 배포용 서명과 사용자 지정 런처 아이콘 패키징은 후속 작업입니다.
- 실제 TalkBack, 큰 시스템 글꼴, API 26 기기의 추가 검증은 남아 있습니다.

빌드·테스트 결과와 개발 단계별 내역은 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)에 기록합니다.
