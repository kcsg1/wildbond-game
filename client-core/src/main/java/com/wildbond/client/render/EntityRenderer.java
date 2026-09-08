package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.client.ViewState;
import com.wildbond.data.GameData;
import com.wildbond.data.chunk.ChunkObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ViewState 를 Y-정렬해 그린다 (docs/architecture.md §5.3). 스프라이트는 {@link PlaceholderSprites} 가 프로그램으로 그린
 * 임시 그림이다 — 플레이어 32×48 사람, 팰은 종 속성 색 크리처(대형은 64×64 로 확대), 포획구는 그림자 + 가상 높이 z.
 *
 * <p>바라보는 방향과 걷기 프레임은 sim 이 아니라 <b>틱 사이 위치 변화</b>에서 뽑는다 — 렌더 전용 정보라 sim 상태를 늘리지 않는다(§6 규칙 3).
 * prev→cur 를 alpha 로 보간해 60fps 에서 20Hz 틱이 떨리지 않게 한다.
 */
public final class EntityRenderer implements Disposable {

  /** 이 값보다 적게 움직였으면 정지로 본다(틱당 픽셀). */
  private static final float MOVE_EPSILON_PX = 0.05f;

  /** 걷기 프레임 하나가 유지되는 시간. */
  private static final float WALK_FRAME_SECONDS = 0.16f;

  /** 피격 3프레임 동안 입히는 붉은 틴트 (단계 6 의 "흰 텍스처로 교체"를 스프라이트에 맞게 바꾼 것). */
  private static final Color HIT_TINT = new Color(1f, 0.45f, 0.45f, 1f);

  private final GameData gameData;
  private final PlaceholderSprites sprites;

  /** 머리 위 HP 바와 쓰러짐 연출에 쓰는 1×1 흰 픽셀. */
  private final Texture pixel = makePixel();

  private final List<ViewState.Snapshot> sortBuffer = new ArrayList<>();

  /** 엔티티별 마지막으로 바라본 방향과 걷기 타이머 — 멈춰도 방향은 유지한다. */
  private final Map<Integer, Integer> facing = new HashMap<>();

  private final Map<Integer, Float> walkTimer = new HashMap<>();

  private int lastRenderCalls;
  private float coinBobPhase;

  public EntityRenderer(GameData gameData, PlaceholderSprites sprites) {
    this.gameData = gameData;
    this.sprites = sprites;
  }

  private static Texture makePixel() {
    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    Texture texture = new Texture(pixmap);
    pixmap.dispose();
    return texture;
  }

  /**
   * 엔티티와 배치 오브젝트(나무·바위)를 함께 Y-정렬해 한 번에 그린다 — 그래야 플레이어가 나무 뒤로 걸어가면 가려진다.
   *
   * @param props {@link ChunkRenderer#props()} — 이미 tileY 오름차순으로 정렬돼 있다
   */
  public void render(
      SpriteBatch batch,
      Matrix4 projection,
      ViewState viewState,
      List<ChunkObject> props,
      float alpha,
      float deltaSeconds,
      HitEffects hitEffects,
      CaptureEffects captureEffects) {
    coinBobPhase += deltaSeconds * 4f;
    sortBuffer.clear();
    sortBuffer.addAll(viewState.current());
    sortBuffer.sort(Comparator.comparingDouble(ViewState.Snapshot::y));

    batch.setProjectionMatrix(projection);
    batch.begin();

    // 둘 다 이미 정렬돼 있으므로 두 포인터로 합치며 그린다(매 프레임 합쳐 정렬하지 않는다).
    int propIndex = 0;
    for (ViewState.Snapshot snapshot : sortBuffer) {
      float prevX = viewState.prevX(snapshot.id(), snapshot.x());
      float prevY = viewState.prevY(snapshot.id(), snapshot.y());
      float x = lerp(prevX, snapshot.x(), alpha);
      float y = lerp(prevY, snapshot.y(), alpha);
      float z = lerp(viewState.prevZ(snapshot.id(), snapshot.z()), snapshot.z(), alpha);

      while (propIndex < props.size() && propAnchorY(props.get(propIndex)) <= y) {
        drawProp(batch, props.get(propIndex));
        propIndex++;
      }

      int frame =
          advanceGait(snapshot.id(), snapshot.x() - prevX, snapshot.y() - prevY, deltaSeconds);
      boolean flashing = hitEffects.isFlashing(snapshot.id());
      batch.setColor(flashing ? HIT_TINT : Color.WHITE);
      drawEntity(batch, snapshot, x, y, z, frame, captureEffects);
      batch.setColor(Color.WHITE);
    }
    while (propIndex < props.size()) {
      drawProp(batch, props.get(propIndex));
      propIndex++;
    }

    batch.end();
    lastRenderCalls = batch.renderCalls;

    forgetGoneEntities(viewState);
  }

  /** 오브젝트의 정렬 기준 y — 밑동이 놓인 타일의 아래 모서리. */
  private static float propAnchorY(ChunkObject prop) {
    return (prop.tileY() + 1) * (float) RenderConstants.TILE_PX;
  }

  private void drawProp(SpriteBatch batch, ChunkObject prop) {
    float anchorX = prop.tileX() * (float) RenderConstants.TILE_PX + RenderConstants.TILE_PX / 2f;
    float anchorY = propAnchorY(prop);
    switch (prop.type()) {
      case "tree" ->
          drawSprite(
              batch,
              sprites.tree(),
              anchorX,
              anchorY,
              PlaceholderSprites.TREE_W,
              PlaceholderSprites.TREE_H);
      case "resource_node" ->
          drawSprite(
              batch,
              sprites.rock(),
              anchorX,
              anchorY,
              RenderConstants.TILE_PX,
              RenderConstants.TILE_PX);
      case "house", "house_red" -> {
        // 앵커는 왼쪽 아래 칸이라, 3칸 폭의 가운데로 옮겨서 그린다.
        float houseCenterX =
            anchorX + (PlaceholderSprites.HOUSE_TILES_W - 1) * RenderConstants.TILE_PX / 2f;
        drawSprite(
            batch,
            sprites.house("house_red".equals(prop.type())),
            houseCenterX,
            anchorY,
            PlaceholderSprites.HOUSE_W,
            PlaceholderSprites.HOUSE_H);
      }
      case "fence" ->
          drawSprite(
              batch,
              sprites.fence(),
              anchorX,
              anchorY,
              RenderConstants.TILE_PX,
              RenderConstants.TILE_PX);
      default -> {
        // 모르는 오브젝트 종류는 그리지 않는다 (스폰 포인트 등 보이지 않아야 하는 것도 있다).
      }
    }
  }

  /**
   * 이번 틱의 이동량으로 방향을 갱신하고 걷기 프레임을 돌린다. 멈춰 있으면 0번 프레임(정지)으로 되돌린다.
   *
   * @return 그릴 걷기 프레임 번호
   */
  private int advanceGait(int entityId, float dx, float dy, float deltaSeconds) {
    boolean moving = Math.abs(dx) > MOVE_EPSILON_PX || Math.abs(dy) > MOVE_EPSILON_PX;
    if (!moving) {
      walkTimer.put(entityId, 0f);
      return 0;
    }
    if (Math.abs(dx) > Math.abs(dy)) {
      facing.put(entityId, dx > 0 ? PlaceholderSprites.DIR_RIGHT : PlaceholderSprites.DIR_LEFT);
    } else {
      // 월드는 yDown — y 가 커지는 쪽이 화면 아래(정면).
      facing.put(entityId, dy > 0 ? PlaceholderSprites.DIR_DOWN : PlaceholderSprites.DIR_UP);
    }
    float timer = walkTimer.getOrDefault(entityId, 0f) + deltaSeconds;
    walkTimer.put(entityId, timer);
    return (int) (timer / WALK_FRAME_SECONDS) % PlaceholderSprites.WALK_FRAMES;
  }

  /** 사라진 엔티티의 방향·타이머를 흘려보낸다 — 팰이 계속 스폰·회수되므로 놔두면 맵이 자란다. */
  private void forgetGoneEntities(ViewState viewState) {
    Iterator<Map.Entry<Integer, Integer>> it = facing.entrySet().iterator();
    while (it.hasNext()) {
      int id = it.next().getKey();
      if (viewState.snapshot(id) == null) {
        it.remove();
        walkTimer.remove(id);
      }
    }
  }

  private void drawEntity(
      SpriteBatch batch,
      ViewState.Snapshot snapshot,
      float anchorX,
      float anchorY,
      float z,
      int frame,
      CaptureEffects captureEffects) {
    switch (snapshot.kind()) {
      case PLAYER -> {
        int dir = facing.getOrDefault(snapshot.id(), PlaceholderSprites.DIR_DOWN);
        // Kenney 캐릭터에는 걷기 프레임이 없다 — 1번 프레임에 1px 들썩여 걷는 느낌만 준다.
        float bob = frame == 1 ? -1f : 0f;
        drawSprite(
            batch,
            sprites.player(dir, frame),
            anchorX,
            anchorY + bob,
            PlaceholderSprites.CHARACTER_PX,
            PlaceholderSprites.CHARACTER_PX);
      }
      case DUMMY ->
          drawSprite(
              batch,
              sprites.dummy(),
              anchorX,
              anchorY,
              PlaceholderSprites.CHARACTER_PX,
              PlaceholderSprites.CHARACTER_PX);
      case PAL -> {
        if (captureEffects.isPalHidden(snapshot.id())) {
          return; // 포획구가 흔들리는 동안에는 숨는다.
        }
        float size = palFootprint(snapshot.speciesId()) * (float) RenderConstants.TILE_PX;
        float dying = snapshot.deathProgress();
        if (dying > 0f) {
          // 쓰러지는 중 — 점점 납작해지고 흐려진다. 다 쓰러지면 동전만 남는다.
          batch.setColor(1f, 1f, 1f, Math.max(0f, 1f - dying));
          float squashed = size * Math.max(0.15f, 1f - dying);
          batch.draw(
              sprites.pal(snapshot.speciesId()),
              anchorX - size / 2f,
              anchorY - squashed,
              size,
              squashed);
          batch.setColor(Color.WHITE);
          return;
        }
        drawSprite(batch, sprites.pal(snapshot.speciesId()), anchorX, anchorY, size, size);
        drawHealthBar(batch, snapshot, anchorX, anchorY - size);
      }
      case DROP -> drawCoin(batch, anchorX, anchorY);
      case SPHERE -> drawSphere(batch, anchorX, anchorY, z);
      case UNKNOWN -> {
        // 렌더가 모르는 종류는 그리지 않는다.
      }
    }
  }

  /** 몬스터 머리 위 HP 바 — 다치지 않았으면 그리지 않는다(화면이 바로 지저분해진다). */
  private void drawHealthBar(
      SpriteBatch batch, ViewState.Snapshot snapshot, float centerX, float topY) {
    int hp = snapshot.hp();
    int maxHp = snapshot.maxHp();
    if (hp < 0 || maxHp <= 0 || hp >= maxHp) {
      return;
    }
    float width = 28f;
    float height = 4f;
    float x = centerX - width / 2f;
    float y = topY - height - 3f;

    batch.setColor(0.08f, 0.08f, 0.10f, 0.85f);
    batch.draw(pixel, x - 1f, y - 1f, width + 2f, height + 2f);
    float ratio = Math.max(0f, (float) hp / maxHp);
    batch.setColor(ratio > 0.3f ? 0.85f : 0.9f, ratio > 0.3f ? 0.25f : 0.15f, 0.25f, 1f);
    batch.draw(pixel, x, y, width * ratio, height);
    batch.setColor(Color.WHITE);
  }

  /** 바닥에 떨어진 동전 — 살짝 위아래로 흔들려 눈에 띄게 한다. */
  private void drawCoin(SpriteBatch batch, float x, float y) {
    float bob = (float) StrictMath.sin(coinBobPhase) * 2f;
    int size = 10;
    batch.setColor(0f, 0f, 0f, 0.35f);
    batch.draw(pixel, x - size / 2f, y - 3f, size, 3f);
    batch.setColor(Color.valueOf("F2C23EFF"));
    batch.draw(pixel, x - size / 2f, y - size - 3f + bob, size, size);
    batch.setColor(Color.valueOf("8C6A14FF"));
    batch.draw(pixel, x - size / 2f + 3f, y - size - 1f + bob, 4f, size - 4f);
    batch.setColor(Color.WHITE);
  }

  /** 지면에 그림자를, 그 위 z 만큼 띄워 포획구를 그린다 (§3.2 "가상 높이 z"). */
  private void drawSphere(SpriteBatch batch, float x, float y, float z) {
    int shadow = sprites.shadowPx();
    int ball = sprites.spherePx();
    batch.draw(sprites.shadow(), x - shadow / 2f, y - shadow / 2f, shadow, shadow);
    // 월드는 yDown 이므로 높이 z 만큼 위로 = y 가 작아지는 방향.
    batch.draw(sprites.sphere(), x - ball / 2f, y - z - ball, ball, ball);
  }

  private static void drawSprite(
      SpriteBatch batch,
      TextureRegion region,
      float anchorX,
      float anchorY,
      float width,
      float height) {
    batch.draw(region, anchorX - width / 2f, anchorY - height, width, height); // 발 위치 기준(§5.3)
  }

  private int palFootprint(int speciesId) {
    return speciesId < 0 ? 1 : gameData.palSpecies(speciesId).footprint();
  }

  /** 포획구 흔들림 연출 — 스프라이트를 이 클래스가 들고 있으므로 그리기도 여기서 한다. */
  public void renderCaptureShakes(SpriteBatch batch, Matrix4 projection, CaptureEffects effects) {
    if (effects.activeCount() == 0) {
      return;
    }
    int ball = sprites.spherePx();
    batch.setProjectionMatrix(projection);
    batch.begin();
    for (int i = 0; i < effects.activeCount(); i++) {
      batch.setColor(1f, 1f, 1f, effects.alpha(i));
      batch.draw(
          sprites.sphere(),
          effects.x(i) + effects.shakeOffsetX(i) - ball / 2f,
          effects.y(i) - ball,
          ball,
          ball);
    }
    batch.setColor(Color.WHITE);
    batch.end();
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  /**
   * 그 엔티티가 바라보는 방향의 조준각 ({@code Angle} 1/1024 단위). 스페이스바 공격이 마우스 대신 이 값을 쓴다 (D-17). 방향은 렌더가 이동량에서
   * 뽑아 들고 있으므로 sim 에 facing 을 추가하지 않아도 된다.
   */
  public int facingAngle(int entityId) {
    int dir = facing.getOrDefault(entityId, PlaceholderSprites.DIR_DOWN);
    if (dir == PlaceholderSprites.DIR_UP) {
      return 768; // -y (yDown 이라 화면 위)
    }
    if (dir == PlaceholderSprites.DIR_LEFT) {
      return 512; // -x
    }
    if (dir == PlaceholderSprites.DIR_RIGHT) {
      return 0; // +x
    }
    return 256; // +y (화면 아래)
  }

  public int renderCalls() {
    return lastRenderCalls;
  }

  @Override
  public void dispose() {
    // 스프라이트는 PlayScreen 이 소유한다(ChunkRenderer 등과 공유하므로 여기서 버리지 않는다).
    pixel.dispose();
  }
}
