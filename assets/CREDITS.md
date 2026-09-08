# 에셋 출처 · 라이선스

이 폴더의 그림 중 아래 파일은 외부에서 가져온 것이다. 나머지(예: `tilesets/placeholder.png`)는
빌드 시 `tools/chunk-compiler` 가 생성한다.

## Kenney — CC0 1.0 (퍼블릭 도메인)

| 파일 | 원본 팩 | 출처 |
|---|---|---|
| `tilesets/kenney_tiny_town.png` | Kenney "Tiny Town" | https://kenney.nl/assets/tiny-town |
| `sprites/kenney_tiny_dungeon.png` | Kenney "Tiny Dungeon" | https://kenney.nl/assets/tiny-dungeon |

- 라이선스: [Creative Commons Zero v1.0 Universal (CC0)](http://creativecommons.org/publicdomain/zero/1.0/)
- 저작자: Kenney Vleugels (www.kenney.nl)
- CC0 는 **표기 의무가 없고 상업적 사용도 자유**다. 그래도 예의상 여기에 적어 둔다.
  로드맵 §7 의 Steam 출시를 감안해 전염성 라이선스(CC-BY-SA 등)는 일부러 피했다.
- 원본 파일은 손대지 않았다. 두 파일 모두 16×16 타일이 여백 없이 붙은 시트다(12열 × 11행).

## 게임 안에서 어떻게 쓰는가

- 지형: `tools/chunk-compiler` 의 `PlaceholderTilesetGenerator` 가 Tiny Town 시트에서 필요한 타일을
  골라 32×32 로 확대해 `tilesets/placeholder.png` 를 만든다. **물은 Tiny Town 에 없어서 직접 그린다.**
- 캐릭터·팰·나무·바위: `client-core` 의 `PlaceholderSprites` 가 두 시트를 읽어 잘라 쓴다.


## LPC (Liberated Pixel Cup) — 플레이어 캐릭터

| 파일 | 내용 |
|---|---|
| `sprites/lpc_player_walk.png` | 4방향 걷기 9프레임 (128×128 프레임으로 합성) |
| `sprites/lpc_player_slash.png` | 4방향 무기 베기 6프레임 (128×128 프레임으로 합성) |

두 파일은 [LPC Universal Spritesheet Character
Generator](https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator)
의 레이어(몸·머리·머리카락·상의·바지·신발·검)를 겹쳐 만든 것이다. 원본 레이어는 수정하지 않았고 합치기만 했다.

- **라이선스: CC-BY-SA 3.0 / GPL 3.0 / OGA-BY 3.0 / GPL 2.0 / CC0** (레이어마다 다르며, 합성물에는 가장 강한
  조건이 적용된다고 보는 것이 안전하다)
- **저작자 표기는 의무다.** 쓴 레이어들의 저작자:

  Benjamin K. Smith (BenCreating), bluecarrot16, Durrani, Eliza Wyatt (ElizaWy), Evert,
  JaidynReiman, Johannes Sjölund (wulax), Matthew Krohn (makrohn), MuffinElZangano, Nila122,
  Stephen Challener (Redshrike), TheraHedwig

- 원본 출처: https://opengameart.org/content/liberated-pixel-cup-lpc-base-assets-sprites-map-tiles
  외 LPC 관련 OpenGameArt 페이지들 (생성기 저장소의 `CREDITS.csv` 참고)

### 라이선스 방침 (2026-09-09 갱신)

처음에는 "CC0만 쓴다"였다 — 로드맵 §7 의 Steam 출시 때문이었다. **사람이 "스팀에 올리지 않는다, 개인용"이라고
정해서** CC-BY-SA/GPL 계열도 쓰기로 했다. 대신 다음을 지킨다:

- 위 저작자 목록을 지운 채 배포하지 않는다 (CC-BY-SA·OGA-BY 의 표기 의무).
- **나중에 상업 배포로 방향이 바뀌면 이 캐릭터 에셋을 먼저 걷어내야 한다.** 코드는 영향받지 않지만,
  LPC 아트를 실은 채 상업 배포하면 아트를 같은 라이선스로 공개해야 한다.
