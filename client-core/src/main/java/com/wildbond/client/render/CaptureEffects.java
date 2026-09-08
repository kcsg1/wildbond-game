package com.wildbond.client.render;

import com.wildbond.sim.events.PalCaptureFailed;
import com.wildbond.sim.events.PalCaptured;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 포획 연출의 <b>상태</b>만 들고 있는다 (docs/m0-prompts.md 단계7) — 낙하 지점에서 포획구가 흔들림 횟수만큼 흔들리는 동안 팰을 감췄다가, 성공이면
 * 그대로 사라지고 실패면 다시 나타난다(튀어나온다).
 *
 * <p>흔들림 횟수는 §3.2 대로 sim 이 정해서 이벤트로 보내 준다 — 여기서는 그대로 연출만 한다. 성공 뒤에도 팰은 월드에 남아 주인을 따라다니므로(파티 팰),
 * "사라짐"은 흔들리는 동안만 그리지 않는 연출이다.
 *
 * <p>그리기는 스프라이트를 소유한 {@link EntityRenderer#renderCaptureShakes} 가 맡는다 — 이 클래스는 GL 자원을 갖지 않아
 * BootScreen 처럼 렌더러보다 먼저 만들어지는 곳에서도 안전하다.
 */
public final class CaptureEffects {

  private static final float SHAKE_SECONDS = 0.35f;
  private static final float SHAKE_AMPLITUDE_PX = 4f;

  private static final class Shake {
    int palEntityId;
    float x;
    float y;
    int shakes;
    float age;
    boolean success;
  }

  private final List<Shake> shakes = new ArrayList<>();

  public void onCaptured(PalCaptured event) {
    add(event.palEntityId(), event.x(), event.y(), event.shakes(), true);
  }

  public void onCaptureFailed(PalCaptureFailed event) {
    add(event.palEntityId(), event.x(), event.y(), event.shakes(), false);
  }

  private void add(int palEntityId, float x, float y, int shakeCount, boolean success) {
    Shake shake = new Shake();
    shake.palEntityId = palEntityId;
    shake.x = x;
    shake.y = y;
    shake.shakes = Math.max(1, shakeCount);
    shake.success = success;
    shakes.add(shake);
  }

  /** PlayScreen 이 렌더 프레임마다 한 번 호출한다. */
  public void update(float deltaSeconds) {
    Iterator<Shake> it = shakes.iterator();
    while (it.hasNext()) {
      Shake shake = it.next();
      shake.age += deltaSeconds;
      if (shake.age >= shake.shakes * SHAKE_SECONDS) {
        it.remove();
      }
    }
  }

  /** 흔들리는 동안에는 팰을 그리지 않는다 — 포획구에 들어가 있는 연출. */
  public boolean isPalHidden(int entityId) {
    for (int i = 0; i < shakes.size(); i++) {
      if (shakes.get(i).palEntityId == entityId) {
        return true;
      }
    }
    return false;
  }

  public int activeCount() {
    return shakes.size();
  }

  public float x(int index) {
    return shakes.get(index).x;
  }

  public float y(int index) {
    return shakes.get(index).y;
  }

  /** 흔들림 한 번당 좌우로 한 번 왕복한다. */
  public float shakeOffsetX(int index) {
    Shake shake = shakes.get(index);
    float phase = shake.age / SHAKE_SECONDS;
    return SHAKE_AMPLITUDE_PX * (float) StrictMath.sin(phase * 2 * StrictMath.PI);
  }

  /** 실패한 포획구는 흔들림이 끝나면서 서서히 사라진다(팰이 튀어나온다). */
  public float alpha(int index) {
    Shake shake = shakes.get(index);
    if (shake.success) {
      return 1f;
    }
    float progress = shake.age / (shake.shakes * SHAKE_SECONDS);
    return Math.max(0f, 1f - progress);
  }
}
