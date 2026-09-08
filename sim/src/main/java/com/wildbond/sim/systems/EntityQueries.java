package com.wildbond.sim.systems;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.wildbond.sim.EntityKind;
import com.wildbond.sim.SimView;
import com.wildbond.sim.components.DummyTag;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Owner;
import com.wildbond.sim.components.PalData;
import com.wildbond.sim.components.Party;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Sphere;

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
  private final ComponentMapper<PalData> mPal;
  private final ComponentMapper<Owner> mOwner;
  private final ComponentMapper<Party> mParty;
  private final ComponentMapper<Sphere> mSphere;

  public EntityQueries(World world, EntityIndex index) {
    this.index = index;
    this.mPosition = world.getMapper(Position.class);
    this.mHealth = world.getMapper(Health.class);
    this.mPlayer = world.getMapper(PlayerTag.class);
    this.mDummy = world.getMapper(DummyTag.class);
    this.mPal = world.getMapper(PalData.class);
    this.mOwner = world.getMapper(Owner.class);
    this.mParty = world.getMapper(Party.class);
    this.mSphere = world.getMapper(Sphere.class);
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

  public int maxHealth(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mHealth.has(artemisId) ? mHealth.get(artemisId).max : -1;
  }

  public EntityKind kind(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    if (mPlayer.has(artemisId)) {
      return EntityKind.PLAYER;
    }
    if (mPal.has(artemisId)) {
      return EntityKind.PAL;
    }
    if (mSphere.has(artemisId)) {
      return EntityKind.SPHERE;
    }
    if (mDummy.has(artemisId)) {
      return EntityKind.DUMMY;
    }
    return EntityKind.UNKNOWN;
  }

  public int speciesId(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mPal.has(artemisId) ? mPal.get(artemisId).speciesId : -1;
  }

  public int level(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mPal.has(artemisId) ? mPal.get(artemisId).level : -1;
  }

  public int ownerId(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mOwner.has(artemisId) ? mOwner.get(artemisId).ownerStableId : -1;
  }

  public float renderZ(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mSphere.has(artemisId) ? mSphere.get(artemisId).z : 0f;
  }

  /** 플레이어(첫 PlayerTag 엔티티)의 파티에서 slot 번째 팰. 비었으면 -1. */
  public int partyEntityId(int slot) {
    if (slot < 0 || slot >= SimView.PARTY_SLOTS) {
      return -1;
    }
    int playerStableId = -1;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      if (mPlayer.has(index.artemisIdAt(i))) {
        playerStableId = index.stableIdAt(i);
        break;
      }
    }
    if (playerStableId < 0) {
      return -1;
    }
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mParty.has(artemisId) || !mOwner.has(artemisId)) {
        continue;
      }
      if (mOwner.get(artemisId).ownerStableId == playerStableId
          && mParty.get(artemisId).slot == slot) {
        return index.stableIdAt(i);
      }
    }
    return -1;
  }
}
