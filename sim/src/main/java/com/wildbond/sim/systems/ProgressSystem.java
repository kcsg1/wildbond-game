package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.wildbond.data.Monster;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Experience;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Mana;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.events.EventBus;
import com.wildbond.sim.events.LevelUp;

/**
 * §4.1 시스템 7번 — 몬스터가 죽으면 마지막 타격자에게 경험치를 주고, 문턱을 넘으면 레벨을 올린다 (docs/architecture.md §3.2).
 *
 * <p>플레이어 부활은 여기서 하지 않는다 — 죽어서 제거된 플레이어를 마을에서 다시 만드는 것은 존 전환과 같은 절차라 클라이언트의 ZoneRuntime 이 {@code
 * SpawnPlayer + RestorePlayer} 명령으로 처리한다(D-16).
 */
public final class ProgressSystem extends BaseSystem {

  private final EntityIndex index;
  private final com.wildbond.data.GameData gameData;
  private final EventBus eventBus;

  private ComponentMapper<Dead> mDead;
  private ComponentMapper<MonsterData> mMonster;
  private ComponentMapper<Experience> mExperience;
  private ComponentMapper<Stats> mStats;
  private ComponentMapper<Health> mHealth;
  private ComponentMapper<Mana> mMana;
  private ComponentMapper<EntityIdComponent> mEntityId;

  public ProgressSystem(EntityIndex index, com.wildbond.data.GameData gameData, EventBus eventBus) {
    this.index = index;
    this.gameData = gameData;
    this.eventBus = eventBus;
  }

  @Override
  protected void initialize() {
    mDead = world.getMapper(Dead.class);
    mMonster = world.getMapper(MonsterData.class);
    mExperience = world.getMapper(Experience.class);
    mStats = world.getMapper(Stats.class);
    mHealth = world.getMapper(Health.class);
    mMana = world.getMapper(Mana.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
  }

  @Override
  protected void processSystem() {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mDead.has(artemisId) || !mMonster.has(artemisId)) {
        continue;
      }
      Dead dead = mDead.get(artemisId);
      if (dead.expAwarded) {
        continue;
      }
      dead.expAwarded = true;

      int killerArtemisId = index.artemisIdOrMissing(dead.killerStableId);
      if (killerArtemisId < 0
          || !mExperience.has(killerArtemisId)
          || !mStats.has(killerArtemisId)) {
        continue;
      }
      Monster species = gameData.monster(mMonster.get(artemisId).speciesId);
      award(killerArtemisId, species.expYield());
    }
  }

  private void award(int artemisId, int expYield) {
    Experience experience = mExperience.get(artemisId);
    Stats stats = mStats.get(artemisId);
    experience.exp += expYield;

    int newLevel = Progression.levelFor(stats.level, experience.exp);
    if (newLevel == stats.level) {
      return;
    }
    experience.exp = Progression.expCarried(stats.level, experience.exp);
    applyLevel(artemisId, stats, newLevel);
    eventBus.enqueue(new LevelUp(mEntityId.get(artemisId).value, newLevel));
  }

  /** 레벨에 맞는 스탯으로 맞추고 HP/MP 를 전부 회복한다 (§3.2 레벨업). RestorePlayer 도 같은 절차를 쓴다. */
  static void applyLevel(
      int artemisId,
      Stats stats,
      int level,
      ComponentMapper<Health> mHealth,
      ComponentMapper<Mana> mMana) {
    stats.level = level;
    stats.atk = Progression.atkAt(level);
    stats.def = Progression.defAt(level);
    if (mHealth.has(artemisId)) {
      Health health = mHealth.get(artemisId);
      health.max = Progression.maxHpAt(level);
      health.current = health.max;
    }
    if (mMana.has(artemisId)) {
      Mana mana = mMana.get(artemisId);
      mana.max = Progression.maxMpAt(level);
      mana.current = mana.max;
    }
  }

  private void applyLevel(int artemisId, Stats stats, int level) {
    applyLevel(artemisId, stats, level, mHealth, mMana);
  }
}
