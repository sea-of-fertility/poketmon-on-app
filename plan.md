
# PokePet 개발 계획

> 각 단계는 디바이스에서 사용자가 직접 테스트할 수 있는 단위로 나눈다.

---

## 현재 상태 요약

| 영역 | 구현 상태 | 비고 |
|------|----------|------|
| MainActivity UI | 최소 구현 (프리뷰 이미지 + 토글 버튼) | mock_ui의 탭/그리드/설정 미구현 |
| SpriteSheet 파싱 | 완료 | AnimData.xml 파싱, 프레임 추출 동작 |
| PetView 렌더링 | 완료 | 스프라이트 애니메이션 재생 동작 |
| PetOverlayService | 기본 동작 (Idle + 드래그) | 상태 전환, 자동 행동, 롱프레스 메뉴 미구현 |
| 포켓몬 선택 | 미구현 | Pikachu(#025) 하드코딩 |
| 설정 | 미구현 | 크기/투명도/속도 등 |
| 스프라이트 에셋 | 0001~0649 (633개, 16개 누락) | 아래 에셋 현황 참고 |

### 스프라이트 에셋 현황

- **폴더 존재**: 633개 (0001~0649 중 16개 누락)
- **누락 폴더**: 0514, 0516, 0520, 0522, 0523, 0538, 0539, 0558, 0564, 0565, 0580, 0591, 0592, 0616, 0617, 0626
- **AnimData.xml**: 633개 전부 존재
- **애니메이션 시트 현황**:
  - Walk, Sleep, Hop, Hurt: 거의 전부 존재
  - Eat: 472개 폴더에 누락 (161개만 보유)
  - Idle 누락 6개: 0015, 0148, 0266, 0268, 0345, 0414
  - 0593: Hop, Hurt, Eat 누락
  - 0618: Idle만 존재 (Walk, Sleep, Hop, Hurt, Eat 모두 누락)
- **CopyOf 태그**: AnimData.xml에 826개 CopyOf 존재. 앱에서 사용하는 6종 애니메이션 중 CopyOf를 쓰는 건 위 Idle 누락 6개뿐 (Idle CopyOf=Walk). **CopyOf 파싱은 구현하지 않고, 아래 fallback 규칙으로 대체.**
- **방향(Row) 수**: Walk/Idle/Hop/Hurt는 8행(8방향), **Sleep/Eat은 1행(방향 없음)**. 1행 시트 접근 시 directionRow와 무관하게 row=0 사용.
- **규칙**:
  - 각 포켓몬마다 실제 존재하는 `-Anim.png` 파일만 사용 가능한 애니메이션으로 노출. 없는 애니메이션은 UI/로직에서 제외.
  - **애니메이션 fallback 체인** (SpriteSheet 레벨에서 처리):
    - Idle 없음 → Walk 사용
    - Walk 없음 → Idle 사용
    - Sleep 없음 → Idle (fallback 적용 후의 Idle) 사용
    - Hop/Hurt/Eat 없음 → 탭 반응 후보에서 제외 (fallback 없음)
  - **탭 반응**: 실제 `-Anim.png`가 존재하는 애니메이션(Hop, Hurt, Eat)에서만 랜덤 선택. 셋 다 없으면 반응 없이 무시.
  - **Run 상태**: Run-Anim.png는 존재하지 않음 (633개 중 0개). **Walk 애니메이션을 재생 속도 1.5배 + 이동 속도 2배로 사용.**

---

## Step 1: MainActivity UI 껍데기

**목표**: mock_ui.html의 화면 구조를 Android 네이티브로 잡기

- [ ] `activity_main.xml` 재작성: 상단 프리뷰 + TabLayout + ViewPager2
- [ ] 프리뷰 영역: 그라데이션 배경, 스프라이트 카드, 포켓몬 이름/번호, 상태 텍스트
- [ ] "펫 시작하기 / 중지하기" 버튼
- [ ] Segmented Control (재우기/걷기) — 비활성 상태로 배치
- [ ] "포켓몬" / "설정" 탭 2개 — 내용은 빈 Fragment (placeholder 텍스트)
- [ ] `PokemonListFragment.kt` + `fragment_pokemon_list.xml` (빈 화면)
- [ ] `SettingsFragment.kt` + `fragment_settings.xml` (빈 화면)

### 테스트 방법
```
1. 앱 설치 후 실행
2. 상단에 프리뷰 영역(스프라이트 카드 + 이름 + 상태)이 보이는지 확인
3. "포켓몬" / "설정" 탭을 눌러 화면이 전환되는지 확인
4. "펫 시작하기" 버튼을 눌러 오버레이가 뜨는지 확인 (기존 기능 유지)
5. Segmented Control이 보이는지 확인 (아직 동작 안 해도 OK)
```

### 파일 변경
- `activity_main.xml` 전면 재작성
- `MainActivity.kt` 수정 (ViewPager2 + TabLayout 연결)
- `PokemonListFragment.kt` 신규
- `SettingsFragment.kt` 신규
- `fragment_pokemon_list.xml` 신규
- `fragment_settings.xml` 신규

---

## Step 2: 포켓몬 그리드 + 선택

**목표**: 포켓몬 탭에서 그리드를 보고, 탭하면 프리뷰가 바뀌기

- [ ] `assets/pokemon_data.json` 추가 (macOS 버전에서 복사 — 649종, id/name/gen/types)
- [ ] `PokemonData` data class 정의 (id, name, gen, types)
- [ ] `PokemonRepository.kt` — JSON 파싱 + assets/Sprites 폴더 존재 여부로 사용 가능 판별
- [ ] RecyclerView + GridLayoutManager (5열) 구현
- [ ] 각 셀: Idle 스프라이트 첫 프레임 썸네일 + 이름 + 번호
- [ ] **썸네일 메모리 관리**: 셀 bind 시 비동기 로드 → 첫 프레임 crop → 시트 recycle, LruCache 사용
- [ ] 스프라이트 없는 포켓몬(16개 누락)은 "N/A" 표시 + 선택 불가
- [ ] 선택 상태 표시 (테두리 하이라이트)
- [ ] 포켓몬 탭 → 프리뷰 영역에 이름/번호/스프라이트 업데이트

### 테스트 방법
```
1. 앱 실행 → "포켓몬" 탭 선택
2. 포켓몬 그리드가 5열로 나오는지 확인
3. 각 셀에 스프라이트 이미지 + 이름 + 번호가 보이는지 확인
4. 빠르게 스크롤해도 버벅이거나 OOM 없이 동작하는지 확인
5. 아무 포켓몬(예: Charmander)을 탭
6. 상단 프리뷰 영역의 이름/번호/이미지가 Charmander로 바뀌는지 확인
7. 선택된 셀에 초록색 테두리가 표시되는지 확인
8. 스프라이트 없는 포켓몬(누락 폴더)이 "N/A"로 표시되고 탭해도 선택 안 되는지 확인
```

### 파일 변경
- `assets/pokemon_data.json` 신규 (macOS 버전에서 복사)
- `PokemonData.kt` 신규
- `PokemonRepository.kt` 신규
- `PokemonAdapter.kt` 신규 (비동기 썸네일 로드 + LruCache)
- `item_pokemon.xml` 신규
- `PokemonListFragment.kt` 구현

---

## Step 3: 선택한 포켓몬으로 오버레이 실행

**목표**: 그리드에서 고른 포켓몬이 실제 오버레이에 반영

- [ ] 선택된 포켓몬 ID를 SharedPreferences에 저장
- [ ] PetOverlayService가 시작 시 저장된 포켓몬 ID로 SpriteSheet 로드
- [ ] 서비스 실행 중 포켓몬 변경 → Intent로 서비스에 전달 → 오버레이 즉시 교체
- [ ] SpriteSheet에 fallback 체인 구현: `resolveAnimation(name)` — Idle↔Walk, Sleep→Idle
- [ ] SpriteSheet에 `hasAnimation(name)` / `availableReactions()` 메서드 추가
- [ ] 서비스 실행 상태 감지: `PetPreferences`에 isServiceRunning 플래그 저장, Activity `onResume`에서 확인

### 테스트 방법
```
1. 앱 실행 → 포켓몬 탭에서 Bulbasaur(#001) 선택
2. "펫 시작하기" 버튼 탭
3. 오버레이에 Bulbasaur 스프라이트가 뜨는지 확인 (Pikachu가 아닌!)
4. 앱으로 돌아가서 Charmander(#004) 선택
5. 오버레이가 즉시 Charmander로 바뀌는지 확인
6. 앱 종료 후 재실행 → 이전에 선택한 Charmander가 유지되는지 확인
7. 앱 강제 종료 후 재실행 → 서비스 실행 중이면 "펫 중지하기" 버튼 표시 확인
8. Beedrill(#015, Idle 없음) 선택 → 오버레이에 Walk 애니메이션이 대기로 표시되는지 확인
```

### 파일 변경
- `PetPreferences.kt` 신규 (SharedPreferences 래퍼 — 포켓몬 ID + 서비스 상태)
- `SpriteSheet.kt` 수정 (fallback 체인, hasAnimation, availableReactions)
- `PetOverlayService.kt` 수정 (동적 포켓몬 로드, 서비스 상태 플래그)
- `MainActivity.kt` 수정 (서비스에 포켓몬 변경 전달, 서비스 상태 확인)

---

## Step 4: 오버레이 상태 머신 + 자동 이동

**목표**: 펫이 혼자서 돌아다니고, 가만히 있다가, 잠들기

- [ ] `PetStateMachine` 구현 — 상태: IDLE, WALK, RUN, SLEEP, REACTION, DRAGGED
- [ ] IDLE → 활동 빈도에 따른 랜덤 시간(2~5초 기본) 후 WALK 전환
- [ ] WALK → 랜덤 방향 이동 → 일정 시간 후 IDLE 복귀 (자동 순환)
- [ ] RUN → Walk 애니메이션 + speedMultiplier 1.5배 + 이동 속도 2배 → 10초 후 WALK 복귀
- [ ] 장시간 비활동 → SLEEP 자동 전환 (수면 진입 설정값 기준)
- [ ] 화면 경계 충돌 시 방향 반전
- [ ] 이동 방향에 맞는 directionRow 설정 (8방향 Direction.from(dx, dy))
- [ ] 상태별 애니메이션 매핑: IDLE→Idle, WALK→Walk, RUN→Walk(1.5배속), SLEEP→Sleep
- [ ] 애니메이션 전환 시 fallback 체인 적용 (Idle↔Walk, Sleep→Idle)
- [ ] `PetView`에 `speedMultiplier` 추가 — `getFrameDelayMs()`에서 적용

### 테스트 방법
```
1. 앱에서 "펫 시작하기" 탭
2. 오버레이 펫이 가만히 있다가(Idle 애니메이션)
3. 몇 초 후 스스로 걸어다니기 시작하는지 확인 (Walk 애니메이션 + 위치 이동)
4. 걷는 방향에 맞게 스프라이트 방향이 바뀌는지 확인
5. 화면 가장자리에 닿으면 방향이 바뀌는지 확인
6. 잠시 후 다시 멈추는지 확인 (WALK → IDLE 자동 복귀)
7. 드래그가 여전히 정상 동작하는지 확인
8. 0618(Idle만 존재) 선택 후 WALK 전환 시도 → Idle이 유지되는지 확인 (fallback)
```

### 파일 변경
- `PetStateMachine.kt` 신규
- `Direction.kt` 신규 (8방향 enum + from(dx, dy) 계산)
- `PetOverlayService.kt` 수정 (상태 머신 연동, 자동 이동 타이머)
- `PetView.kt` 수정 (상태 변경 시 애니메이션 전환, speedMultiplier 추가)

---

## Step 5: 잠금화면 오버레이

**목표**: 화면 잠금 상태에서도 펫이 걸어다니는 모습이 보이되, 터치는 차단

- [ ] `FLAG_SHOW_WHEN_LOCKED` 추가 → 잠금화면에서 오버레이 표시
- [ ] `BroadcastReceiver` 등록: `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, `ACTION_USER_PRESENT`
- [ ] 화면 꺼짐(`SCREEN_OFF`) → 애니메이션 정지 (배터리 절약)
- [ ] 화면 켜짐 + 잠금(`SCREEN_ON`) → 애니메이션 재개 + `FLAG_NOT_TOUCHABLE` (터치 차단)
- [ ] 잠금 해제(`USER_PRESENT`) → `FLAG_NOT_TOUCHABLE` 제거 (터치 복원)

### 테스트 방법
```
1. 펫 시작 후 전원 버튼을 눌러 화면 끄기
2. 다시 전원 버튼을 눌러 잠금화면 진입
3. 잠금화면 위에 펫이 보이면서 걸어다니는지 확인
4. 잠금화면에서 펫을 탭/드래그해도 반응하지 않는지 확인
5. 잠금 해제 후 펫을 탭/드래그 → 정상 동작하는지 확인
6. 화면을 끈 채로 1분 방치 → 배터리 소모가 크지 않은지 확인
```

### 파일 변경
- `PetOverlayService.kt` 수정 (화면 상태 BroadcastReceiver, 터치 플래그 전환)

---

## Step 6: 오버레이 터치 인터랙션

**목표**: 펫을 탭하면 반응하고, 길게 누르면 메뉴가 나오기

### 6-1. 탭/드래그/롱프레스 분기
- [ ] 탭 vs 드래그 판별 (이동 거리 < 10px → 탭)
- [ ] 롱프레스 판별 (500ms 이상 누르고 있으면)

### 6-2. 탭 반응
- [ ] 짧은 탭 → `SpriteSheet.availableReactions()`에서 랜덤 선택하여 1회 재생 (playOnce 패턴)
  - 실제 `-Anim.png` 파일이 존재하는 애니메이션만 후보에 포함
  - Eat 누락(472/633마리) 시 Hop/Hurt에서만 랜덤 선택
  - Hop/Hurt도 누락된 극소수 케이스(예: 0593)는 반응 없이 무시
- [ ] 반응 애니메이션 끝나면 IDLE로 복귀 (REACTION → IDLE)
- [ ] Sleep 상태에서 탭 → 깨우기 (SLEEP → IDLE)

### 6-3. 롱프레스 메뉴
- [ ] 길게 누르면 오버레이 팝업 메뉴 표시
- [ ] 메뉴 항목: 재우기/깨우기, 걷기/뛰기, 종료
- [ ] "재우기" → SLEEP 상태 전환, "깨우기" → IDLE 복귀
- [ ] "걷기" → WALK 전환 (Walk 시트 없으면 비활성)
- [ ] "뛰기" → RUN 전환 (Walk 시트 + 1.5배속, 10초 후 WALK 자동 복귀)
- [ ] "종료" → 서비스 중지

### 테스트 방법
```
1. 펫 시작 후, 오버레이 펫을 가볍게 탭
2. Eat/Hop/Hurt 중 랜덤 애니메이션이 재생되는지 확인
3. 반응 후 원래 상태(Idle 또는 Walk)로 돌아가는지 확인
4. 펫을 길게 누르기 (0.5초 이상)
5. 팝업 메뉴가 나타나는지 확인
6. "재우기" 탭 → 펫이 Sleep 애니메이션으로 바뀌고 이동 멈추는지 확인
7. 다시 롱프레스 → "깨우기" 탭 → Idle로 돌아오는지 확인
8. 롱프레스 → "종료" 탭 → 오버레이가 사라지는지 확인
9. 드래그가 여전히 정상 동작하는지 확인
```

### 버그 수정: React 애니메이션 프레임 크기 점프

**증상**: 탭 반응(Hop/Hurt/Eat) 시 프레임이 끊기는 듯한 동작. Idle↔Reaction 전환 시 스프라이트 크기가 튀었음.

**원인**: `PetView.drawScaledFrame`이 프레임별 크기(`frame.width`, `frame.height`)를 기준으로 스케일을 계산했음. Idle(40×56)과 Hop(48×88) 같이 프레임 크기가 다른 애니메이션 간 전환 시 스케일 팩터가 달라져 렌더링 크기가 ~20% 점프. macOS 버전은 `spriteScale`이 모든 애니메이션에 동일하게 적용되므로 이 문제가 없었음.

**수정**: `maxFrameWidth`/`maxFrameHeight`(해당 포켓몬의 모든 사용 애니메이션 중 최대 프레임 크기)를 기준으로 고정 스케일을 계산하도록 변경. 어떤 애니메이션이든 동일한 배율로 렌더링되며, 작은 프레임은 오버레이 안에서 여백이 생기고 큰 프레임은 딱 맞게 그려짐.

**변경 파일**:
- `PetView.kt` — `drawScaledFrame`에서 maxFrame 기준 고정 스케일 적용, `maxFrameWidth`/`maxFrameHeight` 프로퍼티 추가
- `PetOverlayService.kt` — `loadPokemon`에서 `petView.maxFrameWidth`/`maxFrameHeight` 설정
- `SpriteSheet.kt` — `maxFrameWidth`/`maxFrameHeight` 계산 (사용 애니메이션 중 최대값)

### 파일 변경
- `PetOverlayService.kt` 수정 (터치 이벤트 분기)
- `OverlayMenuView.kt` 신규 (팝업 메뉴)

---

## Step 7: 설정 UI + 오버레이 실시간 반영

**목표**: 설정 탭에서 슬라이더를 조절하면 오버레이가 즉시 바뀌기

- [ ] 설정 탭 UI 구현 (mock_ui 기준)
  - 외형: 크기 (50~200%), 불투명도 (30~100%)
  - 행동: 이동 속도 (1~5), 활동 빈도 (1~5), 수면 진입 (1~10분)
- [ ] 각 슬라이더 옆에 현재 값 표시
- [ ] SharedPreferences에 저장 (PetPreferences 확장)
- [ ] 서비스에 설정 변경 전달 (Intent action)
- [ ] PetOverlayService에서 크기/투명도 즉시 반영
- [ ] PetStateMachine에서 속도/빈도/수면타이머 반영
- [ ] **설정값 수치 테이블 (macOS 검증 완료)**:
  - 이동 속도: 1→1.0, 2→1.5, 3→2.0, 4→3.0, 5→4.0 (px/frame)
  - Run 속도: Walk × 2.0
  - Idle→Walk 전환: 빈도 1→5~10초, 3→2~5초, 5→0.5~2초
  - Walk→Idle 전환: 빈도 1→2~4초, 3→3~10초, 5→8~20초
  - 수면 타임아웃: 설정값 × 60초

### 테스트 방법
```
1. 펫 시작 상태에서, 앱 → "설정" 탭 이동
2. "크기" 슬라이더를 200%로 올리기 → 오버레이 펫이 즉시 커지는지 확인
3. "불투명도" 슬라이더를 50%로 내리기 → 오버레이 펫이 반투명해지는지 확인
4. "이동 속도"를 5로 올리기 → 펫이 걸을 때 더 빠르게 이동하는지 확인
5. "활동 빈도"를 5로 올리기 → Idle에서 Walk로 전환이 더 자주 되는지 확인
6. "수면 진입"을 1분으로 설정 → 1분간 방치 후 Sleep 상태 되는지 확인
7. 앱 종료 후 재실행 → 설정값이 유지되는지 확인
```

### 파일 변경
- `SettingsFragment.kt` 구현
- `fragment_settings.xml` 구현
- `PetPreferences.kt` 확장 (설정값 저장)
- `PetOverlayService.kt` 수정 (설정 반영)
- `PetStateMachine.kt` 수정 (속도/빈도 파라미터화)

---

## Step 8: Activity ↔ Service 상태 동기화

**목표**: 앱 화면의 Segmented Control과 오버레이 상태가 양방향으로 동기화

- [ ] Service → Activity 상태 전달 (BoundService + 콜백 인터페이스 — LocalBroadcastManager는 deprecated)
- [ ] Activity → Service 명령 전달 (Intent action 또는 BoundService 직접 호출)
- [ ] 프리뷰 영역 상태 텍스트 실시간 업데이트 ("상태: 걷는 중")
- [ ] Segmented Control "재우기" 탭 → 오버레이 SLEEP 전환
- [ ] Segmented Control "걷기" 탭 → 오버레이 WALK 전환
- [ ] 오버레이에서 롱프레스 메뉴로 상태 변경 → 앱 화면에도 반영

### 테스트 방법
```
1. 펫 시작 → 앱 상단에 "상태: 대기 중" 표시 확인
2. Segmented Control에서 "재우기" 탭
   → 상태 텍스트가 "상태: 자는 중"으로 바뀌는지 확인
   → 오버레이 펫이 Sleep 애니메이션으로 바뀌는지 확인
3. Segmented Control에서 "깨우기" 탭 → 양쪽 모두 Idle로 복귀 확인
4. Segmented Control에서 "걷기" 탭
   → 오버레이 펫이 걸어다니기 시작하는지 확인
5. 오버레이에서 롱프레스 → "재우기" 선택
   → 앱으로 돌아왔을 때 상태 텍스트가 "자는 중"인지 확인
   → Segmented Control 버튼이 "깨우기"로 바뀌어 있는지 확인
```

### 파일 변경
- `PetOverlayService.kt` 수정 (상태 브로드캐스트)
- `MainActivity.kt` 수정 (브로드캐스트 수신, Segmented Control 이벤트)

---

## Step 9: 필터링 + 검색

**목표**: 세대/타입별 필터와 이름 검색을 조합하여 원하는 포켓몬을 빠르게 찾기

### 9-1. 필터 칩
- [ ] 세대별 필터 칩 (전체, 1세대, 2세대, ...)
- [ ] 타입별 필터 칩 (Grass, Fire, Water, Electric, Normal, ...)
- [ ] 필터 선택 시 그리드 즉시 갱신
- [ ] 복수 필터 조합 지원

### 9-2. 이름 검색
- [ ] 필터 칩 위에 SearchView (또는 EditText) 배치
- [ ] 현재 적용된 필터 결과 내에서 이름 검색 (필터 → 검색 순서)
- [ ] 입력할 때마다 실시간 필터링 (debounce 적용)
- [ ] 검색어 비우면 필터 결과 전체로 복원
- [ ] 영문/한글 이름 모두 검색 가능

### 테스트 방법
```
1. 포켓몬 탭에서 "전체" 선택 → 모든 포켓몬 표시 확인
2. "1세대" 칩 탭 → 1세대 포켓몬만 표시되는지 확인
3. "Fire" 타입 칩 탭 → 불 타입만 필터링되는지 확인
4. "1세대" + "Fire" 동시 선택 → 1세대 불 타입만 나오는지 확인
5. 필터 해제 시 전체 목록 복원 확인
6. 검색창에 "char" 입력 → Charmander 등 매칭되는 포켓몬만 표시
7. "1세대" 필터 + "pika" 검색 → 1세대 중 Pikachu만 나오는지 확인
8. 검색어 지우기 → 현재 필터 결과 전체로 복원 확인
9. 필터 변경 → 검색 결과가 새 필터 기준으로 갱신되는지 확인
```

### 파일 변경
- `PokemonListFragment.kt` 수정 (필터 + 검색 로직)
- `PokemonRepository.kt` 수정 (필터링 + 검색 메서드)
- `fragment_pokemon_list.xml` 수정 (SearchView + 필터 칩 영역)

---

## Step 10: 알림 개선 + 폴리싱

**목표**: Notification에서 펫을 제어하고, 엣지 케이스 안정화

### 10-1. 알림 개선
- [ ] Notification에 현재 포켓몬 이름 + 상태 표시
- [ ] Notification action 버튼: "중지", "재우기/깨우기"
- [ ] 상태 변경 시 Notification 업데이트

### 10-2. 안정화
- [ ] 서비스 강제 종료 시 리소스 정리
- [x] Bitmap 재사용 (createBitmap 호출 최소화)
- [ ] SLEEP 상태에서 애니메이션 갱신 주기 감소 (배터리 절약)
- [ ] 에셋 누락 시 fallback 처리 (기본 포켓몬)

### 테스트 방법
```
1. 펫 시작 → 상단 알림 바 내리기
2. 알림에 "Bulbasaur - 대기 중" 같은 텍스트가 보이는지 확인
3. 알림의 "재우기" 버튼 탭 → 오버레이 펫이 잠드는지 확인
4. 알림의 "중지" 버튼 탭 → 오버레이가 사라지는지 확인
5. 장시간 방치 후 배터리 사용량 확인 (설정 → 배터리)
6. 최근 앱에서 앱 강제 종료 → 오버레이가 깔끔하게 사라지는지 확인
```

### 파일 변경
- `PetOverlayService.kt` 수정 (Notification action, 리소스 정리)
- `SpriteSheet.kt` 수정 (Bitmap 캐시 최적화)
- `PetView.kt` 수정 (갱신 주기 조절)

---

## 개발 흐름 요약

```
Step 1   UI 껍데기          → 앱 열면 탭 전환 가능
Step 2   포켓몬 그리드       → 탭하면 프리뷰 변경
Step 3   선택 → 오버레이     → 고른 포켓몬이 화면에 뜸
Step 4   자동 이동           → 펫이 혼자 돌아다님
Step 5   잠금화면 오버레이   → 잠금화면에서 펫이 걸어다님
Step 6   터치 인터랙션       → 탭 반응 + 롱프레스 메뉴
Step 7   설정 + 실시간 반영  → 크기/속도 조절 즉시 적용
Step 8   상태 동기화         → 앱 ↔ 오버레이 양방향 연동
Step 9   필터링 + 검색       → 세대/타입 필터 내 이름 검색
Step 10  알림 + 폴리싱       → Notification 제어, 안정화
```

매 단계 완료 후 "테스트 방법"을 기기에서 직접 수행하여 통과 확인 후 다음 단계로 진행한다.
