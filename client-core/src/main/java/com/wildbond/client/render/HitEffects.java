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
 * Damaged 이벤트 → 떠오르는 숫자 + 3프레임 타격 플래시 (docs/m0-prompts.md 단계6). sim.subscribe 로 받은 이벤트를 {@link
 * #onDamaged}에 넘기면 되고, 나머지는 매 렌더 프레임 {@link #update}/{@link #renderNumbers}가 처리한다.
 *
 * <p>플래시는 프레임 단위(렌더 프레임)다 — 틱(50ms)과는 별개로, {@link EntityRenderer}가 이 클래스의 {@link #isFlashing}을 물어보고
 * 해당 엔티티만 흰색으로 그린다.
 */
public final class HitEffects implements Disposable {

  private static final float FLOAT_DURATION_SECONDS = 0.8f;
  private static final float FLOAT_RISE_PX = 24f;
  private static final int FLASH_FRAMES = 3;

  private static final class FloatingNumber {
    float x;
    float y;
    float age;
    int amount;
    boolean critical;
  }

  private final List<FloatingNumber> numbers = new ArrayList<>();
  private final Map<Integer, Integer> flashFramesRemaining = new HashMap<>();
  private final BitmapFont font = new BitmapFont(true); // yDown 월드 카메라와 맞춘다(T-006 과 같은 이유).
  private final StringBuilder text = new StringBuilder(8);

  public void onDamaged(Damaged event) {
    FloatingNumber number = new FloatingNumber();
    number.x = event.x();
    number.y = event.y();
    number.amount = event.amount();
    number.critical = event.critical();
    numbers.add(number);
    flashFramesRemaining.put(event.entityId(), FLASH_FRAMES);
  }

  /** PlayScreen 이 렌더 프레임마다 한 번 호출 — 떠오르는 숫자의 수명과 플래시 프레임을 줄인다. */
  public void update(float deltaSeconds) {
    Iterator<FloatingNumber> it = numbers.iterator();
    while (it.hasNext()) {
      FloatingNumber number = it.next();
      number.age += deltaSeconds;
      if (number.age >= FLOAT_DURATION_SECONDS) {
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
    if (numbers.isEmpty()) {
      return;
    }
    batch.setProjectionMatrix(projection);
    batch.begin();
    for (FloatingNumber number : numbers) {
      float progress = number.age / FLOAT_DURATION_SECONDS;
      float alpha = 1f - progress;
      font.setColor(
          number.critical ? Color.GOLD.r : Color.WHITE.r,
          number.critical ? Color.GOLD.g : Color.WHITE.g,
          number.critical ? Color.GOLD.b : Color.WHITE.b,
          alpha);

      text.setLength(0);
      text.append(number.amount);

      float drawX = number.x;
      float drawY = number.y - FLOAT_RISE_PX * progress;
      font.draw(batch, text, drawX, drawY);
    }
    batch.end();
    font.setColor(Color.WHITE);
  }

  @Override
  public void dispose() {
    font.dispose();
  }
}
