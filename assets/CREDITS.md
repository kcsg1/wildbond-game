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
