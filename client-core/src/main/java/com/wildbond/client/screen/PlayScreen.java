package com.wildbond.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.wildbond.client.GameLoop;
import com.wildbond.client.InputMapper;
import com.wildbond.client.ViewState;
import com.wildbond.client.map.FileChunkLoader;
import com.wildbond.client.render.CaptureEffects;
import com.wildbond.client.render.ChunkRenderer;
import com.wildbond.client.render.DebugOverlay;
import com.wildbond.client.render.EntityRenderer;
import com.wildbond.client.render.GameCamera;
import com.wildbond.client.render.HitEffects;
import com.wildbond.client.render.PartyHud;
import com.wildbond.data.GameData;
import com.wildbond.sim.Angle;
import com.wildbond.sim.Sim;
import java.util.function.IntConsumer;

/**
 * 메인 플레이 화면 — sim 을 고정 틱으로 돌리고(GameLoop), 청크·엔티티를 그리고, F3 로 디버그 오버레이를 켠다 (docs/architecture.md
 * §5.2~§5.4).
 */
public final class PlayScreen implements Screen {

  // assets/tilesets/placeholder.png (grass,dirt,water,cliff,sand) — docs/m0-prompts.md 단계4 산출물.
  private static final int TILE_COLUMNS = 5;

  private final GameLoop gameLoop;
  private final ViewState viewState;
  private final int playerId;

  private final Texture tileset;
  private final ChunkRenderer chunkRenderer;
  private final EntityRenderer entityRenderer;
  private final DebugOverlay debugOverlay;
  private final HitEffects hitEffects;
  private final CaptureEffects captureEffects;
  private final PartyHud partyHud;
  private final InputMapper inputMapper;

  private final SpriteBatch batch = new SpriteBatch();
  private final GameCamera gameCamera = new GameCamera();
  private final OrthographicCamera uiCamera = new OrthographicCamera();

  private boolean showDebug = true;

  // 플레이어가 죽어 월드에서 제거되면(§4.1 Dead 5초 유예 후) ViewState 에서 사라진다. 그때 0,0 으로 떨어지면
  // 청크가 없는 검은 화면이 되므로 마지막으로 본 위치를 그대로 유지한다. 사망·부활 처리 자체는 M0 범위 밖이다(§12).
  private float lastPlayerX;
  private float lastPlayerY;

  public PlayScreen(
      InputMapper inputMapper,
      ViewState viewState,
      Sim sim,
      GameData gameData,
      FileChunkLoader chunkLoader,
      Texture tileset,
      int playerId,
      HitEffects hitEffects,
      CaptureEffects captureEffects) {
    this.viewState = viewState;
    this.gameLoop = new GameLoop(sim, inputMapper, viewState);
    this.playerId = playerId;
    this.tileset = tileset;
    this.chunkRenderer = new ChunkRenderer(tileset, TILE_COLUMNS, chunkLoader);
    this.entityRenderer = new EntityRenderer(gameData);
    this.debugOverlay = new DebugOverlay();
    this.hitEffects = hitEffects;
    this.captureEffects = captureEffects;
    this.partyHud = new PartyHud(gameData);
    this.inputMapper = inputMapper;
    // 카메라/뷰포트 크기는 여기서 미리 재지 않는다 — render() 가 매 프레임 실제 프레임버퍼 크기로 다시
    // 계산한다(resize() 콜백 타이밍에 기대지 않는 안전한 선택, 비용도 덧셈 몇 번뿐).
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
   * 좌클릭=근접, 우클릭=원거리, 숫자 키 1=포획구 (§5.4, docs/m0-prompts.md 단계7). {@code isButtonJustPressed}/{@code
   * isKeyJustPressed}는 실제 렌더 프레임 하나에만 참이라 여기서 한 번만 읽고 InputMapper 의 래치로 넘긴다 — 고정 틱 루프 안(drain())에서
   * 직접 읽으면 프레임당 여러 틱이 도는 경우 놓치거나 중복될 수 있다.
   */
  private void handleAimedInput(float playerX, float playerY) {
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
    batch.dispose();
    tileset.dispose();
  }
}
