package com.wildbond.tools.chunk;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * assets/tilesets/placeholder.png 를 색상 블록으로 생성한다 — Tile.csv 의 id 순서(1=grass..5=sand)와 같은 순서로 나열해야
 * test_island.tmx 의 tileset(firstgid=1, columns=5)과 GID 가 맞는다. docs/m0-prompts.md 단계 4.
 */
public final class PlaceholderTilesetGenerator {

  private static final int TILE_PX = 32;
  private static final Color[] COLORS = {
    new Color(0x4C, 0xAF, 0x50), // 1 grass
    new Color(0x8D, 0x6E, 0x63), // 2 dirt
    new Color(0x21, 0x96, 0xF3), // 3 water
    new Color(0x9E, 0x9E, 0x9E), // 4 cliff
    new Color(0xE0, 0xC0, 0x68), // 5 sand
  };

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
        new BufferedImage(COLORS.length * TILE_PX, TILE_PX, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    for (int i = 0; i < COLORS.length; i++) {
      g.setColor(COLORS[i]);
      g.fillRect(i * TILE_PX, 0, TILE_PX, TILE_PX);
    }
    g.dispose();

    Path parent = outFile.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    ImageIO.write(image, "png", outFile.toFile());
  }
}
