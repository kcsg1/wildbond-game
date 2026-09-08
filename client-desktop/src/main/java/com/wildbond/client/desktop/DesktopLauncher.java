package com.wildbond.client.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.wildbond.client.GameConfig;
import com.wildbond.client.WildbondGame;
import java.nio.file.Path;

/** Windows x64 데스크톱 런처 — 1280×720 창, vsync (docs/architecture.md §5, §5.6). */
public final class DesktopLauncher {

  private DesktopLauncher() {}

  public static void main(String[] args) {
    WildbondPaths.dataDir(); // 세이브·로그 폴더를 미리 만들어 둔다.

    GameConfig config =
        new GameConfig(
            resolvePath("wildbond.tables", "data/tables"),
            resolvePath("wildbond.chunks", "assets/maps/chunks"),
            resolvePath("wildbond.tileset", "assets/tilesets/placeholder.png"),
            resolvePath("wildbond.sheet.town", "assets/tilesets/kenney_tiny_town.png"),
            resolvePath("wildbond.sheet.dungeon", "assets/sprites/kenney_tiny_dungeon.png"));

    Lwjgl3ApplicationConfiguration appConfig = new Lwjgl3ApplicationConfiguration();
    appConfig.setTitle("Wildbond");
    appConfig.setWindowedMode(1280, 720);
    appConfig.useVsync(true);

    new Lwjgl3Application(new WildbondGame(config), appConfig);
  }

  private static Path resolvePath(String systemProperty, String fallback) {
    return Path.of(System.getProperty(systemProperty, fallback));
  }
}
