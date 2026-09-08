package com.wildbond.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.wildbond.client.GameConfig;
import com.wildbond.client.GameLoop;
import com.wildbond.client.InputMapper;
import com.wildbond.client.ViewState;
import com.wildbond.client.render.CaptureEffects;
import com.wildbond.client.render.ChunkRenderer;
import com.wildbond.client.render.DebugOverlay;
import com.wildbond.client.render.EntityRenderer;
import com.wildbond.client.render.GameCamera;
import com.wildbond.client.render.HitEffects;
import com.wildbond.client.render.PartyHud;
import com.wildbond.client.render.PlaceholderSprites;
import com.wildbond.client.render.StatusHud;
import com.wildbond.client.world.ZoneRuntime;
import com.wildbond.data.GameData;
import com.wildbond.sim.Angle;
import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.events.PalCaptureFailed;
import com.wildbond.sim.events.PalCaptured;
import java.util.function.IntConsumer;

/**
 * 메인 플레이 화면 — 존의 sim 을 고정 틱으로 돌리고(GameLoop), 청크·엔티티를 그리고, F3 로 디버그 오버레이를 켠다 (docs/architecture.md
 * §5.2~§5.4).
 *
 * <p>존을 넘어가면({@link ZoneRuntime#checkTransition}) sim 이 새로 만들어지므로, 그 sim 에 매달려 있는 것들
 * (GameLoop·ChunkRenderer·ViewState·이벤트 구독)을 전부 다시 붙인다 (D-16).
 */
public final class PlayScreen implements Screen {

  // assets/tilesets/placeholder.png — 앞 5칸이 Tile.csv id 1..5(grass,dirt,water,cliff,sand),
  // 뒤 7칸은 렌더 전용(잔디 변형 3 + 물가 경계 4). ChunkRenderer 가 어떤 칸을 언제 쓸지 정한다.
  private static final int TILE_COLUMNS = 12;

  private final ZoneRuntime zoneRuntime;
  private final ViewState viewState;
  private final InputMapper inputMapper;

  private final Texture tileset;
  private final PlaceholderSprites sprites;
  private final EntityRenderer entityRenderer;
  private final DebugOverlay debugOverlay;
  private final HitEffects hitEffects;
  private final CaptureEffects captureEffects;
  private final PartyHud partyHud;
  private final StatusHud statusHud;

  private final SpriteBatch batch = new SpriteBatch();
  private final GameCamera gameCamera = new GameCamera();
  private final OrthographicCamera uiCamera = new OrthographicCamera();

  private GameLoop gameLoop;
  private ChunkRenderer chunkRenderer;
  private int playerId;

  private boolean showDebug = true;

  // 플레이어가 죽어 월드에서 제거되면(§4.1 Dead 5초 유예 후) ViewState 에서 사라진다. 그때 0,0 으로 떨어지면
  // 청크가 없는 검은 화면이 되므로 마지막으로 본 위치를 그대로 유지한다. 사망·부활 처리 자체는 M0 범위 밖이다(§12).
  private float lastPlayerX;
  private float lastPlayerY;

  public PlayScreen(
      InputMapper inputMapper,
      ViewState viewState,
      ZoneRuntime zoneRuntime,
      GameData gameData,
      GameConfig config,
      Texture tileset,
      HitEffects hitEffects,
      CaptureEffects captureEffects) {
    this.inputMapper = inputMapper;
    this.viewState = viewState;
    this.zoneRuntime = zoneRuntime;
    this.tileset = tileset;
    this.sprites = new PlaceholderSprites(config.tinyTownSheet(), config.tinyDungeonSheet());
    this.entityRenderer = new EntityRenderer(gameData, sprites);
    this.debugOverlay = new DebugOverlay();
    this.hitEffects = hitEffects;
    this.captureEffects = captureEffects;
    this.partyHud = new PartyHud(gameData);
    this.statusHud = new StatusHud();
    attachToZone();
  }

  /** 새 존의 sim 에 루프·렌더러·구독을 다시 건다. 존 전환 때마다 호출된다. */
  private void attachToZone() {
    if (chunkRenderer != null) {
      chunkRenderer.dispose();
    }
    playerId = zoneRuntime.playerId();
    inputMapper.setControlledEntity(playerId);
    gameLoop = new GameLoop(zoneRuntime.sim(), inputMapper, viewState);
    chunkRenderer = new ChunkRenderer(tileset, TILE_COLUMNS, zoneRuntime.chunkLoader());
    zoneRuntime
        .sim()
        .subscribe(
            event -> {
              switch (event) {
                case Damaged damaged -> hitEffects.onDamaged(damaged);
                case PalCaptured captured -> captureEffects.onCaptured(captured);
                case PalCaptureFailed failed -> captureEffects.onCaptureFailed(failed);
                default -> {
                  // 이동·스폰·드롭 이벤트는 렌더가 ViewState 로 이미 보고 있다.
                }
              }
            });
    viewState.capture(zoneRuntime.sim().view());
    lastPlayerX = viewState.curX(playerId, lastPlayerX);
    lastPlayerY = viewState.curY(playerId, lastPlayerY);
  }

  @Override
  public void show() {}

  @Override
  public void render(float delta) {
    if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
      showDebug = !showDebug;
    }

    // getWidth/getHeight 는 논리(포인트) 크기다 — HiDPI 에서 실제 프레임버퍼와 다를 수 있어
    // glViewport 에는 반드시 getBackBufferWidth/Height(실제 픽셀)를 써야 한다.
    int windowWidth = Gdx.graphics.getBackBufferWidth();
    int windowHeight = Gdx.graphics.getBackBufferHeight();
    gameCamera.resize(windowWidth, windowHeight);
    uiCamera.setToOrtho(true, windowWidth, windowHeight);

    float alpha = gameLoop.advance(delta);
    if (zoneRuntime.checkTransition()) {
      attachToZone();
      alpha = 0f;
    }

    float playerX =
        interpolate(
            viewState.prevX(playerId, lastPlayerX), viewState.curX(playerId, lastPlayerX), alpha);
    float playerY =
        interpolate(
            viewState.prevY(playerId, lastPlayerY), viewState.curY(playerId, lastPlayerY), alpha);
    lastPlayerX = playerX;
    lastPlayerY = playerY;

    gameCamera.trackPosition(playerX, playerY);
    chunkRenderer.update(playerX, playerY);
    handleAimedInput(playerX, playerY);
    hitEffects.update(delta);
    captureEffects.update(delta);

    Gdx.gl.glViewport(0, 0, windowWidth, windowHeight);
    ScreenUtils.clear(0f, 0f, 0f, 1f);

    Gdx.gl.glViewport(
        gameCamera.viewportX(),
        gameCamera.viewportY(),
        gameCamera.viewportWidth(),
        gameCamera.viewportHeight());
    chunkRenderer.render(gameCamera.raw().combined);
    entityRenderer.render(
        batch,
        gameCamera.raw().combined,
        viewState,
        chunkRenderer.props(),
        alpha,
        delta,
        hitEffects,
        captureEffects);
    entityRenderer.renderCaptureShakes(batch, gameCamera.raw().combined, captureEffects);
    hitEffects.renderNumbers(batch, gameCamera.raw().combined);

    Gdx.gl.glViewport(0, 0, windowWidth, windowHeight);
    partyHud.render(batch, uiCamera.combined, viewState, windowHeight);
    statusHud.render(
        batch,
        uiCamera.combined,
        viewState,
        playerId,
        windowWidth,
        windowHeight,
        zoneRuntime.zone().name());

    if (showDebug) {
      int drawCalls = chunkRenderer.renderCalls() + entityRenderer.renderCalls();
      DebugOverlay.Stats stats =
          new DebugOverlay.Stats(
              Gdx.graphics.getFramesPerSecond(),
              gameLoop.tick(),
              gameLoop.averageTickMillis(),
              gameLoop.maxTickMillis(),
              gameLoop.stateHash(),
              drawCalls,
              chunkRenderer.loadedChunkCount());
      debugOverlay.render(batch, uiCamera.combined, stats);
    }
  }

  private static float interpolate(float prev, float cur, float alpha) {
    return prev + (cur - prev) * alpha;
  }

  /**
   * 스페이스바·좌클릭 = 근접, 우클릭 = 원거리, 숫자 키 1 = 포획구 (docs/architecture.md D-17).
   *
   * <p>{@code isKeyJustPressed}/{@code isButtonJustPressed} 는 렌더 프레임 하나에만 참이라 여기서 한 번만 읽고
   * InputMapper 의 래치로 넘긴다 — 고정 틱 루프 안(drain())에서 직접 읽으면 프레임당 여러 틱이 도는 경우 놓치거나 중복될 수 있다.
   */
  private void handleAimedInput(float playerX, float playerY) {
    if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
      // 스페이스바는 마우스가 아니라 바라보는 방향으로 친다.
      inputMapper.queueMeleeSkill(entityRenderer.facingAngle(playerId));
    }
    if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
      queueAimed(inputMapper::queueMeleeSkill, playerX, playerY);
    }
    if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
      queueAimed(inputMapper::queueRangedSkill, playerX, playerY);
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
      queueAimed(inputMapper::queueThrowSphere, playerX, playerY);
    }
  }

  private void queueAimed(IntConsumer sink, float playerX, float playerY) {
    Vector3 worldPoint = gameCamera.unproject(Gdx.input.getX(), Gdx.input.getY());
    float dx = worldPoint.x - playerX;
    float dy = worldPoint.y - playerY;
    int aimAngle = Angle.fromRadians((float) StrictMath.atan2(dy, dx));
    sink.accept(aimAngle);
  }

  @Override
  public void resize(int width, int height) {
    // 전달된 값이 아니라 실제 프레임버퍼 픽셀 크기를 다시 물어본다 — HiDPI 불일치 방지.
    int backBufferWidth = Gdx.graphics.getBackBufferWidth();
    int backBufferHeight = Gdx.graphics.getBackBufferHeight();
    gameCamera.resize(backBufferWidth, backBufferHeight);
    uiCamera.setToOrtho(true, backBufferWidth, backBufferHeight);
  }

  @Override
  public void pause() {}

  @Override
  public void resume() {}

  @Override
  public void hide() {}

  @Override
  public void dispose() {
    chunkRenderer.dispose();
    entityRenderer.dispose();
    debugOverlay.dispose();
    hitEffects.dispose();
    partyHud.dispose();
    statusHud.dispose();
    sprites.dispose();
    batch.dispose();
    tileset.dispose();
  }
}
