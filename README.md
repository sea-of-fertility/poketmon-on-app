# PokePet

Android 화면 위에 포켓몬 도트 펫을 오버레이로 띄우는 앱.
포켓몬을 선택하면 화면 위에서 돌아다니고, 탭하면 반응하고, 잠들기도 합니다.

## 주요 기능

- **오버레이 펫** - 다른 앱 위에 포켓몬 도트 스프라이트가 떠다님
- **포켓몬 선택** - 1~5세대 633종 포켓몬 중 선택 (세대/속성 필터, 검색 지원)
- **상태 행동** - 대기, 걷기, 뛰기, 잠자기, 반응(탭) 등 자연스러운 상태 전환
- **터치 인터랙션** - 탭(반응), 드래그(이동), 롱프레스(메뉴)
- **설정 커스터마이징** - 크기, 투명도, 이동 속도, 활동 빈도, 수면 타이머
- **게임 모드** - 게임 실행 시 자동으로 펫 숨김

## 스크린샷

| 포켓몬 선택 | 설정 | 오버레이 |
|:---:|:---:|:---:|
| 포켓몬 그리드에서 선택 | 크기/속도/투명도 조절 | 화면 위에서 돌아다니는 펫 |

## 요구 사항

- Android 8.0 (API 26) 이상
- 오버레이 권한 (다른 앱 위에 표시)

## 빌드

```bash
# APK 빌드
./gradlew assembleDebug

# 디바이스에 설치
./gradlew installDebug
```

빌드된 APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

## 기술 스택

- **Language**: Kotlin
- **UI**: XML 레이아웃 (View-based, Compose 미사용)
- **Min SDK**: 26 / **Target SDK**: 36
- **Dependencies**: AndroidX Core-ktx, AppCompat, Material Design Components, ViewPager2

## 프로젝트 구조

```
com.example.poketmon_on_app/
├── ui/                          # Activity, Fragment, Adapter, Custom View
│   ├── MainActivity.kt          # 메인 화면 (프리뷰, 탭, 서비스 제어)
│   ├── PokemonListFragment.kt   # 포켓몬 그리드 (필터, 검색)
│   ├── SettingsFragment.kt      # 설정 (크기, 속도, 투명도 등)
│   ├── PokemonAdapter.kt        # RecyclerView 어댑터
│   └── PetView.kt               # 스프라이트 애니메이션 렌더링 Custom View
├── service/                     # Foreground Service, 오버레이 관련
│   ├── PetOverlayService.kt     # 오버레이 서비스 (생명주기, 이동, 설정)
│   ├── PetTouchHandler.kt       # 터치 이벤트 처리 (탭, 드래그, 롱프레스)
│   ├── OverlayMenuManager.kt    # 롱프레스 팝업 메뉴
│   ├── PetNotificationManager.kt # 포그라운드 서비스 알림
│   └── GameDetector.kt          # 게임 앱 감지 → 펫 숨김
└── pet/                         # 데이터, 로직
    ├── PokemonData.kt           # 포켓몬 데이터 모델
    ├── PokemonRepository.kt     # 포켓몬 목록/초상화 로드
    ├── SpriteSheet.kt           # AnimData.xml 파싱 + 스프라이트 프레임 추출
    ├── PetStateMachine.kt       # 펫 상태 머신 (Idle, Walk, Sleep 등)
    ├── PetPreferences.kt        # SharedPreferences 래퍼
    └── Direction.kt             # 8방향 enum
```

## 스프라이트 에셋

PMD(Pokemon Mystery Dungeon) 스타일 도트 스프라이트를 사용합니다.

```
assets/Sprites/{4자리 포켓몬 ID}/
  ├── AnimData.xml          # 애니메이션 메타데이터
  └── {AnimName}-Anim.png   # 스프라이트 시트 (행=8방향, 열=프레임)
```

사용하는 애니메이션: Idle, Walk, Sleep, Hop, Hurt, Eat

## 권한

| 권한 | 용도 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 오버레이 표시 |
| `FOREGROUND_SERVICE` | 백그라운드 서비스 유지 |
| `POST_NOTIFICATIONS` | 포그라운드 서비스 알림 |
| `PACKAGE_USAGE_STATS` | 게임 모드 (선택 사항) |

## 라이선스

스프라이트 에셋은 [PMD Sprite Repository](https://sprites.pmdcollab.org/) 출처입니다.
