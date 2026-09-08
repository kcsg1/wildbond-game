package com.wildbond.sim.systems;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntityEdit;
import com.wildbond.data.Element;
import com.wildbond.data.GameData;
import com.wildbond.data.HitShape;
import com.wildbond.data.Skill;
import com.wildbond.data.TileCollision;
import com.wildbond.sim.Angle;
import com.wildbond.sim.Command;
import com.wildbond.sim.Rng;
import com.wildbond.sim.Ticks;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.ElementComponent;
import com.wildbond.sim.components.EntityIdComponent;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.Mana;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.MonsterState;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Projectile;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.Stats;
import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.events.Died;
import com.wildbond.sim.events.EventBus;
import java.util.Arrays;
import java.util.List;

/**
 * §4.1 시스템 5번 — 쿨다운 확인 → hit_shape 별 판정(rect/circle/cone 은 즉시, projectile 은 엔티티를 만들어 이후 틱에
 * CombatSystem 스스로 이동·충돌시킨다) → §3.2 데미지 공식 → Damaged/Died 이벤트.
 *
 * <p>투사체는 일부러 MovementSystem 을 타지 않는다 — 그쪽의 엔티티 간 밀어내기(resolvePush)가 투사체·대상 사이에서 겹침을 먼저 떼어내 버리면 이
 * 시스템의 명중 판정이 절대 성립하지 않기 때문이다. 대신 이 클래스가 소유·이동·충돌·소멸까지 전부 처리한다.
 */
public final class CombatSystem extends BaseSystem {

  private final EntityIndex index;
  private final GameData gameData;
  private final TileMap tileMap;
  private final EventBus eventBus;
  private final Rng rng;
  private final SimClock clock;

  private List<Command> pending = List.of();

  /** AISystem(§4.1 시스템 2번)이 이번 틱에 요청한 스킬 — {entityId, skillId, aimAngle} 3개씩 쌓인다. */
  private int[] aiRequests = new int[3 * 8];

  private int aiRequestCount;

  private ComponentMapper<Position> mPosition;
  private ComponentMapper<Health> mHealth;
  private ComponentMapper<Stats> mStats;
  private ComponentMapper<ElementComponent> mElement;
  private ComponentMapper<Skills> mSkills;
  private ComponentMapper<Dead> mDead;
  private ComponentMapper<Projectile> mProjectile;
  private ComponentMapper<EntityIdComponent> mEntityId;
  private ComponentMapper<Brain> mBrain;
  private ComponentMapper<PlayerTag> mPlayer;
  private ComponentMapper<MonsterData> mMonster;
  private ComponentMapper<Mana> mMana;
  private ComponentMapper<com.wildbond.sim.components.DeathAnim> mDeathAnim;

  private float manaRegenCarry;

  private int[] projectileIds = new int[8];
  private int projectileCount;

  public CombatSystem(
      EntityIndex index,
      GameData gameData,
      TileMap tileMap,
      EventBus eventBus,
      Rng rng,
      SimClock clock) {
    this.index = index;
    this.gameData = gameData;
    this.tileMap = tileMap;
    this.eventBus = eventBus;
    this.rng = rng;
    this.clock = clock;
  }

  /** Sim.step() 이 world.process() 전에 호출한다(CommandApplySystem 과 같은 명령 목록을 별도로 받는다). */
  public void enqueue(List<Command> commands) {
    this.pending = commands;
  }

  /**
   * AI 가 발동하는 스킬 — 플레이어의 {@code Command.UseSkill} 과 같은 경로로 처리된다. AISystem 은 이 시스템보다 먼저 도므로 요청은 같은
   * 틱에 소비된다.
   */
  public void requestSkill(int entityId, int skillId, int aimAngle) {
    if (aiRequestCount * 3 == aiRequests.length) {
      aiRequests = Arrays.copyOf(aiRequests, aiRequests.length * 2);
    }
    int base = aiRequestCount * 3;
    aiRequests[base] = entityId;
    aiRequests[base + 1] = skillId;
    aiRequests[base + 2] = aimAngle;
    aiRequestCount++;
  }

  @Override
  protected void initialize() {
    mPosition = world.getMapper(Position.class);
    mHealth = world.getMapper(Health.class);
    mStats = world.getMapper(Stats.class);
    mElement = world.getMapper(ElementComponent.class);
    mSkills = world.getMapper(Skills.class);
    mDead = world.getMapper(Dead.class);
    mProjectile = world.getMapper(Projectile.class);
    mEntityId = world.getMapper(EntityIdComponent.class);
    mBrain = world.getMapper(Brain.class);
    mPlayer = world.getMapper(PlayerTag.class);
    mMonster = world.getMapper(MonsterData.class);
    mMana = world.getMapper(Mana.class);
    mDeathAnim = world.getMapper(com.wildbond.sim.components.DeathAnim.class);
  }

  @Override
  protected void processSystem() {
    regenerateMana();
    tickCooldowns();
    moveProjectiles();

    for (int i = 0; i < pending.size(); i++) {
      if (pending.get(i) instanceof Command.UseSkill useSkill) {
        handleUseSkill(useSkill.entityId(), useSkill.skillId(), useSkill.aimAngle());
      }
    }
    pending = List.of();

    for (int i = 0; i < aiRequestCount; i++) {
      int base = i * 3;
      handleUseSkill(aiRequests[base], aiRequests[base + 1], aiRequests[base + 2]);
    }
    aiRequestCount = 0;

    processDeaths();
  }

  /** MP 는 매 틱 조금씩 찬다 — 정수 컴포넌트라 소수점 누적분을 따로 들고 있는다. */
  private void regenerateMana() {
    manaRegenCarry += CombatConstants.MP_REGEN_PER_SECOND * Ticks.DT_SECONDS;
    int whole = (int) manaRegenCarry;
    if (whole <= 0) {
      return;
    }
    manaRegenCarry -= whole;
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mMana.has(artemisId)) {
        continue;
      }
      Mana mana = mMana.get(artemisId);
      mana.current = Math.min(mana.max, mana.current + whole);
    }
  }

  private void tickCooldowns() {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int artemisId = index.artemisIdAt(i);
      if (!mSkills.has(artemisId)) {
        continue;
      }
      int[] cooldowns = mSkills.get(artemisId).cooldownRemainingTicks;
      for (int s = 0; s < cooldowns.length; s++) {
        if (cooldowns[s] > 0) {
          cooldowns[s]--;
        }
      }
    }
  }

  private void handleUseSkill(int casterStableId, int skillId, int aimAngle) {
    int casterArtemisId = index.artemisIdOrMissing(casterStableId);
    if (casterArtemisId < 0 || mDead.has(casterArtemisId) || !mSkills.has(casterArtemisId)) {
      return;
    }
    Skills skills = mSkills.get(casterArtemisId);
    int slot = indexOfSkill(skills, skillId);
    if (slot < 0 || skills.cooldownRemainingTicks[slot] > 0) {
      return; // 모르는 스킬이거나 쿨다운 중 — 재사용 무시.
    }

    Skill skill = gameData.skill(skillId);
    int manaCost = manaCostOf(skill);
    if (manaCost > 0) {
      if (!mMana.has(casterArtemisId) || mMana.get(casterArtemisId).current < manaCost) {
        return; // MP 부족 — 쿨다운도 돌리지 않는다.
      }
      mMana.get(casterArtemisId).current -= manaCost;
    }
    skills.cooldownRemainingTicks[slot] = skill.cooldownTicks();

    Position casterPos = mPosition.get(casterArtemisId);
    float theta = Angle.toRadians(aimAngle);
    float cosT = (float) StrictMath.cos(theta);
    float sinT = (float) StrictMath.sin(theta);

    if (skill.hitShape() == HitShape.PROJECTILE) {
      spawnProjectile(casterStableId, casterPos, skill, cosT, sinT);
    } else {
      resolveMeleeHit(casterArtemisId, casterPos, skill, cosT, sinT);
    }
  }

  /** 근접 기본 공격은 무료, 투사체 스킬만 MP 를 먹는다 (D-17 이후의 조작 감각에 맞춘 것). */
  private static int manaCostOf(Skill skill) {
    return skill.hitShape() == HitShape.PROJECTILE ? CombatConstants.RANGED_SKILL_MP_COST : 0;
  }

  private static int indexOfSkill(Skills skills, int skillId) {
    for (int i = 0; i < skills.skillIds.length; i++) {
      if (skills.skillIds[i] == skillId) {
        return i;
      }
    }
    return -1;
  }

  private void resolveMeleeHit(
      int casterArtemisId, Position casterPos, Skill skill, float cosT, float sinT) {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int targetArtemisId = index.artemisIdAt(i);
      if (!isValidTarget(targetArtemisId, casterArtemisId)) {
        continue;
      }
      Position targetPos = mPosition.get(targetArtemisId);
      if (shapeHits(skill, casterPos, targetPos, cosT, sinT)) {
        applyDamage(casterArtemisId, targetArtemisId, skill);
      }
    }
  }

  private static boolean shapeHits(
      Skill skill, Position caster, Position target, float cosT, float sinT) {
    float dx = target.x - caster.x;
    float dy = target.y - caster.y;
    return switch (skill.hitShape()) {
      case CIRCLE -> dx * dx + dy * dy <= (float) skill.hitW() * skill.hitW();
      case RECT -> {
        float localX = dx * cosT + dy * sinT;
        float localY = -dx * sinT + dy * cosT;
        yield localX >= 0f && localX <= skill.hitW() && StrictMath.abs(localY) <= skill.hitH() / 2f;
      }
      case CONE -> {
        float localX = dx * cosT + dy * sinT;
        float localY = -dx * sinT + dy * cosT;
        if (localX < 0f || localX > skill.hitW()) {
          yield false;
        }
        float halfWidthAtDistance = (skill.hitH() / 2f) * (localX / skill.hitW());
        yield StrictMath.abs(localY) <= halfWidthAtDistance;
      }
      case PROJECTILE -> false; // 투사체는 spawnProjectile/moveProjectiles 경로에서만 판정한다.
    };
  }

  private boolean isValidTarget(int targetArtemisId, int casterArtemisId) {
    if (!mHealth.has(targetArtemisId)
        || !mStats.has(targetArtemisId)
        || !mElement.has(targetArtemisId)
        || !mPosition.has(targetArtemisId)
        || mDead.has(targetArtemisId)) {
      return false;
    }
    if (targetArtemisId == casterArtemisId) {
      return false;
    }
    return casterArtemisId < 0 || !sameSide(casterArtemisId, targetArtemisId);
  }

  /**
   * 같은 편끼리는 맞지 않는다 — 몬스터의 범위 스킬이 옆의 몬스터를 때려 난투가 벌어지는 것을 막는다.
   *
   * <p>진영은 둘뿐이다(§3.1): 플레이어 / 몬스터. 허수아비는 어느 쪽도 아니라 누구에게나 맞는다.
   */
  private boolean sameSide(int a, int b) {
    if (mPlayer.has(a) && mPlayer.has(b)) {
      return true;
    }
    return mMonster.has(a) && mMonster.has(b);
  }

  private void applyDamage(int casterArtemisId, int targetArtemisId, Skill skill) {
    Stats attackerStats = mStats.get(casterArtemisId);
    Stats defenderStats = mStats.get(targetArtemisId);
    Element defenderElement = mElement.get(targetArtemisId).value;

    boolean crit = rng.nextFloat(Rng.Stream.COMBAT) < CombatConstants.CRIT_CHANCE;
    float randomRoll =
        CombatConstants.DAMAGE_RANDOM_MIN
            + rng.nextFloat(Rng.Stream.COMBAT)
                * (CombatConstants.DAMAGE_RANDOM_MAX - CombatConstants.DAMAGE_RANDOM_MIN);

    int damage =
        DamageFormula.compute(
            attackerStats.atk,
            attackerStats.level,
            skill.power(),
            skill.element(),
            defenderElement,
            defenderStats.def,
            crit,
            randomRoll);

    Health health = mHealth.get(targetArtemisId);
    health.current = Math.max(0, health.current - damage);

    Position targetPos = mPosition.get(targetArtemisId);
    int targetStableId = mEntityId.get(targetArtemisId).value;
    eventBus.enqueue(new Damaged(targetStableId, damage, targetPos.x, targetPos.y, crit));

    rememberAttacker(casterArtemisId, targetArtemisId);

    if (health.current <= 0 && !mDead.has(targetArtemisId)) {
      Dead dead = world.edit(targetArtemisId).create(Dead.class);
      dead.ticksRemaining = CombatConstants.DEAD_REMOVE_TICKS;
      dead.killerStableId =
          mEntityId.has(casterArtemisId) ? mEntityId.get(casterArtemisId).value : -1;
      com.wildbond.sim.components.DeathAnim anim =
          world.edit(targetArtemisId).create(com.wildbond.sim.components.DeathAnim.class);
      anim.totalTicks = CombatConstants.DEAD_REMOVE_TICKS;
      anim.elapsedTicks = 0;
      eventBus.enqueue(new Died(targetStableId));
    }
  }

  /**
   * 맞은 몬스터에게 "때린 놈"을 남긴다 (§9.1). passive 는 이걸로 도망치고, timid 는 반격하며, aggressive 는 시야 밖에서 원거리로 맞아도 곧장
   * 추격한다 — 감지만으로는 threat 이 생기지 않는 경우를 메운다. 기억은 HIT_MEMORY_TICKS 뒤 사라진다.
   */
  private void rememberAttacker(int casterArtemisId, int targetArtemisId) {
    if (mBrain.has(targetArtemisId) && mEntityId.has(casterArtemisId)) {
      Brain brain = mBrain.get(targetArtemisId);
      brain.threatStableId = mEntityId.get(casterArtemisId).value;
      brain.threatExpiresTick = clock.tick() + MonsterConstants.HIT_MEMORY_TICKS;
      brain.state = MonsterState.COMBAT;
    }
  }

  private void spawnProjectile(
      int casterStableId, Position casterPos, Skill skill, float cosT, float sinT) {
    int artemisId = world.create();
    EntityEdit edit = world.edit(artemisId);

    Position pos = edit.create(Position.class);
    pos.x = casterPos.x;
    pos.y = casterPos.y;

    Projectile proj = edit.create(Projectile.class);
    proj.ownerStableId = casterStableId;
    proj.skillId = skill.id();
    proj.dirX = cosT;
    proj.dirY = sinT;
    proj.speedPxS = CombatConstants.PROJECTILE_SPEED_PX_S;
    proj.traveledPx = 0f;
    proj.maxRangePx = skill.rangePx();
    proj.hitRadiusPx = skill.hitW() / 2f;

    addProjectile(artemisId);
  }

  private void moveProjectiles() {
    float dt = Ticks.DT_SECONDS;
    for (int i = 0; i < projectileCount; i++) {
      int artemisId = projectileIds[i];
      Projectile proj = mProjectile.get(artemisId);
      Position pos = mPosition.get(artemisId);

      float step = proj.speedPxS * dt;
      float newX = pos.x + proj.dirX * step;
      float newY = pos.y + proj.dirY * step;
      proj.traveledPx += step;

      boolean expired = proj.traveledPx >= proj.maxRangePx;
      if (expired || tileBlocked(newX, newY)) {
        destroyProjectile(artemisId, i);
        i--;
        continue;
      }

      pos.x = newX;
      pos.y = newY;

      int casterArtemisId = index.artemisIdOrMissing(proj.ownerStableId);
      if (casterArtemisId < 0) {
        destroyProjectile(artemisId, i); // 쏜 쪽이 이미 사라졌다 — 진영 판정도 데미지도 할 수 없다.
        i--;
        continue;
      }
      int hitTargetArtemisId = findProjectileTarget(proj, pos, casterArtemisId);
      if (hitTargetArtemisId >= 0) {
        applyDamage(casterArtemisId, hitTargetArtemisId, gameData.skill(proj.skillId));
        destroyProjectile(artemisId, i);
        i--;
      }
    }
  }

  private boolean tileBlocked(float x, float y) {
    int tx = (int) StrictMath.floor(x / SimConstants.TILE_SIZE_PX);
    int ty = (int) StrictMath.floor(y / SimConstants.TILE_SIZE_PX);
    return tileMap.collision(tx, ty) != TileCollision.NONE;
  }

  private int findProjectileTarget(Projectile proj, Position projPos, int casterArtemisId) {
    int n = index.size();
    for (int i = 0; i < n; i++) {
      int targetArtemisId = index.artemisIdAt(i);
      if (!isValidTarget(targetArtemisId, casterArtemisId)) {
        continue;
      }
      Position targetPos = mPosition.get(targetArtemisId);
      float dx = targetPos.x - projPos.x;
      float dy = targetPos.y - projPos.y;
      if (dx * dx + dy * dy <= proj.hitRadiusPx * proj.hitRadiusPx) {
        return targetArtemisId;
      }
    }
    return -1;
  }

  private void processDeaths() {
    for (int i = index.size() - 1; i >= 0; i--) {
      int artemisId = index.artemisIdAt(i);
      if (!mDead.has(artemisId)) {
        continue;
      }
      Dead dead = mDead.get(artemisId);
      dead.ticksRemaining--;
      if (mDeathAnim.has(artemisId)) {
        mDeathAnim.get(artemisId).elapsedTicks++;
      }
      if (dead.ticksRemaining <= 0) {
        int stableId = index.stableIdAt(i);
        world.delete(artemisId);
        index.remove(stableId);
      }
    }
  }

  private void addProjectile(int artemisId) {
    if (projectileCount == projectileIds.length) {
      projectileIds = Arrays.copyOf(projectileIds, projectileCount * 2);
    }
    projectileIds[projectileCount++] = artemisId;
  }

  private void destroyProjectile(int artemisId, int slot) {
    world.delete(artemisId);
    projectileCount--;
    projectileIds[slot] = projectileIds[projectileCount];
  }
}
