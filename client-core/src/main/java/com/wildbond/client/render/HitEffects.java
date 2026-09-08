package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.sim.events.Damaged;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Damaged 이벤트 → 떠오르는 숫자 + 3프레임 타격 플래시 (docs/m0-prompts.md 단계6), LevelUp 이벤트 → 떠오르는 "LEVEL UP" 글자.
 * sim.subscribe 로 받은 이벤트를 {@link #onDamaged}/{@link #onLevelUp}에 넘기면 되고, 나머지는 매 렌더 프레임 {@link
 * #update}/{@link #renderNumbers}가 처리한다.
 *
 * <p>플래시는 프레임 단위(렌더 프레임)다 — 틱(50ms)과는 별개로, {@link EntityRenderer}가 이 클래스의 {@link #isFlashing}을 물어보고
 * 해당 엔티티만 붉게 그린다.
 */
public final class HitEffects implements Disposable {

  private static final float FLOAT_DURATION_SECONDS = 0.8f;
  private static final float LEVEL_UP_DURATION_SECONDS = 1.6f;
  private static final float FLOAT_RISE_PX = 24f;
  private static final int FLASH_FRAMES = 3;
  private static final Color LEVEL_UP_COLOR = Color.valueOf("7CFC9AFF");

  private static final class FloatingText {
    float x;
    float y;
    float age;
    float duration;
    String label;
    Color color;
  }

  private final List<FloatingText> texts = new ArrayList<>();
  private final Map<Integer, Integer> flashFramesRemaining = new HashMap<>();
  private final BitmapFont font = new BitmapFont(true); // yDown 월드 카메라와 맞춘다(T-006 과 같은 이유).

  public void onDamaged(Damaged event) {
    FloatingText number = new FloatingText();
    number.x = event.x();
    number.y = event.y();
    number.duration = FLOAT_DURATION_SECONDS;
    number.label = Integer.toString(event.amount());
    number.color = event.critical() ? Color.GOLD : Color.WHITE;
    texts.add(number);
    flashFramesRemaining.put(event.entityId(), FLASH_FRAMES);
  }

  /** 레벨업 — 이벤트에는 위치가 없으므로 호출하는 쪽이 플레이어 위치를 넘긴다. 문자열은 ASCII (T-009). */
  public void onLevelUp(int level, float x, float y) {
    FloatingText text = new FloatingText();
    text.x = x - 30f;
    text.y = y - 40f;
    text.duration = LEVEL_UP_DURATION_SECONDS;
    text.label = "LEVEL UP! Lv " + level;
    text.color = LEVEL_UP_COLOR;
    texts.add(text);
  }

  /** PlayScreen 이 렌더 프레임마다 한 번 호출 — 떠오르는 글자의 수명과 플래시 프레임을 줄인다. */
  public void update(float deltaSeconds) {
    Iterator<FloatingText> it = texts.iterator();
    while (it.hasNext()) {
      FloatingText text = it.next();
      text.age += deltaSeconds;
      if (text.age >= text.duration) {
        it.remove();
      }
    }

    Iterator<Map.Entry<Integer, Integer>> flashIt = flashFramesRemaining.entrySet().iterator();
    while (flashIt.hasNext()) {
      Map.Entry<Integer, Integer> entry = flashIt.next();
      int remaining = entry.getValue() - 1;
      if (remaining <= 0) {
        flashIt.remove();
      } else {
        entry.setValue(remaining);
      }
    }
  }

  public boolean isFlashing(int entityId) {
    return flashFramesRemaining.containsKey(entityId);
  }

  public void renderNumbers(SpriteBatch batch, Matrix4 projection) {
    if (texts.isEmpty()) {
      return;
    }
    batch.setProjectionMatrix(projection);
    batch.begin();
    for (FloatingText text : texts) {
      float progress = text.age / text.duration;
      float alpha = 1f - progress;
      font.setColor(text.color.r, text.color.g, text.color.b, alpha);

      float drawX = text.x;
      float drawY = text.y - FLOAT_RISE_PX * progress;
      font.draw(batch, text.label, drawX, drawY);
    }
    batch.end();
    font.setColor(Color.WHITE);
  }

  @Override
  public void dispose() {
    font.dispose();
  }
}
