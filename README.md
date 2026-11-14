# 댕큐 (Daengq)

Pleos Connect 차량용 안드로이드 앱이에요. 주행 중 강아지 소리(울음, 낑낑, 하울링)를 기록하고, TMAP 지도와 주행 결과로 보여 줘요.

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
