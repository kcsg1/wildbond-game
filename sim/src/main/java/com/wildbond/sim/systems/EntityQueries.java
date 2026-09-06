package com.wildbond.sim.systems;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.wildbond.sim.EntityKind;
import com.wildbond.sim.components.DummyTag;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;

/**
 * Sim(SimView 구현)이 위임하는 읽기 전용 질의. 이 클래스만 컴포넌트를 직접 읽고, Sim 은 원시 값만 돌려받는다 — ArchitectureTest 의 "컴포넌트는
 * systems/components 패키지에서만" 규칙을 만족하기 위함.
 */
public final class EntityQueries {

  private final EntityIndex index;
  private final ComponentMapper<Position> mPosition;
  private final ComponentMapper<Health> mHealth;
  private final ComponentMapper<PlayerTag> mPlayer;
  private final ComponentMapper<DummyTag> mDummy;

  public EntityQueries(World world, EntityIndex index) {
    this.index = index;
    this.mPosition = world.getMapper(Position.class);
    this.mHealth = world.getMapper(Health.class);
    this.mPlayer = world.getMapper(PlayerTag.class);
    this.mDummy = world.getMapper(DummyTag.class);
  }

  public int entityCount() {
    return index.size();
  }

  public int stableIdAt(int i) {
    return index.stableIdAt(i);
  }

  public float x(int stableId) {
    return mPosition.get(index.artemisIdOf(stableId)).x;
  }

  public float y(int stableId) {
    return mPosition.get(index.artemisIdOf(stableId)).y;
  }

  public int health(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mHealth.has(artemisId) ? mHealth.get(artemisId).current : -1;
  }

  public EntityKind kind(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    if (mPlayer.has(artemisId)) {
      return EntityKind.PLAYER;
    }
    if (mDummy.has(artemisId)) {
      return EntityKind.DUMMY;
    }
    return EntityKind.UNKNOWN;
  }
}
