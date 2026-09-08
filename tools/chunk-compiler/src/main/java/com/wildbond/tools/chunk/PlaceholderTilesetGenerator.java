package com.wildbond.tools.chunk;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * assets/tilesets/placeholder.png 를 만든다 — Tile.csv 의 id 순서(1=grass..5=sand)와 같은 순서로 나열해야
 * test_island.tmx 의 tileset(firstgid=1, columns=5)과 GID 가 맞는다. docs/m0-prompts.md 단계 4.
 *
 * <p>지형 그림은 Kenney "Tiny Town"(CC0) 시트에서 골라 온다 — 16×16 타일을 최근접 이웃으로 32×32 로
 * 확대한다(assets/CREDITS.md). <b>물만 예외로 직접 그린다</b>: Tiny Town 에는 물 타일이 없어서, 같은 화풍(굵은 외곽선·낮은 채도)에 맞춰
 * 잔물결을 얹은 타일을 만든다.
 *
 * <p>난수는 고정 시드({@value #NOISE_SEED})라서 같은 입력이면 같은 PNG 가 나온다 — 빌드 산출물이 매번 달라지지 않게 하기 위함이다.
 */
public final class PlaceholderTilesetGenerator {

  private static final int OUT_TILE_PX = 32;
  private static final int SRC_TILE_PX = 16;

  /** Tile.csv id 1..5 에 대응하는 앞쪽 칸 수. 뒤쪽 칸은 렌더 전용이라 Tile.csv 에 없다. */
  private static final int BASE_TILE_COUNT = 5;

  /**
   * 시트 전체 칸 수 = 기본 5 + 잔디 변형 3 + 물가 경계 4.
   *
   * <p>변형·경계 타일은 지형 종류가 아니라 <b>같은 지형을 덜 밋밋하게 그리는 수단</b>이라 Tile.csv 에 넣지 않는다 — 넣으면 충돌·바이옴 같은 규칙이 타일
   * 그림 개수만큼 늘어난다. 어떤 칸을 언제 쓸지는 ChunkRenderer 가 정한다.
   */
  private static final int TILE_COUNT = 12;

  /** 잔디 변형 3종이 시작하는 칸. */
  public static final int GRASS_VARIANT_START = 5;

  public static final int GRASS_VARIANT_COUNT = 3;

  /** 물가 경계 4종(북/동/남/서)이 시작하는 칸. */
  public static final int WATER_EDGE_START = 8;

  private static final long NOISE_SEED = 20260908L;

  /** Tiny Town 시트에서 가져올 타일 좌표 (col,row) — Tile.csv id 순서. 물(id 3)은 직접 그리므로 자리만 비운다. */
  private static final int[][] SOURCE_TILES = {
    {0, 0}, // 1 grass — 잔디
    {3, 3}, // 2 dirt  — 흙길
    {-1, -1}, // 3 water — 직접 그린다
    {1, 9}, // 4 cliff — 돌바닥(절벽면 대용)
    {5, 3}, // 5 sand  — 모래 섞인 길
    {1, 0}, // 6 잔디 변형 A — 잔풀
    {2, 0}, // 7 잔디 변형 B — 노란 꽃
    {7, 3}, // 8 잔디 변형 C — 돌이 박힌 잔디
  };

  private PlaceholderTilesetGenerator() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("usage: PlaceholderTilesetGenerator <tinyTownSheet.png> <outFile.png>");
      System.exit(2);
      return;
    }
    Path sheet = Path.of(args[0]);
    Path outFile = Path.of(args[1]);
    generate(sheet, outFile);
    System.out.println("tileset -> " + outFile.toAbsolutePath());
  }

  static void generate(Path sheetFile, Path outFile) throws IOException {
    BufferedImage sheet = ImageIO.read(sheetFile.toFile());
    if (sheet == null) {
      throw new IOException("타일 시트를 읽지 못했다: " + sheetFile.toAbsolutePath());
    }

    BufferedImage image =
        new BufferedImage(TILE_COUNT * OUT_TILE_PX, OUT_TILE_PX, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

    for (int i = 0; i < SOURCE_TILES.length; i++) {
      int col = SOURCE_TILES[i][0];
      if (col < 0) {
        continue; // 물 — 아래에서 직접 그린다.
      }
      int row = SOURCE_TILES[i][1];
      BufferedImage src =
          sheet.getSubimage(col * SRC_TILE_PX, row * SRC_TILE_PX, SRC_TILE_PX, SRC_TILE_PX);
      g.drawImage(src, i * OUT_TILE_PX, 0, OUT_TILE_PX, OUT_TILE_PX, null);
    }
    g.dispose();

    Random rng = new Random(NOISE_SEED);
    drawWater(image, 2 * OUT_TILE_PX, rng);
    drawWaterEdges(image);

    Path parent = outFile.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    ImageIO.write(image, "png", outFile.toFile());
  }

  /**
   * 물 — 이어 붙여도 이음매가 안 보이도록 <b>세로 그라데이션을 쓰지 않는다</b>. 균일한 바탕에 짙고 옅은 얼룩과 가로 잔물결만 얹는다(그라데이션을 쓰면 타일마다
   * 밝기가 반복돼 32px 간격 줄무늬가 생긴다).
   */
  private static void drawWater(BufferedImage img, int ox, Random rng) {
    fill(img, ox, 0xFF3C8CC8);
    speckle(img, ox, rng, 150, 0xFF3684C0);
    speckle(img, ox, rng, 120, 0xFF4A98D4);
    for (int i = 0; i < 9; i++) {
      int y = rng.nextInt(OUT_TILE_PX);
      int x = rng.nextInt(OUT_TILE_PX - 8);
      int len = 5 + rng.nextInt(8);
      hline(img, ox, x, y, len, 0xFF8FD0EC);
      if (y + 1 < OUT_TILE_PX) {
        hline(img, ox, x + 1, y + 1, Math.max(1, len - 3), 0xFF63B2DE);
      }
    }
  }

  /**
   * 물가 경계 4종 — 물 타일 쪽에 얕은 물/포말 띠를 얹는 반투명 오버레이다. 호수 가장자리가 직각으로 뚝 끊기는 것을 막는다. 순서는 북/동/남/서이고,
   * ChunkRenderer 가 이웃이 물이 아닌 방향의 것만 골라 덧그린다.
   */
  private static void drawWaterEdges(BufferedImage img) {
    int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    for (int d = 0; d < dirs.length; d++) {
      int ox = (WATER_EDGE_START + d) * OUT_TILE_PX;
      clear(img, ox);
      for (int band = 0; band < 6; band++) {
        // 바깥일수록 밝고 진하게 — 물가에서 안쪽으로 옅어진다.
        int alpha = 210 - band * 32;
        int argb = (Math.max(0, alpha) << 24) | 0x00CFEBF7;
        for (int t = 0; t < OUT_TILE_PX; t++) {
          int x = dirs[d][0] == 0 ? t : (dirs[d][0] > 0 ? OUT_TILE_PX - 1 - band : band);
          int y = dirs[d][1] == 0 ? t : (dirs[d][1] > 0 ? OUT_TILE_PX - 1 - band : band);
          img.setRGB(ox + x, y, argb);
        }
      }
    }
  }

  private static void clear(BufferedImage img, int ox) {
    for (int y = 0; y < OUT_TILE_PX; y++) {
      hline(img, ox, 0, y, OUT_TILE_PX, 0x00000000);
    }
  }

  private static void fill(BufferedImage img, int ox, int argb) {
    for (int y = 0; y < OUT_TILE_PX; y++) {
      hline(img, ox, 0, y, OUT_TILE_PX, argb);
    }
  }

  private static void speckle(BufferedImage img, int ox, Random rng, int count, int argb) {
    for (int i = 0; i < count; i++) {
      img.setRGB(ox + rng.nextInt(OUT_TILE_PX), rng.nextInt(OUT_TILE_PX), argb);
    }
  }

  private static void hline(BufferedImage img, int ox, int x, int y, int len, int argb) {
    for (int i = 0; i < len && x + i < OUT_TILE_PX; i++) {
      img.setRGB(ox + x + i, y, argb);
    }
  }
}
