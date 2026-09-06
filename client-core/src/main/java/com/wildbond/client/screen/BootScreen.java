package com.wildbond.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.wildbond.client.GameConfig;
import com.wildbond.client.InputMapper;
import com.wildbond.client.ViewState;
import com.wildbond.client.WildbondGame;
import com.wildbond.client.map.FileChunkLoader;
import com.wildbond.client.render.HitEffects;
import com.wildbond.client.render.RenderConstants;
import com.wildbond.data.Element;
import com.wildbond.data.GameData;
import com.wildbond.sim.ChunkTileMap;
import com.wildbond.sim.Command;
import com.wildbond.sim.Sim;
import com.wildbond.sim.SimView;
import com.wildbond.sim.events.Damaged;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 정적 데이터·청크 로더·sim 을 준비하고 곧바로 PlayScreen 으로 넘어간다 (docs/architecture.md §5, docs/m0-prompts.md 단계5
 * "BootScreen → PlayScreen").
 */
public final class BootScreen implements Screen {

  private static final long WORLD_SEED = 20260904L;

  // test_island.tmx 의 연못(tx 10~17,ty 10~15)·절벽(tx 40~41) 을 피하고, 카메라 절반 폭(가상 640px
  // = 타일 10개, 높이는 타일 5.625개)이 어느 쪽으로도 맵 밖(청크 없음 → 검은 화면)을 비추지 않을
  // 만큼 가장자리에서 떨어진 지점 — 64x64 맵 한가운데 쪽.
  private static final float SPAWN_TILE_X = 25.5f;
  private static final float SPAWN_TILE_Y = 25.5f;

  // 단계6 임시 허수아비(docs/m0-prompts.md) — 플레이어에서 3타일 동쪽, 화면 안(가상 640px=20타일)에 바로 보인다.
  private static final float DUMMY_TILE_X = SPAWN_TILE_X + 3f;
  private static final float DUMMY_TILE_Y = SPAWN_TILE_Y;
  private static final int DUMMY_MAX_HP = 100;

  private final WildbondGame game;
  private final GameConfig config;

  public BootScreen(WildbondGame game, GameConfig config) {
    this.game = game;
    this.config = config;
  }

  @Override
  public void show() {
    GameData gameData = loadGameData();
    FileChunkLoader chunkLoader = new FileChunkLoader(config.chunksDir());
    ChunkTileMap tileMap = new ChunkTileMap(chunkLoader);
    Sim sim = new Sim(gameData, tileMap, WORLD_SEED);

    float spawnX = SPAWN_TILE_X * RenderConstants.TILE_PX;
    float spawnY = SPAWN_TILE_Y * RenderConstants.TILE_PX;
    float dummyX = DUMMY_TILE_X * RenderConstants.TILE_PX;
    float dummyY = DUMMY_TILE_Y * RenderConstants.TILE_PX;
    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(spawnX, spawnY),
            new Command.SpawnDummy(dummyX, dummyY, Element.NONE, DUMMY_MAX_HP, 0, 0, 1)));

    SimView view = sim.view();
    int playerId = view.stableIdAt(0);

    InputMapper inputMapper = new InputMapper();
    inputMapper.setControlledEntity(playerId);

    ViewState viewState = new ViewState();
    viewState.capture(view);

    HitEffects hitEffects = new HitEffects();
    sim.subscribe(
        event -> {
          if (event instanceof Damaged damaged) {
            hitEffects.onDamaged(damaged);
          }
        });

    Texture tileset =
        new Texture(Gdx.files.absolute(config.tilesetFile().toAbsolutePath().toString()));

    game.setScreen(
        new PlayScreen(inputMapper, viewState, sim, chunkLoader, tileset, playerId, hitEffects));
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
