package com.wildbond.tools.chunk;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * assets/tilesets/placeholder.png 를 생성한다 — Tile.csv 의 id 순서(1=grass..5=sand)와 같은 순서로 나열해야
 * test_island.tmx 의 tileset(firstgid=1, columns=5)과 GID 가 맞는다. docs/m0-prompts.md 단계 4.
 *
 * <p>단색 블록이 아니라 결이 보이는 픽셀 타일을 그린다 — 풀은 풀잎, 물은 잔물결, 절벽은 균열, 모래·흙은 알갱이. 아직 임시 아트지만 화면에서 지형이 구분되고 호수가
 * 호수처럼 보이는 것이 목적이다. 실제 아트 아틀라스는 M1 이후다(§10).
 *
 * <p>난수는 고정 시드({@value #NOISE_SEED})라서 같은 입력이면 같은 PNG 가 나온다 — 빌드 산출물이 매번 달라지지 않게 하기 위함이다.
 */
public final class PlaceholderTilesetGenerator {

  private static final int TILE_PX = 32;
  private static final int TILE_COUNT = 5;
  private static final long NOISE_SEED = 20260908L;

  private PlaceholderTilesetGenerator() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("usage: PlaceholderTilesetGenerator <outFile.png>");
      System.exit(2);
      return;
    }
    Path outFile = Path.of(args[0]);
    generate(outFile);
    System.out.println("placeholder tileset -> " + outFile.toAbsolutePath());
  }

  static void generate(Path outFile) throws IOException {
    BufferedImage image =
        new BufferedImage(TILE_COUNT * TILE_PX, TILE_PX, BufferedImage.TYPE_INT_RGB);
    Random rng = new Random(NOISE_SEED);

    drawGrass(image, 0 * TILE_PX, rng);
    drawDirt(image, 1 * TILE_PX, rng);
    drawWater(image, 2 * TILE_PX, rng);
    drawCliff(image, 3 * TILE_PX, rng);
    drawSand(image, 4 * TILE_PX, rng);

    Path parent = outFile.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    ImageIO.write(image, "png", outFile.toFile());
  }

  /** 1 grass — 바탕에 명암 알갱이를 뿌리고 짧은 풀잎을 세운다. */
  private static void drawGrass(BufferedImage img, int ox, Random rng) {
    fill(img, ox, 0x4C9A4F);
    speckle(img, ox, rng, 150, 0x57A85A);
    speckle(img, ox, rng, 110, 0x429046);
    for (int i = 0; i < 22; i++) {
      int x = rng.nextInt(TILE_PX);
      int y = rng.nextInt(TILE_PX - 3);
      int color = rng.nextBoolean() ? 0x62B765 : 0x3B833F;
      vline(img, ox, x, y, 3, color);
    }
  }

  /** 2 dirt — 흙 알갱이와 작은 자갈. */
  private static void drawDirt(BufferedImage img, int ox, Random rng) {
    fill(img, ox, 0x8A6A4F);
    speckle(img, ox, rng, 170, 0x96755A);
    speckle(img, ox, rng, 140, 0x7C5E45);
    for (int i = 0; i < 10; i++) {
      block(img, ox, rng.nextInt(TILE_PX - 2), rng.nextInt(TILE_PX - 2), 2, 2, 0xA08464);
    }
  }

  /** 3 water — 위아래로 옅어지는 바탕에 가로 잔물결. 호수가 평평한 파란 사각형으로 보이지 않게 한다. */
  private static void drawWater(BufferedImage img, int ox, Random rng) {
    for (int y = 0; y < TILE_PX; y++) {
      // 위쪽이 조금 밝은 얕은 물, 아래로 갈수록 깊은 물.
      float t = y / (float) (TILE_PX - 1);
      int color = lerpColor(0x3E9BE8, 0x1C63C4, t);
      hline(img, ox, 0, y, TILE_PX, color);
    }
    speckle(img, ox, rng, 90, 0x2A7ED8);
    for (int i = 0; i < 7; i++) {
      int y = rng.nextInt(TILE_PX);
      int x = rng.nextInt(TILE_PX - 6);
      int len = 4 + rng.nextInt(6);
      hline(img, ox, x, y, len, 0x8FD3F5);
      if (y + 1 < TILE_PX) {
        hline(img, ox, x + 1, y + 1, Math.max(1, len - 2), 0x5FB4EE);
      }
    }
  }

  /** 4 cliff — 위 모서리에 밝은 띠, 아래로 어두워지는 바위면과 균열. */
  private static void drawCliff(BufferedImage img, int ox, Random rng) {
    for (int y = 0; y < TILE_PX; y++) {
      float t = y / (float) (TILE_PX - 1);
      hline(img, ox, 0, y, TILE_PX, lerpColor(0x9AA0A8, 0x5E646C, t));
    }
    hline(img, ox, 0, 0, TILE_PX, 0xB6BCC4); // 윗면 하이라이트
    hline(img, ox, 0, 1, TILE_PX, 0xAAB0B8);
    speckle(img, ox, rng, 120, 0x878D95);
    speckle(img, ox, rng, 90, 0x6B7178);
    for (int i = 0; i < 6; i++) {
      int x = 2 + rng.nextInt(TILE_PX - 4);
      int y = 4 + rng.nextInt(TILE_PX - 10);
      vline(img, ox, x, y, 3 + rng.nextInt(5), 0x4E545B); // 균열
    }
  }

  /** 5 sand — 밝은 모래알. */
  private static void drawSand(BufferedImage img, int ox, Random rng) {
    fill(img, ox, 0xE3C97A);
    speckle(img, ox, rng, 180, 0xEDD68C);
    speckle(img, ox, rng, 130, 0xD4B968);
    for (int i = 0; i < 8; i++) {
      hline(img, ox, rng.nextInt(TILE_PX - 5), rng.nextInt(TILE_PX), 3 + rng.nextInt(3), 0xC9AD5E);
    }
  }

  // ------------------------------------------------------------------ 그리기 유틸

  private static void fill(BufferedImage img, int ox, int rgb) {
    for (int y = 0; y < TILE_PX; y++) {
      hline(img, ox, 0, y, TILE_PX, rgb);
    }
  }

  private static void speckle(BufferedImage img, int ox, Random rng, int count, int rgb) {
    for (int i = 0; i < count; i++) {
      img.setRGB(ox + rng.nextInt(TILE_PX), rng.nextInt(TILE_PX), rgb);
    }
  }

  private static void hline(BufferedImage img, int ox, int x, int y, int len, int rgb) {
    for (int i = 0; i < len && x + i < TILE_PX; i++) {
      img.setRGB(ox + x + i, y, rgb);
    }
  }

  private static void vline(BufferedImage img, int ox, int x, int y, int len, int rgb) {
    for (int i = 0; i < len && y + i < TILE_PX; i++) {
      img.setRGB(ox + x, y + i, rgb);
    }
  }

  private static void block(BufferedImage img, int ox, int x, int y, int w, int h, int rgb) {
    for (int dy = 0; dy < h; dy++) {
      hline(img, ox, x, y + dy, w, rgb);
    }
  }

  private static int lerpColor(int from, int to, float t) {
    int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
    int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
    int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return (r << 16) | (g << 8) | b;
  }
}
