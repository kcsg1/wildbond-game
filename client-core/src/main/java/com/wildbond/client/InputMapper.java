package com.wildbond.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.wildbond.sim.Command;
import com.wildbond.sim.Dir8;
import java.util.ArrayList;
import java.util.List;

/**
 * WASD/화살표 → MoveInput(dir8), Shift 달리기, 스페이스바/좌클릭 → 근접, 우클릭 → 원거리 UseSkill (docs/architecture.md
 * §5.4, D-17). 매 틱 현재 키 상태를 그대로 명령으로 바꾼다 — 키를 떼면 다음 틱에 NONE 이 나가 멈춘다.
 *
 * <p>공격은 다르다 — {@code isKeyJustPressed}/{@code isButtonJustPressed} 는 실제 렌더 프레임 하나에만 참이고, 그 프레임 안에서
 * 고정 틱이 0번 또는 여러 번 돌 수 있다. 그래서 감지는 PlayScreen 이 렌더 프레임마다 한 번만 하고 {@link #queueMeleeSkill}/{@link
 * #queueRangedSkill} 로 여기 넘겨 "다음 drain() 호출 한 번"에만 소비되는 래치로 저장한다.
 */
public final class InputMapper {

  private int controlledEntityId = -1;

  private boolean meleePending;
  private int meleeAimAngle;
  private boolean rangedPending;
  private int rangedAimAngle;

  public void setControlledEntity(int stableId) {
    this.controlledEntityId = stableId;
  }

  /** 스페이스바 또는 좌클릭 근접 공격 (docs/architecture.md D-17). */
  public void queueMeleeSkill(int aimAngle) {
    meleePending = true;
    meleeAimAngle = aimAngle;
  }

  /** PlayScreen 이 실제 렌더 프레임마다(고정 틱과 별개로) 우클릭을 감지했을 때 호출한다. */
  public void queueRangedSkill(int aimAngle) {
    rangedPending = true;
    rangedAimAngle = aimAngle;
  }

  /** GameLoop 이 틱마다 한 번 호출한다. */
  public List<Command> drain() {
    if (controlledEntityId < 0) {
      return List.of();
    }
    boolean up = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean down = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
    boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean right =
        Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
    boolean run =
        Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

    int dx = (right ? 1 : 0) - (left ? 1 : 0);
    int dy = (down ? 1 : 0) - (up ? 1 : 0);
    Dir8 dir = resolveDir(dx, dy);

    List<Command> commands = new ArrayList<>(3);
    commands.add(new Command.MoveInput(controlledEntityId, dir, run));
    if (meleePending) {
      commands.add(
          new Command.UseSkill(controlledEntityId, CombatBindings.MELEE_SKILL_ID, meleeAimAngle));
      meleePending = false;
    }
    if (rangedPending) {
      commands.add(
          new Command.UseSkill(controlledEntityId, CombatBindings.RANGED_SKILL_ID, rangedAimAngle));
      rangedPending = false;
    }
    return commands;
  }

  private static Dir8 resolveDir(int dx, int dy) {
    for (Dir8 candidate : Dir8.values()) {
      if (candidate.dx() == dx && candidate.dy() == dy) {
        return candidate;
      }
    }
    return Dir8.NONE;
  }
}
