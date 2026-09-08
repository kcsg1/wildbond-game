package com.wildbond.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 화면에 쓰는 그림을 한곳에서 공급한다. 지형·소품은 Kenney 의 CC0 시트(Tiny Town / Tiny Dungeon)에서 잘라 오고, 플레이어는 LPC 시트,
 * 몬스터는 {@code assets/sprites/monsters/<이름>.png} 낱장(없으면 Tiny Dungeon 의 크리처)이다. 출처·라이선스는
 * assets/CREDITS.md 참고.
 *
 * <p>Kenney 시트는 16×16 타일이 여백 없이 붙어 있다(12열 × 11행). 월드 타일이 32px 이므로 최근접 이웃으로 2배 확대해 쓴다 — 원화보다 픽셀이
 * 굵어지지만 화면 전체가 같은 배율이라 일관된다.
 *
 * <p><b>yDown 주의</b>: 게임 카메라는 y 가 아래로 증가한다(GameCamera). 그 투영으로 텍스처를 그대로 그리면 이미지가 위아래로 뒤집힌다 (T-006 의
 * BitmapFont 와 같은 원인). 그래서 이 클래스가 돌려주는 리전은 전부 <b>세로로 뒤집어</b> 둔다.
 */
public final class PlaceholderSprites implements Disposable {

  public static final int DIR_DOWN = 0;
  public static final int DIR_UP = 1;
  public static final int DIR_RIGHT = 2;
  public static final int DIR_LEFT = 3;

  /**
   * 플레이어는 LPC(Liberated Pixel Cup) 시트를 쓴다 — 4방향 걷기 9프레임 + 무기 베기 6프레임. Kenney 팩에는 방향·공격 프레임이 아예 없어서
   * 요청하신 동작을 그릴 그림 자체가 없었다. 출처·저작자는 assets/CREDITS.md.
   *
   * <p>두 시트 모두 <b>128×128 프레임</b>으로 합성해 두었다 — 베기는 검이 캐릭터 밖으로 크게 휘둘러지므로 64칸에 안 들어간다. 캐릭터 본체는 프레임
   * 한가운데 64×64 안에 있고, 발은 프레임 위에서 {@value #LPC_FOOT_FROM_TOP_PX} 픽셀 지점이다.
   */
  public static final int LPC_FRAME_PX = 128;

  public static final int LPC_WALK_FRAMES = 9;
  public static final int LPC_SLASH_FRAMES = 6;

  /** 128 프레임 안에서 발이 닿는 높이. 엔티티 위치(발)에 맞춰 그리려면 이만큼 내려 그린다. */
  public static final int LPC_FOOT_FROM_TOP_PX = 92;

  private static final int SRC = 16;

  /** 캐릭터·몬스터 기본 크기(월드 픽셀). footprint 2 인 몬스터는 두 배. */
  public static final int CHARACTER_PX = 32;

  public static final int TREE_W = 32;
  public static final int TREE_H = 64;

  /** 집은 3×4 타일 — 시트에서 3열 4행을 통째로 잘라 쓴다. */
  public static final int HOUSE_TILES_W = 3;

  public static final int HOUSE_TILES_H = 4;
  public static final int HOUSE_W = HOUSE_TILES_W * 32;
  public static final int HOUSE_H = HOUSE_TILES_H * 32;

  // --- Tiny Dungeon 시트 좌표 (col,row)
  private static final int[] MONSTER_FALLBACK_TILE = {3, 10}; // 버섯 크리처

  /**
   * data/tables/Monster.csv id → 그림. 낱장 파일이 있는 종은 {@code monsters/<이름>.png}, 없는 종은 Tiny Dungeon 타일.
   * 1 사슴 / 2 늑대 / 3 바위 골렘.
   */
  private static final Map<Integer, String> MONSTER_FILES = Map.of(1, "deer.png", 2, "wolf.png");

  private static final Map<Integer, int[]> MONSTER_TILES =
      Map.of(3, new int[] {4, 10}); // 회색 바위 크리처 — 땅 속성, 2×2

  // --- Tiny Town 시트 좌표 (col,row)
  private static final int[] TREE_TILE = {4, 0}; // 세로 2칸짜리 소나무 (4,0)+(4,1)
  private static final int[] ROCK_TILE = {7, 3}; // 돌이 박힌 땅 — 자원 노드
  private static final int[] DUMMY_TILE = {11, 7}; // 과녁 — 단계 6 허수아비
  private static final int[] HOUSE_BLUE_TILE = {0, 4}; // 파란 지붕 집 (3×4)
  private static final int[] HOUSE_RED_TILE = {4, 4}; // 붉은 지붕 집 (3×4)
  private static final int[] FENCE_TILE = {9, 6}; // 나무 울타리 가로대

  private final Texture townSheet;
  private final Texture dungeonSheet;
  private final Texture lpcWalkSheet;
  private final Texture lpcSlashSheet;
  private final Path monsterDir;
  private final List<Texture> monsterTextures = new ArrayList<>();

  /** [방향][프레임] — 방향은 DIR_* 순서(아래/위/오른쪽/왼쪽)로 다시 담는다. */
  private final TextureRegion[][] lpcWalk = new TextureRegion[4][LPC_WALK_FRAMES];

  private final TextureRegion[][] lpcSlash = new TextureRegion[4][LPC_SLASH_FRAMES];
  private final Map<Integer, TextureRegion> monsterRegions = new HashMap<>();
  private final Map<Integer, TextureRegion> monsterRegionsFlipped = new HashMap<>();
  private final TextureRegion treeRegion;
  private final TextureRegion rockRegion;
  private final TextureRegion dummyRegion;
  private final TextureRegion houseBlueRegion;
  private final TextureRegion houseRedRegion;
  private final TextureRegion fenceRegion;

  public PlaceholderSprites(
      Path tinyTownSheet, Path tinyDungeonSheet, Path lpcWalk, Path lpcSlash, Path monsterDir) {
    townSheet = loadSheet(tinyTownSheet);
    dungeonSheet = loadSheet(tinyDungeonSheet);
    lpcWalkSheet = loadSheet(lpcWalk);
    lpcSlashSheet = loadSheet(lpcSlash);
    this.monsterDir = monsterDir;
    sliceLpc(lpcWalkSheet, this.lpcWalk, LPC_WALK_FRAMES);
    sliceLpc(lpcSlashSheet, this.lpcSlash, LPC_SLASH_FRAMES);

    treeRegion = region(townSheet, TREE_TILE[0] * SRC, TREE_TILE[1] * SRC, SRC, SRC * 2);
    rockRegion = tile(townSheet, ROCK_TILE);
    dummyRegion = tile(townSheet, DUMMY_TILE);
    houseBlueRegion = houseRegion(HOUSE_BLUE_TILE);
    houseRedRegion = houseRegion(HOUSE_RED_TILE);
    fenceRegion = tile(townSheet, FENCE_TILE);
  }

  /** LPC 시트의 행 순서는 위/왼쪽/아래/오른쪽이다. 이 클래스의 DIR_* 순서(아래/위/오른쪽/왼쪽)로 옮겨 담아, 쓰는 쪽이 LPC 관례를 몰라도 되게 한다. */
  private static void sliceLpc(Texture sheet, TextureRegion[][] out, int frames) {
    int[] lpcRowForDir = {2, 0, 3, 1}; // DOWN, UP, RIGHT, LEFT
    for (int dir = 0; dir < 4; dir++) {
      for (int f = 0; f < frames; f++) {
        out[dir][f] =
            region(
                sheet,
                f * LPC_FRAME_PX,
                lpcRowForDir[dir] * LPC_FRAME_PX,
                LPC_FRAME_PX,
                LPC_FRAME_PX);
      }
    }
  }

  /** 걷기 프레임 (정지 상태는 0번). */
  public TextureRegion playerWalk(int direction, int frame) {
    int dir = direction < 0 || direction > 3 ? DIR_DOWN : direction;
    return lpcWalk[dir][Math.floorMod(frame, LPC_WALK_FRAMES)];
  }

  /** 무기 휘두르기 프레임. */
  public TextureRegion playerSlash(int direction, int frame) {
    int dir = direction < 0 || direction > 3 ? DIR_DOWN : direction;
    return lpcSlash[dir][Math.min(LPC_SLASH_FRAMES - 1, Math.max(0, frame))];
  }

  private static Texture loadSheet(Path file) {
    Texture texture = new Texture(Gdx.files.absolute(file.toAbsolutePath().toString()));
    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    return texture;
  }

  /** 종별 몬스터 스프라이트. 낱장 그림은 오른쪽을 보고 있으므로 {@code facingLeft} 면 좌우 반전본을 준다. 표에 없는 종은 기본 크리처로 떨어진다. */
  public TextureRegion monster(int speciesId, boolean facingLeft) {
    Map<Integer, TextureRegion> cache = facingLeft ? monsterRegionsFlipped : monsterRegions;
    TextureRegion cached = cache.get(speciesId);
    if (cached != null) {
      return cached;
    }
    TextureRegion base = monsterRegions.computeIfAbsent(speciesId, this::loadMonster);
    if (!facingLeft) {
      return base;
    }
    TextureRegion flipped = new TextureRegion(base);
    flipped.flip(true, false);
    monsterRegionsFlipped.put(speciesId, flipped);
    return flipped;
  }

  private TextureRegion loadMonster(int speciesId) {
    String file = MONSTER_FILES.get(speciesId);
    if (file != null && monsterDir != null) {
      Path path = monsterDir.resolve(file);
      if (Files.isRegularFile(path)) {
        Texture texture = loadSheet(path);
        monsterTextures.add(texture);
        return region(texture, 0, 0, texture.getWidth(), texture.getHeight());
      }
    }
    return tile(dungeonSheet, MONSTER_TILES.getOrDefault(speciesId, MONSTER_FALLBACK_TILE));
  }

  /** 나무 — 세로 2칸(16×32)이라 월드에서는 32×64 로 그린다. */
  public TextureRegion tree() {
    return treeRegion;
  }

  public TextureRegion rock() {
    return rockRegion;
  }

  public TextureRegion dummy() {
    return dummyRegion;
  }

  /** 마을 집 — 지붕 색 두 가지. 3×4 타일이라 앵커(왼쪽 아래 칸) 기준으로 가운데를 맞춰 그려야 한다. */
  public TextureRegion house(boolean red) {
    return red ? houseRedRegion : houseBlueRegion;
  }

  public TextureRegion fence() {
    return fenceRegion;
  }

  private TextureRegion houseRegion(int[] colRow) {
    return region(
        townSheet, colRow[0] * SRC, colRow[1] * SRC, HOUSE_TILES_W * SRC, HOUSE_TILES_H * SRC);
  }

  private static TextureRegion tile(Texture sheet, int[] colRow) {
    return region(sheet, colRow[0] * SRC, colRow[1] * SRC, SRC, SRC);
  }

  /** yDown 카메라로 그릴 리전 — 세로로 뒤집어 둔다(클래스 주석 참고). */
  private static TextureRegion region(Texture texture, int x, int y, int w, int h) {
    TextureRegion out = new TextureRegion(texture, x, y, w, h);
    out.flip(false, true);
    return out;
  }

  @Override
  public void dispose() {
    townSheet.dispose();
    dungeonSheet.dispose();
    lpcWalkSheet.dispose();
    lpcSlashSheet.dispose();
    for (Texture texture : monsterTextures) {
      texture.dispose();
    }
    monsterTextures.clear();
    monsterRegions.clear();
    monsterRegionsFlipped.clear();
  }
}
