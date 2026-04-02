# CLAUDE.md — PokePet (poketmon-on-app)

## Project Overview

Android 화면 위에 포켓몬 도트 펫을 오버레이로 띄우는 앱.
사용자가 포켓몬을 선택하면 화면 위에서 돌아다니고, 탭하면 반응하고, 잠들기도 한다.

## Tech Stack

- **Language**: Kotlin (no Compose — View-based UI)
- **Min SDK**: 26 / **Target SDK**: 36
- **Build**: Gradle Kotlin DSL (`build.gradle.kts`)
- **Dependencies**: AndroidX Core-ktx, AppCompat, Material Design Components
- **No DI framework** — 직접 생성자 주입
- **No Room/DB** — SharedPreferences 사용

## Package Structure

```
com.example.poketmon_on_app/
├── ui/              # Activity, Fragment, Custom View
│   ├── MainActivity.kt
│   └── PetView.kt      # 스프라이트 애니메이션 렌더링 Custom View
├── service/
│   └── PetOverlayService.kt  # Foreground Service + TYPE_APPLICATION_OVERLAY
└── pet/
    └── SpriteSheet.kt  # AnimData.xml 파싱 + 스프라이트 시트 프레임 추출
```

## Build & Run

```bash
./gradlew assembleDebug                    # APK 빌드
./gradlew installDebug                     # 디바이스에 설치
adb shell am start -n com.example.poketmon_on_app/.ui.MainActivity  # 실행
```

## Sprite Asset Structure

스프라이트 에셋은 PMD(Pokemon Mystery Dungeon) 스타일 도트 스프라이트이다.

### 경로 규칙
```
assets/Sprites/{4자리 포켓몬 ID}/
  ├── AnimData.xml          # 애니메이션 메타데이터
  ├── {AnimName}-Anim.png   # 스프라이트 시트
  └── {AnimName}-Shadow.png # 그림자 시트 (미사용)
```

- 포켓몬 ID: 4자리 zero-padded (`0001` = Bulbasaur, `0025` = Pikachu)
- 현재 에셋: 0001~0649 (633개 폴더)
- 앱에서 사용하는 애니메이션: **Idle, Walk, Sleep, Hop, Hurt, Eat**
- 모든 포켓몬에 모든 애니메이션이 있는 것은 아님 — 에셋 현황과 fallback 규칙은 `plan.md` 참조

### AnimData.xml 구조
```xml
<AnimData>
  <Anims>
    <Anim>
      <Name>Walk</Name>
      <FrameWidth>40</FrameWidth>
      <FrameHeight>40</FrameHeight>
      <Durations><Duration>4</Duration></Durations>
    </Anim>
  </Anims>
</AnimData>
```

### 스프라이트 시트 레이아웃
- **행(Row)** = 8방향: 0=Down, 1=DownRight, 2=Right, 3=UpRight, 4=Up, 5=UpLeft, 6=Left, 7=DownLeft
- **열(Col)** = 프레임 인덱스 (Duration 개수와 동일)
- 프레임 추출: `Bitmap.createBitmap(sheet, col * frameW, row * frameH, frameW, frameH)`

### 렌더링 규칙
- **Pixel art** → nearest-neighbor 스케일링 (anti-aliasing OFF, filterBitmap OFF)
- Duration 변환: `delayMs = durationTicks * 1000 / 60`

## Key Permissions

- `SYSTEM_ALERT_WINDOW` — 오버레이 표시 (런타임 권한, Settings.canDrawOverlays)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — 백그라운드 서비스
- `POST_NOTIFICATIONS` — 포그라운드 서비스 알림

## UI Reference

`mock_ui.html`이 목표 UI/UX의 기준 레퍼런스이다. UI 관련 작업 시 반드시 이 파일을 읽고 참고할 것.
- MainActivity 화면 구조 (프리뷰, 탭, 포켓몬 그리드, 설정)
- 오버레이 동작 (상태 표시, 롱프레스 메뉴, 드래그)
- 컴포넌트 스타일 (Segmented Control, 필터 칩, 슬라이더 등)

## Development Plan

`plan.md` 참조. 10단계로 구성되며 각 단계마다 디바이스 테스트 가능.

## macOS 버전 참조

macOS 버전: `/Users/hyungjunpark/dev/poketmon/` — 로직 참고용 (pokemon_data.json, 상태 머신, 설정값 수치 등)

## Coding Conventions

- 한국어 UI 문자열 사용 (버튼, 상태 텍스트 등)
- XML 레이아웃 기반 (Compose 사용 안 함)
- Material Design Components 활용 (MaterialButton 등)
- 클래스/변수명은 영어, 주석은 최소화
