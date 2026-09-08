package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Velocity;

/**
 * §4.1 시스템 3번 — Path → Velocity (docs/architecture.md §9.3). 웨이포인트 타일의 중심을 향해 {@code
 * Brain.speedPxS} 로 움직이고, 충분히 가까워지면 다음 웨이포인트로 넘어간다. 경로를 다 쓰면 정지하고 경로를 비운다.
 *
 * <p>AISystem 이 직접 속도를 준 경우(가까운 추격·도주)에는 경로를 비워 두므로 이 시스템이 그 속도를 덮어쓰지 않는다.
 */
public final class PathFollowSystem extends BaseSystem {

  private final EntityIndex index;

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Velocity> mVelocity;
  private ComponentMapper<PathComponent> mPath;
  private ComponentMapper<Brain> mBrain;
  private ComponentMapper<Dead> mDead;

  public PathFollowSystem(EntityIndex index) {
    this.index = index;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mVelocity = world.getMapper(Velocity.class);
    mPath = world.getMapper(PathComponent.class);
    mBrain = world.getMapper(Brain.class);
    mDead = world.getMapper(Dead.class);
  }

  @Override
  protected void processSystem() {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mPath.has(artemisId) || mDead.has(artemisId)) {
        continue;
      }
      PathComponent path = mPath.get(artemisId);
      if (path.length == 0) {
        continue;
      }
      Position position = mPosition.get(artemisId);
      Velocity velocity = mVelocity.get(artemisId);

      while (path.index < path.length
          && withinArrivalRadius(position, path.tileX[path.index], path.tileY[path.index])) {
        path.index++;
      }
      if (path.index >= path.length) {
        path.clear();
        velocity.vx = 0f;
        velocity.vy = 0f;
        continue;
      }

      float targetX = tileCenter(path.tileX[path.index]);
      float targetY = tileCenter(path.tileY[path.index]);
      float dx = targetX - position.x;
      float dy = targetY - position.y;
      float length = (float) StrictMath.sqrt(dx * dx + dy * dy);
      if (length < 0.0001f) {
        velocity.vx = 0f;
        velocity.vy = 0f;
        continue;
      }
      float speed = mBrain.has(artemisId) ? mBrain.get(artemisId).speedPxS : 0f;
      velocity.vx = dx / length * speed;
      velocity.vy = dy / length * speed;
    }
  }

  private static boolean withinArrivalRadius(Position position, int tileX, int tileY) {
    float dx = tileCenter(tileX) - position.x;
    float dy = tileCenter(tileY) - position.y;
    return dx * dx + dy * dy
        <= MonsterConstants.WAYPOINT_ARRIVE_PX * MonsterConstants.WAYPOINT_ARRIVE_PX;
  }

  private static float tileCenter(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }
}
