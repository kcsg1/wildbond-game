package com.wildbond.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 화면에 쓰는 그림을 한곳에서 공급한다. 대부분은 Kenney 의 CC0 시트(Tiny Town / Tiny Dungeon)에서 잘라 오고, 그 팩에 없는 것(포획구,
 * 그림자)만 코드로 그린다. 출처·라이선스는 assets/CREDITS.md 참고.
 *
 * <p>두 시트 모두 16×16 타일이 여백 없이 붙어 있다(12열 × 11행). 월드 타일이 32px 이므로 최근접 이웃으로 2배 확대해 쓴다 — 원화보다 픽셀이 굵어지지만
 * 화면 전체가 같은 배율이라 일관된다.
 *
 * <p><b>yDown 주의</b>: 게임 카메라는 y 가 아래로 증가한다(GameCamera). 그 투영으로 텍스처를 그대로 그리면 이미지가 위아래로 뒤집힌다 (T-006 의
 * BitmapFont 와 같은 원인). 그래서 이 클래스가 돌려주는 리전은 전부 <b>세로로 뒤집어</b> 둔다.
 */
public final class PlaceholderSprites implements Disposable {

  public static final int DIR_DOWN = 0;
  public static final int DIR_UP = 1;
  public static final int DIR_RIGHT = 2;
  public static final int DIR_LEFT = 3;

  /** 걷기 프레임 수. Kenney 캐릭터는 애니메이션 프레임이 없어서, 1번 프레임은 렌더가 1px 들썩이는 것으로 대신한다. */
  public static final int WALK_FRAMES = 2;

  private static final int SRC = 16;

  /** 캐릭터·팰 기본 크기(월드 픽셀). 대형 팰은 두 배. */
  public static final int CHARACTER_PX = 32;

  public static final int PAL_LARGE_PX = 64;
  public static final int TREE_W = 32;
  public static final int TREE_H = 64;
  private static final int SPHERE_PX = 12;
  private static final int SHADOW_PX = 10;

  // --- Tiny Dungeon 시트 좌표 (col,row)
  private static final int[] PLAYER_TILE = {2, 8}; // 갈색 머리 모험가
  private static final int[] PAL_FALLBACK_TILE = {3, 10}; // 버섯 크리처

  /** data/tables/PalSpecies.csv id → Tiny Dungeon 타일. 1 mossling / 2 emberpup / 3 boulderox. */
  private static final Map<Integer, int[]> PAL_TILES =
      Map.of(
          1, new int[] {0, 9}, // 초록 슬라임 — 풀 속성
          2, new int[] {1, 9}, // 주황 골렘 — 불 속성
          3, new int[] {4, 10}); // 회색 바위 크리처 — 땅 속성, 2×2

  // --- Tiny Town 시트 좌표 (col,row)
  private static final int[] TREE_TILE = {4, 0}; // 세로 2칸짜리 소나무 (4,0)+(4,1)
  private static final int[] ROCK_TILE = {7, 3}; // 돌이 박힌 땅 — 자원 노드
  private static final int[] DUMMY_TILE = {11, 7}; // 과녁 — 단계 6 허수아비

  private final Texture townSheet;
  private final Texture dungeonSheet;
  private final Texture sphereTexture;
  private final Texture shadowTexture;

  private final TextureRegion[] playerFrames = new TextureRegion[4];
  private final Map<Integer, TextureRegion> palRegions = new HashMap<>();
  private final TextureRegion treeRegion;
  private final TextureRegion rockRegion;
  private final TextureRegion dummyRegion;
  private final TextureRegion sphereRegion;
  private final TextureRegion shadowRegion;

  public PlaceholderSprites(Path tinyTownSheet, Path tinyDungeonSheet) {
    townSheet = loadSheet(tinyTownSheet);
    dungeonSheet = loadSheet(tinyDungeonSheet);

    TextureRegion front = tile(dungeonSheet, PLAYER_TILE);
    playerFrames[DIR_DOWN] = front;
    playerFrames[DIR_UP] = front; // Kenney 캐릭터는 정면 한 장뿐이다 — 위/아래는 같은 그림을 쓴다.
    playerFrames[DIR_RIGHT] = front;
    TextureRegion left = new TextureRegion(front);
    left.flip(true, false);
    playerFrames[DIR_LEFT] = left;

    treeRegion = region(townSheet, TREE_TILE[0] * SRC, TREE_TILE[1] * SRC, SRC, SRC * 2);
    rockRegion = tile(townSheet, ROCK_TILE);
    dummyRegion = tile(townSheet, DUMMY_TILE);

    sphereTexture = buildSphere();
    sphereRegion = region(sphereTexture, 0, 0, SPHERE_PX, SPHERE_PX);
    shadowTexture = buildShadow();
    shadowRegion = region(shadowTexture, 0, 0, SHADOW_PX, SHADOW_PX);
  }

  private static Texture loadSheet(Path file) {
    Texture texture = new Texture(Gdx.files.absolute(file.toAbsolutePath().toString()));
    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    return texture;
  }

  /** 방향에 맞는 플레이어 스프라이트. frame 은 렌더가 들썩임에 쓰고 그림은 같다. */
  public TextureRegion player(int direction, int frame) {
    int dir = direction < 0 || direction > 3 ? DIR_DOWN : direction;
    return playerFrames[dir];
  }

  /** 종별 팰 스프라이트. 표에 없는 종은 기본 크리처로 떨어진다. */
  public TextureRegion pal(int speciesId) {
    return palRegions.computeIfAbsent(
        speciesId, id -> tile(dungeonSheet, PAL_TILES.getOrDefault(id, PAL_FALLBACK_TILE)));
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

  public TextureRegion sphere() {
    return sphereRegion;
  }

  public TextureRegion shadow() {
    return shadowRegion;
  }

  public int spherePx() {
    return SPHERE_PX;
  }

  public int shadowPx() {
    return SHADOW_PX;
  }

  // ------------------------------------------------------- 팩에 없어서 직접 그리는 것

  /** 포획구 — Kenney 팩에 대응물이 없다. 흰/빨강 이색 구. */
  private static Texture buildSphere() {
    Pixmap pm = new Pixmap(SPHERE_PX, SPHERE_PX, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();
    pm.setColor(Color.valueOf("ECECECFF"));
    pm.fillCircle(SPHERE_PX / 2, SPHERE_PX / 2, SPHERE_PX / 2 - 1);
    pm.setColor(Color.valueOf("C0392BFF"));
    pm.fillRectangle(1, 1, SPHERE_PX - 2, SPHERE_PX / 2 - 1);
    pm.setColor(Color.valueOf("2B2B33FF"));
    pm.drawCircle(SPHERE_PX / 2, SPHERE_PX / 2, SPHERE_PX / 2 - 1);
    pm.drawLine(1, SPHERE_PX / 2, SPHERE_PX - 2, SPHERE_PX / 2);
    return toTexture(pm);
  }

  private static Texture buildShadow() {
    Pixmap pm = new Pixmap(SHADOW_PX, SHADOW_PX, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();
    pm.setColor(0f, 0f, 0f, 0.4f);
    pm.fillCircle(SHADOW_PX / 2, SHADOW_PX / 2, SHADOW_PX / 2 - 1);
    return toTexture(pm);
  }

  private static Texture toTexture(Pixmap pm) {
    Texture texture = new Texture(pm);
    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    pm.dispose();
    return texture;
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
    sphereTexture.dispose();
    shadowTexture.dispose();
    palRegions.clear();
  }
}
