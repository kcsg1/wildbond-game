package com.wildbond.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.wildbond.client.GameConfig;
import com.wildbond.client.InputMapper;
import com.wildbond.client.ViewState;
import com.wildbond.client.WildbondGame;
import com.wildbond.client.render.HitEffects;
import com.wildbond.client.world.Zone;
import com.wildbond.client.world.ZoneRuntime;
import com.wildbond.data.GameData;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 정적 데이터·청크 로더·sim 을 준비하고 곧바로 PlayScreen 으로 넘어간다 (docs/architecture.md §5, docs/m0-prompts.md 단계5
 * "BootScreen → PlayScreen").
 */
public final class BootScreen implements Screen {

  private final WildbondGame game;
  private final GameConfig config;

  public BootScreen(WildbondGame game, GameConfig config) {
    this.game = game;
    this.config = config;
  }

  @Override
  public void show() {
    GameData gameData = loadGameData();

    // 마을에서 시작한다 (docs/architecture.md D-16). 십자로 한가운데 광장.
    ZoneRuntime zoneRuntime = new ZoneRuntime(config, gameData);
    zoneRuntime.enter(Zone.VILLAGE, Zone.VILLAGE_SPAWN_TX, Zone.VILLAGE_SPAWN_TY);

    InputMapper inputMapper = new InputMapper();
    inputMapper.setControlledEntity(zoneRuntime.playerId());

    ViewState viewState = new ViewState();
    viewState.capture(zoneRuntime.sim().view());

    HitEffects hitEffects = new HitEffects();

    Texture tileset =
        new Texture(Gdx.files.absolute(config.tilesetFile().toAbsolutePath().toString()));

    game.setScreen(
        new PlayScreen(inputMapper, viewState, zoneRuntime, gameData, config, tileset, hitEffects));
  }

  private GameData loadGameData() {
    try {
      return GameData.load(config.tablesDir());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void render(float delta) {}

  @Override
  public void resize(int width, int height) {}

  @Override
  public void pause() {}

  @Override
  public void resume() {}

  @Override
  public void hide() {}

  @Override
  public void dispose() {}
}
