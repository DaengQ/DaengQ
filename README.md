<div align="center">

<img src="docs/images/daengq-3d.png" alt="댕큐 3D 로고" width="160" />

# 댕큐 (DaengQ)

**댕댕이와 즐겁게 댕겨오자**

</div>

## 목차

1. [서비스 소개](#1-서비스-소개)
2. [팀원 소개](#2-팀원-소개)
3. [기술 스택](#3-기술-스택)
4. [빌드 준비](#빌드-준비)
5. [빌드와 테스트](#빌드와-테스트)

## 1. 서비스 소개

댕큐는 주행 중 놓치기 쉬운 반려견의 불편 신호를 자동으로 감지하고 기록해, 보호자가 반려견의 이동 특성을 이해하고 더 나은 여정을 준비하도록 돕는 서비스입니다.

Pleos Connect 차량용 안드로이드 앱이에요. 운전 중에는 화면을 오래 볼 수 없으니, 감지와 기록은 자동으로 하고 확인과 수정은 음성으로 할 수 있게 만들었어요.

### 주요 기능

| 단계 | 기능 |
| --- | --- |
| 출발 전 | 함께 탈 강아지를 고르거나 새로 등록해요. |
| 주행 중 | 차량 마이크로 강아지 소리(울음, 낑낑, 하울링)를 감지해 시간과 위치를 함께 기록하고, 음성으로 알려 줘요. |
| 주행 중 | 운전자가 직접 기록하거나 고칠 때는 버튼을 누르거나 "아까 거 울음으로 바꿔줘"처럼 말하면 돼요. |
| 주행 중 | TMAP 지도에 지나온 길과 기록 위치를 실시간으로 보여 줘요. |
| 주행 후 | 결과 화면에서 지나온 길, 종류별 횟수, 시간대별 막대그래프, 가장 많았던 소리와 구간을 한 줄로 요약해 줘요. |

## 2. 팀원 소개

| 이름 | 역할 | 담당 |
| --- | --- | --- |
| 구가경 | 팀장 | 기획 |
| 김나경 | 팀원 | 디자인, 개발 |

## 3. 기술 스택

| 분류 | 사용 기술 |
| --- | --- |
| 플랫폼 | Android Automotive (Pleos Connect), minSdk 24, targetSdk 37 |
| 언어 | Kotlin |
| 빌드 | Gradle (Kotlin DSL), Android Gradle Plugin 9.5 |
| UI | Android View, AndroidX AppCompat, Material Components, 커스텀 View (시간대별 막대그래프, 적응 타이머 게이지) |
| 차량 SDK | Pleos Playground SDK: Vehicle, NaviHelper (현재 위치), TextToSpeech, SpeechToText (차량 내장 엔진, 온디바이스) |
| 지도 | TMAP Android SDK 3.8 |
| 소리 감지 | MediaPipe Audio Classifier, YAMNet (온디바이스) |
| 저장 | JSON 파일, SharedPreferences |
| 테스트 | JUnit 4 |

## 빌드 준비

저장소에 들어 있지 않은 두 가지를 먼저 준비해야 해요.

1. **TMAP SDK.** SK open API에서 TMAP Android SDK를 받아 아래 두 파일을 `app/libs/`에 넣어 주세요.
   - `tmap-sdk-3.8.aar`
   - `vsm-tmap-sdk-v2-eaa-2.0.14.aar`
2. **TMAP 앱 키.** 프로젝트 루트의 `local.properties`에 키를 적어 주세요. 이 파일은 git에 올라가지 않아요.

   ```
   TMAP_APP_KEY=발급받은_키
   ```

Pleos SDK(Vehicle, NaviHelper, TextToSpeech, SpeechToText)는 `settings.gradle.kts`에 적힌 Pleos Playground 저장소에서 받아요.

## 빌드와 테스트

```bash
./gradlew installDebug
```

```bash
./gradlew testDebugUnitTest
```
