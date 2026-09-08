package com.wildbond.sim.systems;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.wildbond.sim.EntityKind;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.DeathAnim;
import com.wildbond.sim.components.DroppedItem;
import com.wildbond.sim.components.DummyTag;
import com.wildbond.sim.components.Experience;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Inventory;
import com.wildbond.sim.components.Mana;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.components.Wallet;

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
  private final ComponentMapper<MonsterData> mMonster;
  private final ComponentMapper<Stats> mStats;
  private final ComponentMapper<Mana> mMana;
  private final ComponentMapper<Wallet> mWallet;
  private final ComponentMapper<Experience> mExperience;
  private final ComponentMapper<Inventory> mInventory;
  private final ComponentMapper<DroppedItem> mDrop;
  private final ComponentMapper<DeathAnim> mDeathAnim;
  private final ComponentMapper<Dead> mDead;

  public EntityQueries(World world, EntityIndex index) {
    this.index = index;
    this.mPosition = world.getMapper(Position.class);
    this.mHealth = world.getMapper(Health.class);
    this.mPlayer = world.getMapper(PlayerTag.class);
    this.mDummy = world.getMapper(DummyTag.class);
    this.mMonster = world.getMapper(MonsterData.class);
    this.mStats = world.getMapper(Stats.class);
    this.mMana = world.getMapper(Mana.class);
    this.mWallet = world.getMapper(Wallet.class);
    this.mExperience = world.getMapper(Experience.class);
    this.mInventory = world.getMapper(Inventory.class);
    this.mDrop = world.getMapper(DroppedItem.class);
    this.mDeathAnim = world.getMapper(DeathAnim.class);
    this.mDead = world.getMapper(Dead.class);
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
    if (mMonster.has(artemisId)) {
      return EntityKind.MONSTER;
    }
    if (mDrop.has(artemisId)) {
      return EntityKind.DROP;
    }
    if (mDummy.has(artemisId)) {
      return EntityKind.DUMMY;
    }
    return EntityKind.UNKNOWN;
  }

  public int speciesId(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mMonster.has(artemisId) ? mMonster.get(artemisId).speciesId : -1;
  }

  public int level(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mStats.has(artemisId) ? mStats.get(artemisId).level : -1;
  }

  public int mana(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mMana.has(artemisId) ? mMana.get(artemisId).current : -1;
  }

  public int maxMana(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mMana.has(artemisId) ? mMana.get(artemisId).max : -1;
  }

  public int coins(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mWallet.has(artemisId) ? mWallet.get(artemisId).coins : -1;
  }

  public int experience(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mExperience.has(artemisId) ? mExperience.get(artemisId).exp : -1;
  }

  public int expToNextLevel(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    if (!mExperience.has(artemisId) || !mStats.has(artemisId)) {
      return -1;
    }
    return Progression.expToNext(mStats.get(artemisId).level);
  }

  public int inventoryItemId(int stableId, int slot) {
    int artemisId = index.artemisIdOf(stableId);
    if (!mInventory.has(artemisId) || slot < 0 || slot >= Inventory.SLOTS) {
      return 0;
    }
    return mInventory.get(artemisId).itemIds[slot];
  }

  public int inventoryCount(int stableId, int slot) {
    int artemisId = index.artemisIdOf(stableId);
    if (!mInventory.has(artemisId) || slot < 0 || slot >= Inventory.SLOTS) {
      return 0;
    }
    return mInventory.get(artemisId).counts[slot];
  }

  public int dropItemId(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mDrop.has(artemisId) ? mDrop.get(artemisId).itemId : -1;
  }

  public int dropAmount(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    return mDrop.has(artemisId) ? mDrop.get(artemisId).amount : -1;
  }

  public float deathProgress(int stableId) {
    int artemisId = index.artemisIdOf(stableId);
    if (!mDead.has(artemisId) || !mDeathAnim.has(artemisId)) {
      return 0f;
    }
    DeathAnim anim = mDeathAnim.get(artemisId);
    return anim.totalTicks <= 0 ? 1f : Math.min(1f, (float) anim.elapsedTicks / anim.totalTicks);
  }
}
