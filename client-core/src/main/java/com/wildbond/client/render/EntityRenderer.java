package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.client.ViewState;
import com.wildbond.sim.EntityKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ViewState 를 Y-정렬해 그린다 (docs/architecture.md §5.3). 플레이어는 32×48 색 블록(임시), 발 위치를 기준으로 앵커한다.
 * prev→cur 를 alpha 로 보간해 60fps 에서 20Hz 틱이 떨리지 않게 한다.
 */
public final class EntityRenderer implements Disposable {

  private final Texture playerTexture;
  private final Texture dummyTexture;
  private final Texture flashTexture;
  private final List<ViewState.Snapshot> sortBuffer = new ArrayList<>();

  private int lastRenderCalls;

  public EntityRenderer() {
    playerTexture = solidTexture("3F7FE0FF");
    dummyTexture = solidTexture("A0522DFF");
    flashTexture = solidTexture("FFFFFFFF");
  }

  private static Texture solidTexture(String hexColor) {
    Pixmap pixmap =
        new Pixmap(
            (int) RenderConstants.PLAYER_WIDTH_PX,
            (int) RenderConstants.PLAYER_HEIGHT_PX,
            Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.valueOf(hexColor));
    pixmap.fill();
    Texture texture = new Texture(pixmap);
    pixmap.dispose();
    return texture;
  }

  public void render(
      SpriteBatch batch,
      Matrix4 projection,
      ViewState viewState,
      float alpha,
      HitEffects hitEffects) {
    sortBuffer.clear();
    sortBuffer.addAll(viewState.current());
    sortBuffer.sort(Comparator.comparingDouble(ViewState.Snapshot::y));

    batch.setProjectionMatrix(projection);
    batch.begin();
    for (ViewState.Snapshot snapshot : sortBuffer) {
      float prevX = viewState.prevX(snapshot.id(), snapshot.x());
      float prevY = viewState.prevY(snapshot.id(), snapshot.y());
      float x = lerp(prevX, snapshot.x(), alpha);
      float y = lerp(prevY, snapshot.y(), alpha);
      boolean flashing = hitEffects.isFlashing(snapshot.id());
      drawEntity(batch, snapshot.kind(), x, y, flashing);
    }
    batch.end();
    lastRenderCalls = batch.renderCalls;
  }

  private void drawEntity(
      SpriteBatch batch, EntityKind kind, float anchorX, float anchorY, boolean flashing) {
    Texture texture =
        switch (kind) {
          case PLAYER -> playerTexture;
          case DUMMY -> dummyTexture;
          case UNKNOWN -> null; // 단계 7 에서 PAL 이 추가된다
        };
    if (texture == null) {
      return;
    }
    if (flashing) {
      texture = flashTexture;
    }
    float drawX = anchorX - RenderConstants.PLAYER_WIDTH_PX / 2f;
    float drawY = anchorY - RenderConstants.PLAYER_HEIGHT_PX; // 발 위치 기준(§5.3)
    batch.draw(
        texture, drawX, drawY, RenderConstants.PLAYER_WIDTH_PX, RenderConstants.PLAYER_HEIGHT_PX);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  public int renderCalls() {
    return lastRenderCalls;
  }

  @Override
  public void dispose() {
    playerTexture.dispose();
    dummyTexture.dispose();
    flashTexture.dispose();
  }
}
