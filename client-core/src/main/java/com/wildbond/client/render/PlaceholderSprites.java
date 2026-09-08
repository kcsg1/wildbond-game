package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.data.Element;
import java.util.HashMap;
import java.util.Map;

/**
 * 임시 스프라이트를 프로그램으로 그린다 — 아틀라스 에셋(§10 2048² 아틀라스)은 M1 이후이고, 그때까지 손그림 파일 없이 형태만 알아볼 수 있게 한다 (단계 4 의
 * `PlaceholderTilesetGenerator` 와 같은 취지).
 *
 * <p><b>yDown 주의</b>: 게임 카메라는 y 가 아래로 증가한다(GameCamera). 그 투영으로 텍스처를 그대로 그리면 이미지가 위아래로 뒤집힌다 — 단색 블록일
 * 때는 안 보이던 문제다(T-006 의 BitmapFont 와 같은 원인). 그래서 이 클래스가 돌려주는 리전은 전부 <b>세로로 뒤집어</b> 둔다. Pixmap 좌표는
 * 위에서 아래로 내려가므로, 아래 그리기 코드는 "y 가 작을수록 머리 쪽"으로 읽으면 된다.
 */
public final class PlaceholderSprites implements Disposable {

  public static final int DIR_DOWN = 0;
  public static final int DIR_UP = 1;
  public static final int DIR_RIGHT = 2;
  public static final int DIR_LEFT = 3;

  /** 걷기 프레임 수 (정지 = 0번 프레임). */
  public static final int WALK_FRAMES = 2;

  private static final int W = (int) RenderConstants.PLAYER_WIDTH_PX; // 32
  private static final int H = (int) RenderConstants.PLAYER_HEIGHT_PX; // 48
  private static final int PAL_PX = 32;
  private static final int PAL_LARGE_PX = 64;
  private static final int TREE_W = 48;
  private static final int TREE_H = 64;
  private static final int SPHERE_PX = 12;
  private static final int SHADOW_PX = 10;

  private static final Color OUTLINE = Color.valueOf("14181FFF");
  private static final Color HAIR = Color.valueOf("3A2A1EFF");
  private static final Color SKIN = Color.valueOf("F0C089FF");
  private static final Color SHIRT = Color.valueOf("3F7FE0FF");
  private static final Color SHIRT_DARK = Color.valueOf("2F5FA8FF");
  private static final Color PANTS = Color.valueOf("34405AFF");
  private static final Color BOOTS = Color.valueOf("20262FFF");
  private static final Color HAIR_LIT = Color.valueOf("5A4432FF");
  private static final Color SKIN_SHADE = Color.valueOf("D2A271FF");
  private static final Color MOUTH = Color.valueOf("9C5B4AFF");
  private static final Color SHIRT_LIT = Color.valueOf("6BA0EAFF");
  private static final Color PANTS_SHADE = Color.valueOf("2A3349FF");
  private static final Color BELT = Color.valueOf("4A3524FF");
  private static final Color BUCKLE = Color.valueOf("C9A227FF");

  private final Texture playerSheet;
  private final TextureRegion[][] playerFrames = new TextureRegion[4][WALK_FRAMES];
  private final Texture dummyTexture;
  private final TextureRegion dummyRegion;
  private final Texture sphereTexture;
  private final TextureRegion sphereRegion;
  private final Texture shadowTexture;
  private final TextureRegion shadowRegion;
  private final Map<Integer, Texture> palTextures = new HashMap<>();
  private final Map<Integer, TextureRegion> palRegions = new HashMap<>();
  private final Texture treeTexture;
  private final TextureRegion treeRegion;
  private final Texture rockTexture;
  private final TextureRegion rockRegion;

  public PlaceholderSprites() {
    // 시트: 가로 3칸(정면/후면/측면) × 세로 2칸(정지/걷기).
    Pixmap sheet = new Pixmap(W * 3, H * WALK_FRAMES, Pixmap.Format.RGBA8888);
    sheet.setBlending(Pixmap.Blending.None);
    sheet.setColor(0, 0, 0, 0);
    sheet.fill();
    for (int frame = 0; frame < WALK_FRAMES; frame++) {
      drawHumanFront(sheet, 0, frame * H, frame);
      drawHumanBack(sheet, W, frame * H, frame);
      drawHumanSide(sheet, W * 2, frame * H, frame);
    }
    playerSheet = new Texture(sheet);
    sheet.dispose();

    for (int frame = 0; frame < WALK_FRAMES; frame++) {
      playerFrames[DIR_DOWN][frame] = flipped(playerSheet, 0, frame * H, W, H);
      playerFrames[DIR_UP][frame] = flipped(playerSheet, W, frame * H, W, H);
      playerFrames[DIR_RIGHT][frame] = flipped(playerSheet, W * 2, frame * H, W, H);
      TextureRegion left = new TextureRegion(playerFrames[DIR_RIGHT][frame]);
      left.flip(true, false);
      playerFrames[DIR_LEFT][frame] = left;
    }

    dummyTexture = buildScarecrow();
    dummyRegion = flipped(dummyTexture, 0, 0, W, H);
    sphereTexture = buildSphere();
    sphereRegion = flipped(sphereTexture, 0, 0, SPHERE_PX, SPHERE_PX);
    shadowTexture = buildShadow();
    shadowRegion = flipped(shadowTexture, 0, 0, SHADOW_PX, SHADOW_PX);
    treeTexture = buildTree();
    treeRegion = flipped(treeTexture, 0, 0, TREE_W, TREE_H);
    rockTexture = buildRock();
    rockRegion = flipped(rockTexture, 0, 0, PAL_PX, PAL_PX);
  }

  /** 방향(0~3)과 걷기 프레임에 맞는 플레이어 스프라이트. */
  public TextureRegion player(int direction, int frame) {
    int dir = direction < 0 || direction > 3 ? DIR_DOWN : direction;
    return playerFrames[dir][Math.floorMod(frame, WALK_FRAMES)];
  }

  /**
   * 종별 팰 스프라이트. footprint 2 인 대형 종은 64×64 로 직접 그린다 — 32×32 를 늘리면 픽셀이 뭉개진다.
   *
   * @param speciesId data/tables/PalSpecies.csv 의 id (모르는 값이면 속성 색 기본 크리처)
   */
  public TextureRegion pal(int speciesId, Element element, int footprint) {
    TextureRegion cached = palRegions.get(speciesId);
    if (cached != null) {
      return cached;
    }
    int px = footprint >= 2 ? PAL_LARGE_PX : PAL_PX;
    Texture texture = buildPal(speciesId, element, px);
    palTextures.put(speciesId, texture);
    TextureRegion region = flipped(texture, 0, 0, px, px);
    palRegions.put(speciesId, region);
    return region;
  }

  /** 나무 — 48×64 (밑동이 발 위치). */
  public TextureRegion tree() {
    return treeRegion;
  }

  /** 자원 노드 바위 — 32×32. */
  public TextureRegion rock() {
    return rockRegion;
  }

  public int treeWidthPx() {
    return TREE_W;
  }

  public int treeHeightPx() {
    return TREE_H;
  }

  public int rockSizePx() {
    return PAL_PX;
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

  // ------------------------------------------------------------------ 사람

  /** 정면(아래를 향함). frame 1 은 다리를 엇갈리고 팔을 흔들어 걷는 느낌을 준다. */
  private static void drawHumanFront(Pixmap pm, int ox, int oy, int frame) {
    int step = frame == 0 ? 0 : 1;
    drawHumanLegs(pm, ox, oy, step);
    drawHumanTorso(pm, ox, oy, step, true);

    outlinedRect(pm, ox + 10, oy + 1, 12, 15, HAIR); // 머리
    rect(pm, ox + 11, oy + 6, 10, 9, SKIN); // 얼굴
    rect(pm, ox + 11, oy + 2, 10, 4, HAIR); // 앞머리
    rect(pm, ox + 12, oy + 2, 3, 2, HAIR_LIT); // 머리 하이라이트
    rect(pm, ox + 20, oy + 6, 1, 9, SKIN_SHADE); // 얼굴 오른쪽 음영
    rect(pm, ox + 13, oy + 9, 2, 2, OUTLINE); // 눈
    rect(pm, ox + 18, oy + 9, 2, 2, OUTLINE);
    rect(pm, ox + 15, oy + 12, 2, 1, SKIN_SHADE); // 코
    rect(pm, ox + 14, oy + 14, 4, 1, MOUTH); // 입
    rect(pm, ox + 14, oy + 16, 4, 2, SKIN); // 목
  }

  /** 후면(위를 향함) — 얼굴이 보이지 않고 뒤통수만 있다. */
  private static void drawHumanBack(Pixmap pm, int ox, int oy, int frame) {
    int step = frame == 0 ? 0 : 1;
    drawHumanLegs(pm, ox, oy, -step);
    drawHumanTorso(pm, ox, oy, -step, false);

    outlinedRect(pm, ox + 10, oy + 1, 12, 16, HAIR);
    rect(pm, ox + 12, oy + 3, 4, 3, HAIR_LIT);
    rect(pm, ox + 14, oy + 16, 4, 2, SKIN);
  }

  /** 측면(오른쪽을 향함) — 왼쪽은 이 리전을 좌우 반전해 쓴다. */
  private static void drawHumanSide(Pixmap pm, int ox, int oy, int frame) {
    int step = frame == 0 ? 0 : 2;

    outlinedRect(pm, ox + 11 + step, oy + 32, 6, 12, PANTS); // 앞다리
    outlinedRect(pm, ox + 15 - step, oy + 32, 6, 12, PANTS_SHADE); // 뒷다리
    rect(pm, ox + 11 + step, oy + 43, 7, 4, BOOTS);
    rect(pm, ox + 15 - step, oy + 43, 7, 4, BOOTS);

    outlinedRect(pm, ox + 11, oy + 17, 11, 16, SHIRT); // 몸통
    rect(pm, ox + 20, oy + 18, 1, 14, SHIRT_DARK); // 등쪽 음영
    rect(pm, ox + 11, oy + 29, 11, 2, BELT); // 벨트
    outlinedRect(pm, ox + 13, oy + 18 + step, 5, 12, SHIRT_DARK); // 보이는 팔 하나
    rect(pm, ox + 14, oy + 27 + step, 3, 3, SKIN);

    outlinedRect(pm, ox + 10, oy + 1, 13, 15, HAIR);
    rect(pm, ox + 14, oy + 6, 9, 9, SKIN); // 얼굴(오른쪽을 향함)
    rect(pm, ox + 10, oy + 2, 9, 6, HAIR); // 뒷머리
    rect(pm, ox + 11, oy + 2, 3, 2, HAIR_LIT);
    rect(pm, ox + 18, oy + 9, 2, 2, OUTLINE); // 눈
    rect(pm, ox + 22, oy + 10, 1, 3, SKIN_SHADE); // 코
    rect(pm, ox + 19, oy + 14, 3, 1, MOUTH);
    rect(pm, ox + 14, oy + 16, 4, 2, SKIN);
  }

  /** 다리·신발 — 앞뒤 공통. step 부호로 어느 다리가 앞으로 나오는지 바뀐다. */
  private static void drawHumanLegs(Pixmap pm, int ox, int oy, int step) {
    outlinedRect(pm, ox + 9 - step, oy + 31, 7, 13, PANTS);
    outlinedRect(pm, ox + 16 + step, oy + 31, 7, 13, PANTS_SHADE);
    rect(pm, ox + 9 - step, oy + 43, 7, 4, BOOTS);
    rect(pm, ox + 16 + step, oy + 43, 7, 4, BOOTS);
  }

  /** 몸통·팔·손·벨트 — 앞뒤 공통. */
  private static void drawHumanTorso(Pixmap pm, int ox, int oy, int step, boolean front) {
    outlinedRect(pm, ox + 8, oy + 17, 16, 16, SHIRT);
    rect(pm, ox + 21, oy + 18, 2, 14, SHIRT_DARK); // 오른쪽 음영
    rect(pm, ox + 9, oy + 18, 2, 4, SHIRT_LIT); // 어깨 하이라이트
    rect(pm, ox + 8, oy + 29, 16, 3, BELT); // 벨트
    rect(pm, ox + 15, oy + 29, 2, 3, BUCKLE); // 버클
    if (front) {
      rect(pm, ox + 15, oy + 18, 2, 11, SHIRT_DARK); // 옷깃 라인
    }

    outlinedRect(pm, ox + 4, oy + 18 + step, 5, 12, SHIRT); // 팔
    outlinedRect(pm, ox + 23, oy + 18 - step, 5, 12, SHIRT_DARK);
    rect(pm, ox + 5, oy + 27 + step, 3, 3, SKIN); // 손
    rect(pm, ox + 24, oy + 27 - step, 3, 3, SKIN);
  }

  // ------------------------------------------------------------------- 팰

  /**
   * 종마다 다른 실루엣을 그린다 — 색만 바꾼 같은 덩어리보다 화면에서 구분이 훨씬 쉽다. data/tables/PalSpecies.csv 의 id 를 그대로 쓴다 (1
   * mossling / 2 emberpup / 3 boulderox). 모르는 id 는 속성 색 기본 크리처로 떨어진다.
   */
  private static Texture buildPal(int speciesId, Element element, int px) {
    Color body = Color.valueOf(elementColor(element));
    Color dark = shade(body, 0.62f);
    Color light = shade(body, 1.22f);

    Pixmap pm = new Pixmap(px, px, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();

    switch (speciesId) {
      case 1 -> drawMossling(pm, px, body, dark, light);
      case 2 -> drawEmberpup(pm, px, body, dark, light);
      case 3 -> drawBoulderox(pm, px, body, dark, light);
      default -> drawGenericCreature(pm, px, body, dark, light);
    }
    return toTexture(pm);
  }

  /** 1 mossling — 둥근 풀 덩어리에 잎사귀 하나, 큰 눈 두 개. */
  private static void drawMossling(Pixmap pm, int px, Color body, Color dark, Color light) {
    float k = px / 32f;
    Color leaf = Color.valueOf("6FD36BFF");
    outlinedOval(pm, s(k, 4), s(k, 10), s(k, 24), s(k, 18), body); // 몸통
    rect(pm, s(k, 7), s(k, 12), s(k, 8), s(k, 4), light); // 위쪽 하이라이트
    // 잎사귀와 줄기
    rect(pm, s(k, 15), s(k, 4), s(k, 2), s(k, 7), Color.valueOf("4E7B33FF"));
    outlinedOval(pm, s(k, 16), s(k, 1), s(k, 10), s(k, 7), leaf);
    // 눈
    eye(pm, s(k, 9), s(k, 15), s(k, 5));
    eye(pm, s(k, 18), s(k, 15), s(k, 5));
    rect(pm, s(k, 14), s(k, 22), s(k, 4), s(k, 2), dark); // 입
    rect(pm, s(k, 7), s(k, 27), s(k, 6), s(k, 4), dark); // 발
    rect(pm, s(k, 19), s(k, 27), s(k, 6), s(k, 4), dark);
  }

  /** 2 emberpup — 강아지 실루엣에 뾰족 귀와 불꽃 꼬리. */
  private static void drawEmberpup(Pixmap pm, int px, Color body, Color dark, Color light) {
    float k = px / 32f;
    // 불꽃 꼬리
    rect(pm, s(k, 26), s(k, 12), s(k, 4), s(k, 8), Color.valueOf("FFB03AFF"));
    rect(pm, s(k, 27), s(k, 9), s(k, 3), s(k, 5), Color.valueOf("FFE070FF"));

    outlinedRect(pm, s(k, 5), s(k, 16), s(k, 21), s(k, 10), body); // 몸통
    rect(pm, s(k, 7), s(k, 17), s(k, 10), s(k, 3), light);
    outlinedRect(pm, s(k, 3), s(k, 2), s(k, 6), s(k, 7), dark); // 귀
    outlinedRect(pm, s(k, 14), s(k, 2), s(k, 6), s(k, 7), dark);
    outlinedRect(pm, s(k, 2), s(k, 6), s(k, 19), s(k, 13), body); // 머리
    eye(pm, s(k, 5), s(k, 10), s(k, 5));
    eye(pm, s(k, 13), s(k, 10), s(k, 5));
    rect(pm, s(k, 8), s(k, 15), s(k, 6), s(k, 3), dark); // 주둥이
    rect(pm, s(k, 5), s(k, 26), s(k, 5), s(k, 5), dark); // 다리
    rect(pm, s(k, 18), s(k, 26), s(k, 5), s(k, 5), dark);
  }

  /** 3 boulderox — 2×2 대형. 뿔 달린 육중한 소. */
  private static void drawBoulderox(Pixmap pm, int px, Color body, Color dark, Color light) {
    float k = px / 32f;
    outlinedRect(pm, s(k, 3), s(k, 12), s(k, 26), s(k, 14), body); // 몸통
    rect(pm, s(k, 5), s(k, 13), s(k, 14), s(k, 4), light); // 등 하이라이트
    rect(pm, s(k, 6), s(k, 20), s(k, 6), s(k, 4), dark); // 바위 무늬
    rect(pm, s(k, 17), s(k, 18), s(k, 7), s(k, 5), dark);

    Color horn = Color.valueOf("E8E0CCFF");
    outlinedRect(pm, s(k, 2), s(k, 2), s(k, 5), s(k, 6), horn); // 뿔
    outlinedRect(pm, s(k, 19), s(k, 2), s(k, 5), s(k, 6), horn);
    outlinedRect(pm, s(k, 4), s(k, 5), s(k, 18), s(k, 11), body); // 머리
    eye(pm, s(k, 7), s(k, 8), s(k, 4));
    eye(pm, s(k, 15), s(k, 8), s(k, 4));
    rect(pm, s(k, 9), s(k, 12), s(k, 8), s(k, 3), dark); // 코
    rect(pm, s(k, 4), s(k, 26), s(k, 6), s(k, 5), dark); // 다리
    rect(pm, s(k, 12), s(k, 26), s(k, 6), s(k, 5), dark);
    rect(pm, s(k, 21), s(k, 26), s(k, 6), s(k, 5), dark);
  }

  /** 표에 없는 종 — 속성 색 기본 크리처. */
  private static void drawGenericCreature(Pixmap pm, int px, Color body, Color dark, Color light) {
    float k = px / 32f;
    outlinedRect(pm, s(k, 6), s(k, 3), s(k, 8), s(k, 5), dark); // 귀
    outlinedRect(pm, s(k, 18), s(k, 3), s(k, 8), s(k, 5), dark);
    outlinedRect(pm, s(k, 7), s(k, 6), s(k, 18), s(k, 13), body); // 머리
    rect(pm, s(k, 9), s(k, 7), s(k, 7), s(k, 3), light);
    eye(pm, s(k, 11), s(k, 11), s(k, 4));
    eye(pm, s(k, 18), s(k, 11), s(k, 4));
    outlinedRect(pm, s(k, 5), s(k, 18), s(k, 22), s(k, 9), body); // 몸통
    outlinedRect(pm, s(k, 26), s(k, 19), s(k, 5), s(k, 4), dark); // 꼬리
    rect(pm, s(k, 7), s(k, 26), s(k, 6), s(k, 5), dark); // 다리
    rect(pm, s(k, 19), s(k, 26), s(k, 6), s(k, 5), dark);
  }

  static String elementColor(Element element) {
    return switch (element == null ? Element.NONE : element) {
      case FIRE -> "E2571EFF";
      case WATER -> "2E86DEFF";
      case GRASS -> "3FA34DFF";
      case ELECTRIC -> "E0C020FF";
      case ICE -> "7FD4E8FF";
      case GROUND -> "A9743CFF";
      case DARK -> "5B3A70FF";
      case DRAGON -> "8E44ADFF";
      case NONE -> "9E9E9EFF";
    };
  }

  // ------------------------------------------------------------------ 나무 · 바위

  /** 나무 — 48×64. 발 위치는 아래 가운데라 밑동이 타일에 서고 수관이 위 타일을 덮는다. */
  private static Texture buildTree() {
    Color trunk = Color.valueOf("6B4A2FFF");
    Color trunkDark = Color.valueOf("4E3521FF");
    Color leafMid = Color.valueOf("2F8A3EFF");
    Color leafLit = Color.valueOf("49AE55FF");
    Color leafDark = Color.valueOf("1F6A2CFF");

    Pixmap pm = new Pixmap(TREE_W, TREE_H, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();

    // 밑동 — 수관 안쪽까지 올려 그려 사이에 잔디가 비치지 않게 한다.
    outlinedRect(pm, 20, 30, 9, 32, trunk);
    rect(pm, 26, 33, 2, 27, trunkDark);
    rect(pm, 17, 57, 15, 5, trunk); // 뿌리께
    rect(pm, 17, 60, 15, 2, trunkDark);
    // 수관 — 겹친 덩어리로 둥글게
    outlinedOval(pm, 3, 10, 42, 30, leafMid);
    outlinedOval(pm, 9, 1, 30, 24, leafMid);
    oval(pm, 8, 14, 20, 13, leafLit); // 왼쪽 위 하이라이트
    oval(pm, 27, 26, 15, 11, leafDark); // 오른쪽 아래 그늘
    oval(pm, 14, 5, 13, 9, leafLit);
    oval(pm, 6, 30, 12, 8, leafDark);
    return toTexture(pm);
  }

  /** 자원 노드 — 32×32 바위 더미. */
  private static Texture buildRock() {
    Color rock = Color.valueOf("8A8F96FF");
    Color rockDark = Color.valueOf("5E646CFF");
    Color rockLit = Color.valueOf("B2B8C0FF");

    Pixmap pm = new Pixmap(PAL_PX, PAL_PX, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();

    outlinedRect(pm, 3, 16, 15, 14, rock);
    outlinedRect(pm, 14, 8, 16, 22, rock);
    rect(pm, 17, 11, 7, 5, rockLit);
    rect(pm, 5, 19, 5, 4, rockLit);
    rect(pm, 20, 23, 8, 5, rockDark);
    rect(pm, 6, 26, 7, 3, rockDark);
    return toTexture(pm);
  }

  // -------------------------------------------------------- 허수아비 · 포획구

  /** 단계 6 확인용 허수아비 — 장대 + 가로대 + 짚 머리. */
  private static Texture buildScarecrow() {
    Color wood = Color.valueOf("6B4A2FFF");
    Color straw = Color.valueOf("D8B24AFF");
    Color cloth = Color.valueOf("A0522DFF");

    Pixmap pm = new Pixmap(W, H, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();

    outlinedRect(pm, 10, 2, 12, 12, straw); // 머리
    rect(pm, 13, 6, 2, 2, OUTLINE); // 눈
    rect(pm, 18, 6, 2, 2, OUTLINE);
    rect(pm, 14, 10, 5, 1, OUTLINE); // 입
    outlinedRect(pm, 5, 16, 22, 4, wood); // 가로대
    outlinedRect(pm, 11, 14, 10, 20, cloth); // 몸통(천)
    rect(pm, 4, 19, 4, 4, straw); // 팔 끝 짚
    rect(pm, 24, 19, 4, 4, straw);
    outlinedRect(pm, 14, 33, 4, 14, wood); // 장대
    return toTexture(pm);
  }

  private static Texture buildSphere() {
    Pixmap pm = new Pixmap(SPHERE_PX, SPHERE_PX, Pixmap.Format.RGBA8888);
    pm.setBlending(Pixmap.Blending.None);
    pm.setColor(0, 0, 0, 0);
    pm.fill();
    pm.setColor(Color.valueOf("ECECECFF"));
    pm.fillCircle(SPHERE_PX / 2, SPHERE_PX / 2, SPHERE_PX / 2 - 1);
    pm.setColor(Color.valueOf("C0392BFF"));
    pm.fillRectangle(1, 1, SPHERE_PX - 2, SPHERE_PX / 2 - 1);
    pm.setColor(OUTLINE);
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

  // ------------------------------------------------------------------ 유틸

  private static void rect(Pixmap pm, int x, int y, int w, int h, Color color) {
    pm.setColor(color);
    pm.fillRectangle(x, y, w, h);
  }

  /**
   * (x,y,w,h) 사각형에 내접하는 타원을 채운다. {@code Pixmap} 에는 원({@code fillCircle})만 있고 타원이 없어서 스캔라인으로 직접 채운다
   * — 수관·몸통처럼 가로세로 비율이 다른 덩어리에 필요하다.
   */
  private static void oval(Pixmap pm, int x, int y, int w, int h, Color color) {
    if (w <= 0 || h <= 0) {
      return;
    }
    pm.setColor(color);
    float rx = w / 2f;
    float ry = h / 2f;
    float cx = x + rx;
    float cy = y + ry;
    for (int py = y; py < y + h; py++) {
      float ny = (py + 0.5f - cy) / ry;
      float inner = 1f - ny * ny;
      if (inner <= 0f) {
        continue;
      }
      int half = Math.max(1, (int) (rx * (float) StrictMath.sqrt(inner)));
      pm.fillRectangle(Math.round(cx) - half, py, half * 2, 1);
    }
  }

  /** 어두운 테두리를 두른 타원 — 배경과 실루엣이 붙어 보이지 않게 한다. */
  private static void outlinedOval(Pixmap pm, int x, int y, int w, int h, Color fill) {
    oval(pm, x, y, w, h, OUTLINE);
    oval(pm, x + 1, y + 1, w - 2, h - 2, fill);
  }

  /** 흰자 + 검은 눈동자. */
  private static void eye(Pixmap pm, int x, int y, int size) {
    rect(pm, x, y, size, size, Color.WHITE);
    rect(pm, x + size / 3, y + size / 3, Math.max(1, size / 2), Math.max(1, size / 2), OUTLINE);
  }

  /** 배율로 밝기를 조절한 색. */
  private static Color shade(Color base, float factor) {
    return new Color(
        Math.min(1f, base.r * factor),
        Math.min(1f, base.g * factor),
        Math.min(1f, base.b * factor),
        1f);
  }

  /** 32px 기준 좌표를 실제 스프라이트 크기로 환산한다(대형 팰 64px 대응). */
  private static int s(float k, int v) {
    return Math.round(v * k);
  }

  /** 1px 어두운 테두리를 두른 사각형 — 배경(풀밭)과 실루엣이 붙어 보이지 않게 한다. */
  private static void outlinedRect(Pixmap pm, int x, int y, int w, int h, Color fill) {
    pm.setColor(OUTLINE);
    pm.fillRectangle(x, y, w, h);
    pm.setColor(fill);
    pm.fillRectangle(x + 1, y + 1, w - 2, h - 2);
  }

  private static Texture toTexture(Pixmap pm) {
    Texture texture = new Texture(pm);
    pm.dispose();
    return texture;
  }

  /** yDown 카메라로 그릴 리전 — 세로로 뒤집어 둔다(클래스 주석 참고). */
  private static TextureRegion flipped(Texture texture, int x, int y, int w, int h) {
    TextureRegion region = new TextureRegion(texture, x, y, w, h);
    region.flip(false, true);
    return region;
  }

  @Override
  public void dispose() {
    playerSheet.dispose();
    dummyTexture.dispose();
    sphereTexture.dispose();
    shadowTexture.dispose();
    treeTexture.dispose();
    rockTexture.dispose();
    for (Texture texture : palTextures.values()) {
      texture.dispose();
    }
    palTextures.clear();
    palRegions.clear();
  }
}
