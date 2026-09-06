package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.data.TileCollision;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Collider;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.events.EntityMoved;
import com.wildbond.sim.events.EventBus;

/**
 * §4.1 시스템 4번 — Velocity × dt, 타일 AABB 스윕(x축 먼저, y축 다음), 엔티티 간 약한 밀어내기.
 *
 * <p>순회는 EntityIndex 의 오름차순(EntityId) 순서를 그대로 쓴다 — 결정성(§4.3).
 */
public final class MovementSystem extends BaseSystem {

  private final EntityIndex index;
  private final TileMap tileMap;
  private final EventBus eventBus;

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Velocity> mVelocity;
  private ComponentMapper<Collider> mCollider;
  private ComponentMapper<EntityIdComponent> mEntityId;

  public MovementSystem(EntityIndex index, TileMap tileMap, EventBus eventBus) {
    this.index = index;
    this.tileMap = tileMap;
    this.eventBus = eventBus;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mVelocity = world.getMapper(Velocity.class);
    mCollider = world.getMapper(Collider.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
  }

  @Override
  protected void processSystem() {
    float dt = world.getDelta();
    int n = index.size();
    boolean notifyListeners = eventBus.hasSubscribers();

    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mVelocity.has(artemisId) || !mPosition.has(artemisId)) {
        continue;
      }
      Position position = mPosition.get(artemisId);
      Velocity velocity = mVelocity.get(artemisId);
      Collider collider = mCollider.has(artemisId) ? mCollider.get(artemisId) : null;

      float oldX = position.x;
      float oldY = position.y;

      if (velocity.vx != 0f) {
        float newX = position.x + velocity.vx * dt;
        if (collider == null || !blocked(newX, position.y, collider)) {
          position.x = newX;
        }
      }
      if (velocity.vy != 0f) {
        float newY = position.y + velocity.vy * dt;
        if (collider == null || !blocked(position.x, newY, collider)) {
          position.y = newY;
        }
      }

      if (notifyListeners && (position.x != oldX || position.y != oldY)) {
        int stableId = mEntityId.get(artemisId).value;
        eventBus.enqueue(new EntityMoved(stableId, position.x, position.y));
      }
    }

    resolvePush(n);
  }

  private boolean blocked(float centerX, float centerY, Collider collider) {
    float halfW = collider.width * 0.5f;
    float halfH = collider.height * 0.5f;
    float epsilon = 0.001f;

    int minTx = (int) StrictMath.floor((centerX - halfW) / SimConstants.TILE_SIZE_PX);
    int maxTx = (int) StrictMath.floor((centerX + halfW - epsilon) / SimConstants.TILE_SIZE_PX);
    int minTy = (int) StrictMath.floor((centerY - halfH) / SimConstants.TILE_SIZE_PX);
    int maxTy = (int) StrictMath.floor((centerY + halfH - epsilon) / SimConstants.TILE_SIZE_PX);

    for (int ty = minTy; ty <= maxTy; ty++) {
      for (int tx = minTx; tx <= maxTx; tx++) {
        if (tileMap.collision(tx, ty) != TileCollision.NONE) {
          return true;
        }
      }
    }
    return false;
  }

  /** 겹치는 두 콜라이더를 얕게 밀어낸다 — 정확한 물리가 아니라 뭉침 방지용. */
  private void resolvePush(int n) {
    for (int i = 0; i < n; i++) {
      int aId = index.artemisIdAt(i);
      if (!mCollider.has(aId) || !mPosition.has(aId)) {
        continue;
      }
      Position pa = mPosition.get(aId);
      Collider ca = mCollider.get(aId);

      for (int j = i + 1; j < n; j++) {
        int bId = index.artemisIdAt(j);
        if (!mCollider.has(bId) || !mPosition.has(bId)) {
          continue;
        }
        Position pb = mPosition.get(bId);
        Collider cb = mCollider.get(bId);

        float dx = pb.x - pa.x;
        float dy = pb.y - pa.y;
        float overlapX = (ca.width + cb.width) * 0.5f - StrictMath.abs(dx);
        float overlapY = (ca.height + cb.height) * 0.5f - StrictMath.abs(dy);
        if (overlapX <= 0f || overlapY <= 0f) {
          continue;
        }

        float pushX = 0f;
        float pushY = 0f;
        if (overlapX < overlapY) {
          pushX = (dx < 0 ? -overlapX : overlapX) * 0.5f * SimConstants.PUSH_STRENGTH;
        } else {
          pushY = (dy < 0 ? -overlapY : overlapY) * 0.5f * SimConstants.PUSH_STRENGTH;
        }
        pa.x -= pushX;
        pa.y -= pushY;
        pb.x += pushX;
        pb.y += pushY;
      }
    }
  }
}
