AC 계기판은 Pleos Connect 차량 화면 오른쪽 앱 영역에서 돌아가는 아세토코르사 텔레메트리 계기판이에요. 이 시스템은 두 층으로 되어 있어요.ㅎ

- **Pleos 기반층**: Pleos Connect 디자인 에셋과 디스플레이 가이드의 토큰, 컴포넌트 규격, 아이콘을 값 그대로 옮긴 것. 이름도 Pleos 이름을 따라요(`background-primary`, `text-tertiary`, `button-basic-enabled`, `radius-button-small`…). 이 값은 바꾸지 마세요.
- **앱층**: 계기판에만 있는 의미(제동, 경고등, 랩 기록)를 위한 토큰으로, 모두 `ac-` 접두사가 붙어요. 새 색을 만들지 않고 Pleos 팔레트 안의 색을 골라 의미를 붙였어요.

## 화면

- 기준 화면은 Pleos `ivi_standard_window` 2560×1440이에요. 상태 표시줄 96px, 본문 1184px, 하단 GNB 160px이고, 본문 왼쪽 840px은 Pleos 드라이빙 뷰, 오른쪽 **1700×1184px이 앱 영역**이에요. 다른 폼팩터는 2048×1152(standard)와 1920×1080(lite)이에요.
- 160dpi라서 1dp = 1px이에요. 이 시스템의 모든 수치는 px 그대로 dp로 쓰면 돼요.
- 앱 영역 안쪽 여백은 `screen-padding`(24px)이에요. 맨 위 64px 줄에 RPM 시프트 라이트, 세션 정보, 연결 상태, 편집 버튼이 있고, 그 아래를 카드 그리드가 채워요.
- 앱은 밝은 화면이 기본이에요. Pleos 시스템 UI가 밝은 화면이라 같이 맞춰요. 다크 테마는 야간용으로 정의만 해 두었어요.

## 말투와 표기

- 라벨은 짧은 명사예요: "현재 랩", "최근 랩", "베스트 랩", "가속", "브레이크", "연료", "타이어 온도", "트랙 맵".
- 버튼은 한 단어예요: "편집", "초기화", "완료".
- 안내 문장은 해요체로 한 문장만 써요: "트랙 학습 중 · 결승선부터 한 바퀴를 돌면 지도가 완성돼요", "알림 접근 권한이 필요해요". 사과나 느낌표는 쓰지 않아요.
- 고유명사는 원문 그대로 써요: Assetto Corsa, Ferrari 488 GT3, Monza, RPM. 이모지는 쓰지 않아요.
- 숫자는 모두 고정폭 숫자(`tnum`)로 써서 값이 바뀌어도 옆으로 흔들리지 않게 해요.

| 값 | 표기 | 예 |
|---|---|---|
| 랩 타임 | `m:ss.mmm` | 1:46.083 |
| 랩 차이 | 부호와 초 | +0.342, ±0.000, −0.218 |
| 속도 | 정수, 단위는 아래 줄 | 247 / km/h |
| 회전수 | 천 단위 쉼표 | 6,617 rpm |
| 연료 | 소수 한 자리 | 47.5 L |
| 타이어 온도 | 정수와 ° | 91° |
| 페달 | 정수 % | 100% |
| 값 없음 | 두 줄표 | -- |

## 색

### 기반: Pleos 중립색

- 화면 배경은 `background-primary`, 카드는 `background-secondary`예요.
- 글자는 `text-primary`(제목, 값), `text-secondary`(보조 문장), `text-tertiary`(라벨, 단위)를 써요. `text-quaternary`는 대비가 2.1:1이라 비활성 상태와 선택 안 된 기어에만 써요.
- 빈 트랙(바, 게이지, 꺼진 세그먼트, 타이어 칸)은 `track`, 구분선은 `divider-secondary`예요.
- 버튼은 Pleos 그대로예요. 기본 버튼은 `button-basic-enabled`, 눌렀을 때 `button-basic-pressed`예요. 모드를 끝내는 버튼 하나만 `button-filled-enabled`로 채워요.

### 앱 색 (추천)

강조색을 새로 만들지 않고 Pleos 팔레트 안에서 골랐어요. 그래서 Pleos 드라이빙 뷰 바로 옆에 놓여도 한 화면처럼 보여요. 색마다 뜻이 하나씩 정해져 있어요.

| 역할 | 토큰 | 값 | 출처 (Pleos) | 쓰는 곳 |
|---|---|---|---|---|
| 내 입력 · 내 위치 | `ac-accent` | #4781FF | Feature/Media/music-primary | 가속 바, 트랙 맵의 내 차, 시프트 라이트 1단계, 음악 진행 바, 편집 모드 테두리, 포커스 링 |
| 한계 · 제동 | `ac-brake` | #FE3D16 | Button/Call/end-normal | 브레이크 바, 레드라인, 시프트 라이트 3단계, 과열 타이어, 베스트보다 느린 랩 |
| 개입 경고 | `ac-telltale` | #FF8A00 | 드라이빙 뷰 경고등 | TCS·ABS 경고등, 시프트 라이트 2단계 |
| 정상 · 개인 최고 | `ac-ok` | #02C265 | Switch/background-on | 연결 상태 점, 개인 최고 랩 표시 점 |
| 세션 최고 | `ac-fastest` | #C911E7 | Feature/Media/radio-primary | 세션 최고 랩·섹터 (모터스포츠 타이밍의 보라색) |
| 차가움 / 뜨거움 | `ac-cold`, `ac-hot` | 위 파랑·빨강의 16% | — | 타이어 온도 칸 배경 |

색을 고른 이유:

- **뜻을 섞지 않아요.** 파랑은 언제나 운전자가 하는 일(밟기, 위치), 빨강은 한계, 주황은 차가 개입한 순간이에요. 같은 색이 다른 뜻으로 쓰이는 곳이 없어요.
- **파랑과 주황·빨강으로 구분해요.** 가속은 레이싱 오버레이에서 흔히 쓰는 초록이 아니라 파랑이에요. 빨강·초록 쌍은 색약인 사람에게 구분이 어렵고, 이 두 색은 밝기 차이도 거의 없어서예요.
- **보라는 모터스포츠 관례예요.** 타이밍 화면에서 보라는 전체 최고 기록을 뜻해서, 레이싱을 아는 사람에게 설명 없이 읽혀요.

대비 규칙:

- `ac-accent`와 `ac-brake`는 흰 배경에서 3.6:1이에요. 바, 아이콘, 19px 이상 굵은 글자에만 쓰고 작은 글자에는 쓰지 않아요.
- `ac-telltale`과 `ac-ok`는 Pleos 원본 값이라 흰 배경에서 2.4:1밖에 안 돼요. 글자로 쓰지 말고, 반드시 모양(경고등 아이콘, 점)과 글자를 함께 써요.
- 강조색 위의 글자는 `on-accent`(흰색, 굵은 19px 이상)예요. 그보다 작으면 `text-primary`를 써요.

### 지금 앱과 다른 점

- 랩 차이는 아직 추천안과 달라요. 지금 앱은 0 이하를 모두 `ac-accent`로 보여줘요. 추천안은 빨라졌을 때 `ac-ok` 점과 `text-primary`, 세션 최고일 때 `ac-fastest`예요.
- `ac-fastest`(보라)는 아직 앱 어디에도 쓰이지 않아요. 섹터 타임을 붙일 때 함께 들어갈 색이에요.
- 나머지 색(가속 파랑, 브레이크 빨강, 경고등 주황, 연결 상태 초록)은 이미 앱에 이 값으로 들어가 있어요.

### 다크 테마

야간용으로 Pleos의 밝은 글자 토큰을 써서 정의했어요. 배경 `basic-900`, 카드 `basic-800`(Surface/container), 글자 `text/light/*`, 트랙 `basic-700`이에요. 채운 버튼은 Pleos 키보드(static)의 #686A72 / #44464E를 빌려 썼어요. 앱 색(`ac-`)은 두 테마에서 같은 값이고, 타이어 칸 틴트(`ac-cold`, `ac-hot`)만 어두운 배경에서 잘 보이게 25%로 진하게 했어요.

## 글꼴

- 글꼴은 Pleos 시스템 글꼴인 **Asta Sans** 하나예요(Regular 400, Bold 700, ExtraBold 800).
- 크기는 Pleos 타입 스케일만 써요. 카드 제목 `body-small`(26/34 Bold), 값 `body-large`(30/38 Bold), 라벨 `label-medium`(24/28), 가장 작은 글자 `label-small`(20/24)이에요.
- 20px보다 작은 글자는 쓰지 않아요. Pleos 가이드가 실제 화면에서 18–20px 이상을 권해요.
- 속도와 진행 중인 랩 타임은 `driving-speed`(112/134 Bold, 자간 −3px)예요. 카드가 작으면 이 크기를 최대로 두고 칸에 맞게 줄여요.

## 레이아웃

- 앱 영역은 카드 그리드예요. 칸 사이는 `spacing-05`(16px)이고, 카드는 자기 크기를 정하지 않고 받은 칸을 채워요.
- 기본(wide, 1100dp 이상) 배치는 3열, 행 비율 4 : 1 : 3이에요.

| | 1열 | 2열 | 3열 |
|---|---|---|---|
| 1행 | 계기판 | 음악 (2열 차지) | |
| 2행 | 트랙 맵 (2행 차지) | 랩 타임 (2행 차지) | 연료 |
| 3행 | | | 타이어 온도 |

- 좁은 화면은 medium(700dp 이상, 2열)과 compact(2열)로 바뀌어요. 운전자는 편집 모드에서 카드를 끌어 옮기거나 오른쪽 아래 모서리로 크기를 바꿀 수 있어요. 편집 중에는 카드 테두리와 손잡이를 `ac-accent`로 그리고, 놓을 수 없는 자리는 `ac-brake`로 보여줘요.
- 카드 안쪽 여백은 `spacing-07`(32px), 계기판처럼 빽빽한 카드는 `screen-padding`(24px)이에요. 요소 사이는 `spacing-03`(8), `spacing-04`(12), `spacing-05`(16)만 써요.

## 모양

- 카드 `radius-card`(32), 아이콘 버튼과 타일 `radius-icon-button`(20), 작은 버튼 `radius-button-small`(16), 배지 `radius-badge`(12), 바와 점 `radius-pill`이에요.
- 그림자는 `shadow-level2` 하나만 써요. 카드에만 쓰고 버튼에는 쓰지 않아요.
- 테두리로 카드를 구분하지 않아요. 카드는 그림자와 배경 차이로만 떠 보여요.

## 상태와 움직임

- 누른 상태는 채우기를 한 단계 진하게 해요(`button-basic-pressed`, `button-filled-pressed`). 비활성은 채우기를 빼고 `divider-secondary` 2px 테두리와 `text-quaternary` 글자예요.
- 포커스는 `focus-ring` 3px 실선, 3px 간격이에요. 차량 로터리 컨트롤러 포커스도 이 링으로 보여줘요.
- 움직이는 것은 시프트 라이트 깜빡임(90ms 주기) 하나뿐이에요. 텔레메트리가 초당 30번 들어오니 값에 애니메이션을 넣지 않아요.

## 아이콘

- Pleos 아이콘은 48px, 24 그리드 위에 그리고, 채움형과 선형 두 가지예요. 선은 3.6px 둥근 끝, 모서리는 1.5px 둥글기예요.
- 시스템 아이콘은 `assets/Icons`, 경고등은 `assets/Telltales`, 차량 그림은 `assets/Vehicle`에 원본 그대로 있어요. 아이콘 버튼 안에서는 56px, 작은 버튼에서는 44px, 버튼 글자 옆에서는 28px로 써요.
- 경고등은 원래 색 그대로 쓰고, 꺼졌을 때만 `basic-200`으로 바꿔요.
- Pleos 파일에는 미디어 아이콘이 없어요. 음악 카드의 셔플, 이전 곡, 재생, 일시정지, 다음 곡, 반복 아이콘은 Material 아이콘(Apache 2.0)을 Pleos 규칙(24 그리드, 채움형)에 맞춰 쓴 대체품이에요. Pleos가 미디어 아이콘을 내면 바꿔 주세요.

## 음악 카드

- 음악 카드(`MusicPlayer`)만 흰 카드가 아니에요. 재생 중인 앨범 아트를 크게 흐려서 카드 바탕으로 깔고, 그 위에 큰 앨범 아트, 제목, 진행 바, 컨트롤을 올려요. 전체 화면 음악 플레이어처럼 보이게 하는 게 목표예요.
- 흐린 아트 위에는 `alpha-dark-300`에서 `alpha-dark-500`으로 짙어지는 막을 덮어서, 밝은 아트에서도 글자가 읽히게 해요.
- 이 바탕 위의 글자와 아이콘은 테마와 상관없이 Pleos `static-light-*` 색을 써요. 제목 `static-light-100`, 아티스트 `static-light-200`, 시간 `static-light-300`이에요.
- 컨트롤은 배경 없는 흰 아이콘이에요. 누르거나 셔플·반복이 켜졌을 때만 `alpha-light-200` 동그라미가 생겨요.
- 앨범 아트가 없으면 바탕은 `basic-600`에서 `basic-800`으로 가는 그라데이션이에요.

## Claude Design으로 화면을 만들 때

- 앱 화면만 그릴 때는 1700×1184 캔버스를, 차량 화면 전체를 그릴 때는 2560×1440에서 왼쪽 840px을 Pleos 드라이빙 뷰 자리로 비워 두세요.
- 색, 글꼴, 간격, 둥글기는 이 시스템의 토큰 이름으로만 지정하세요. 새 색이 필요하면 먼저 `ac-` 토큰을 추가하고 그 뜻을 정하세요.
- 카드 종류와 데이터: 계기판(속도, 회전수, 기어, 가속·브레이크 %, TCS·ABS), 음악(제목, 아티스트, 앨범 아트, 진행), 트랙 맵(트랙 선, 내 위치, 랩 진행 %), 랩 타임(현재·최근·베스트, 랩 수, 차이), 연료(L, 남은 비율), 타이어 온도(앞왼쪽, 앞오른쪽, 뒤왼쪽, 뒤오른쪽 °C).

---

## Consuming this system (generated — do not edit)

Every path named below is under `project/` in this design system: read `project/api/tokens.md`, not `api/tokens.md`.

9 components are documented without a runnable `components/bundle.js`: read each component’s card, and its README where it has one (`components/<Comp>/README.md`), and build to those guidelines. Tokens: the values are on `api/tokens.md`; a Slides deck or Design canvas also takes `tokens.json` by file path. On a Design canvas, also copy `tokens.css` to `project/ds/<folder>/tokens.css` in the call that installs this system, and link it in each artboard that uses this system, right after the `support.js` line and before the system’s other stylesheets: `<link rel="stylesheet" href="ds/<folder>/tokens.css">` (with one `../` in front for each folder the artboard is in).

Fonts: `tokens.css` loads each font from its file, by path.

- **Design canvas:** copy `fonts/AstaSansOTF-Regular.otf`, `fonts/AstaSansOTF-Bold.otf` and `fonts/AstaSansOTF-ExtraBold.otf` to the same paths under the canvas’s `project/ds/<folder>/` (`<folder>` is the folder you install this system under; `fonts/AstaSansOTF-Regular.otf` lands on `project/ds/<folder>/fonts/AstaSansOTF-Regular.otf`), in the same call that installs this system; the copy of `tokens.css` that each artboard links loads them from there. If the install’s reply leaves a font out, bring that font in as for any other page.
- **Slides deck:** for each family you use in the deck (`faces` holds at most the number of typefaces Slides’ `deck-files.md` gives), install its file with the system and name the path it lands on as that family’s `src` in `project/deck.json` `faces`, under the key in the Slides column. A deck takes a font as a file only under the rule in Slides’ `fonts.md` (no space or leading underscore in its path, for two): any other font, or one kept by id, is uploaded as `fonts.md` says.
- **Any other page:** one file per family is enough (below: the upright face nearest regular weight; bold and italic synthesize). Fetch it as the fetch column says (Artifact tool `read`, that id or path as `path`), upload it as an asset (`publish`, `file_path`, `asset:true`) and declare it with an `@font-face { font-family: "<family>"; src: url(<uploaded>) }` rule.

| family | file | fetch | CSS | Slides `faces` key |
| --- | --- | --- | --- | --- |
| Asta Sans | `fonts/AstaSansOTF-Regular.otf` (weight 400) | `read` the file with `project/` in front | `var(--font-sans)` | `asta-sans` |

**Read, per thing:** a component’s props, parts and examples: `api/components/<Comp>.md`; token values: `api/tokens.md`; stored assets and their paths: `api/assets/<Group>.md`. After this README, fetch the cards and fonts you need in ONE message as parallel calls — none depends on another.

**Two rules.** Before you use a thing — a component, a token group, an icon, an asset — read its card from the index below; a value you did not read from a card is a guess. `tokens.json`, `manifest.json` and `design-system.json` are sources for tools: hand them over. `components/<Comp>/README.md` and `assets/<Group>/README.md` are the long-form second read a card links to; `SKILL.md` and `artifact-type/` beside them are authoring guidance, not needed to consume the system.

## Index (generated — do not edit)

**Tokens**

- `api/tokens.md` — Every token: surface, text, fill, border, palette, type, spacing, radius, shadow, size. (17.4k)

**Icons and assets**

- `api/assets/Icons.md` — 8 files, by asset id. (1.8k)
- `api/assets/Telltales.md` — 5 files, by asset id. (1.6k)
- `api/assets/Vehicle.md` — 2 files, by asset id. (1.0k)

**Components** (`api/components/<Comp>.md`, 9)

- **Actions**: `Button` — Pleos ivi_basic_button at Small size: a 64px text button for screen-level actions such as 편집, 초기화, 완료 · `IconButton` — Pleos ivi_icon_button, Basic type at Medium size: an 80px square with 20px corners holding a 56px icon
- **Surfaces**: `Card` — The one surface of the dashboard: every widget (cluster, music, track map, laps, fuel, tyres) is a Card on background-primary
- **Telemetry**: `GearIndicator` — The gear row under the tachometer, drawn like the Pleos driving view's P R N D: the engaged gear sits in a filled rounded square, the rest are faint · `LapDelta` — The difference between the last lap and the best lap, beside 최근 랩, in motorsport timing colours · `LevelBar` — A fully rounded track with a fill clipped to it, after the Pleos slider_track · `ShiftLight` — Fifteen rounded segments across the top bar that fill as revs approach the shift point · `Telltale` — Driving-assist warning lights in the top-left corner of the cluster card, using the Pleos driving-view telltale icons as drawn (assets/Telltales) · `TyreTile` — Four tiles, front-left, front-right, rear-left, rear-right, each showing a tyre's core temperature
