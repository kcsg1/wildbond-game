package com.wildbond.sim.systems;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.wildbond.data.GameData;
import com.wildbond.data.HitShape;
import com.wildbond.data.Monster;
import com.wildbond.data.Skill;
import com.wildbond.data.Temperament;
import com.wildbond.sim.Angle;
import com.wildbond.sim.Rng;
import com.wildbond.sim.TileMap;
import com.wildbond.sim.components.Brain;
import com.wildbond.sim.components.Dead;
import com.wildbond.sim.components.Health;
import com.wildbond.sim.components.MonsterData;
import com.wildbond.sim.components.MonsterState;
import com.wildbond.sim.components.PathComponent;
import com.wildbond.sim.components.PlayerTag;
import com.wildbond.sim.components.Position;
import com.wildbond.sim.components.Skills;
import com.wildbond.sim.components.Velocity;
import com.wildbond.sim.systems.bt.BtStatus;

/**
 * BT 노드가 보는 세상 — 현재 몬스터 하나를 가리키고, 조건/동작이 필요한 질의와 조작을 모두 메서드로 제공한다 (docs/architecture.md §9.1,
 * §9.3).
 *
 * <p>인스턴스는 AiSystem 이 하나만 만들어 엔티티마다 {@link #bind} 로 다시 겨눈다 — 트리도 노드도 매 틱 새로 만들지 않으므로 AI 경로에서 할당이
 * 없다(§4.3). 성향(passive/timid/aggressive)에 따른 차이는 전부 여기 조건에 있다.
 */
public final class AiContext {

  private final EntityIndex index;
  private final TileMap tileMap;
  private final GameData gameData;
  private final Rng rng;
  private final SimClock clock;
  private final Pathfinder pathfinder;
  private final CombatSystem combatSystem;

  private final ComponentMapper<Position> mPosition;
  private final ComponentMapper<Velocity> mVelocity;
  private final ComponentMapper<Health> mHealth;
  private final ComponentMapper<Brain> mBrain;
  private final ComponentMapper<PathComponent> mPath;
  private final ComponentMapper<MonsterData> mMonster;
  private final ComponentMapper<Skills> mSkills;
  private final ComponentMapper<Dead> mDead;
  private final ComponentMapper<PlayerTag> mPlayer;

  private int artemisId;
  private int stableId;

  public AiContext(
      World world,
      EntityIndex index,
      TileMap tileMap,
      GameData gameData,
      Rng rng,
      SimClock clock,
      Pathfinder pathfinder,
      CombatSystem combatSystem) {
    this.index = index;
    this.tileMap = tileMap;
    this.gameData = gameData;
    this.rng = rng;
    this.clock = clock;
    this.pathfinder = pathfinder;
    this.combatSystem = combatSystem;

    this.mPosition = world.getMapper(Position.class);
    this.mVelocity = world.getMapper(Velocity.class);
    this.mHealth = world.getMapper(Health.class);
    this.mBrain = world.getMapper(Brain.class);
    this.mPath = world.getMapper(PathComponent.class);
    this.mMonster = world.getMapper(MonsterData.class);
    this.mSkills = world.getMapper(Skills.class);
    this.mDead = world.getMapper(Dead.class);
    this.mPlayer = world.getMapper(PlayerTag.class);
  }

  void bind(int artemisEntityId, int entityStableId) {
    this.artemisId = artemisEntityId;
    this.stableId = entityStableId;
  }

  Brain brain() {
    return mBrain.get(artemisId);
  }

  private Monster species() {
    return gameData.monster(mMonster.get(artemisId).speciesId);
  }

  // ------------------------------------------------------------------ 감지

  /**
   * §9.1 "0.25s 간격" 감지. aggressive 만 시야로 플레이어를 잡는다. passive/timid 는 맞았을 때 CombatSystem 이 심어 준 위협을
   * 기억 시간이 지날 때까지만 유지한다.
   */
  void sense() {
    Brain brain = brain();
    Temperament temperament = species().temperament();

    if (temperament != Temperament.AGGRESSIVE) {
      if (brain.threatStableId >= 0
          && (clock.tick() > brain.threatExpiresTick
              || !isThreatStillValid(brain.threatStableId, MonsterConstants.CHASE_GIVE_UP_TILES))) {
        brain.threatStableId = -1;
      }
      return;
    }
    if (isThreatStillValid(brain.threatStableId, MonsterConstants.CHASE_GIVE_UP_TILES)) {
      return; // 추격 중인 대상은 시야가 잠깐 끊겨도 유지한다.
    }
    brain.threatStableId = findNearestVisiblePlayer();
  }

  private boolean isThreatStillValid(int threatStableId, int maxTiles) {
    if (threatStableId < 0) {
      return false;
    }
    int threatArtemisId = index.artemisIdOrMissing(threatStableId);
    if (threatArtemisId < 0 || mDead.has(threatArtemisId) || !mPosition.has(threatArtemisId)) {
      return false;
    }
    Position self = mPosition.get(artemisId);
    Position other = mPosition.get(threatArtemisId);
    float maxPx = maxTiles * (float) SimConstants.TILE_SIZE_PX;
    return distanceSquared(self, other) <= maxPx * maxPx;
  }

  /** 시야(12타일 + solid/cliff 레이캐스트) 안의 가장 가까운 플레이어. EntityId 오름차순으로 훑는다(§4.3). */
  private int findNearestVisiblePlayer() {
    Position self = mPosition.get(artemisId);
    float sightPx = MonsterConstants.SIGHT_RADIUS_TILES * (float) SimConstants.TILE_SIZE_PX;
    float bestDistanceSquared = sightPx * sightPx;
    int best = -1;

    int n = index.size();
    for (int i = 0; i < n; i++) {
      int otherArtemisId = index.artemisIdAt(i);
      if (!mPlayer.has(otherArtemisId)
          || mDead.has(otherArtemisId)
          || !mPosition.has(otherArtemisId)) {
        continue;
      }
      Position other = mPosition.get(otherArtemisId);
      float distanceSquared = distanceSquared(self, other);
      if (distanceSquared > bestDistanceSquared) {
        continue;
      }
      if (!Sensing.hasLineOfSight(
          tileMap, tileOf(self.x), tileOf(self.y), tileOf(other.x), tileOf(other.y))) {
        continue;
      }
      bestDistanceSquared = distanceSquared;
      best = index.stableIdAt(i);
    }
    return best;
  }

  // ------------------------------------------------------------ BT 조건

  /** §9.1 Flee: (passive && 위협 있음) || (timid && HP < 20%). aggressive 는 도망치지 않는다. */
  boolean shouldFlee() {
    Temperament temperament = species().temperament();
    if (temperament == Temperament.PASSIVE) {
      return hasThreat();
    }
    if (temperament == Temperament.TIMID && hasThreat() && mHealth.has(artemisId)) {
      Health health = mHealth.get(artemisId);
      return health.max > 0 && health.current <= health.max * MonsterConstants.FLEE_HP_RATIO;
    }
    return false;
  }

  boolean hasThreat() {
    return brain().threatStableId >= 0 && index.artemisIdOrMissing(brain().threatStableId) >= 0;
  }

  // ------------------------------------------------------------ BT 동작

  /** 위협 반대 방향으로 직선 도주 — 경로 탐색을 쓰지 않는다(§9.1 Flee 는 즉시성이 중요하다). */
  BtStatus flee() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0) {
      return BtStatus.FAILURE;
    }
    Brain brain = brain();
    float speed = species().speedPxS() * MonsterConstants.FLEE_SPEED_MULT;
    brain.state = MonsterState.FLEE;
    brain.speedPxS = speed;
    clearPath();

    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    steer(self.x - threat.x, self.y - threat.y, speed);
    return BtStatus.RUNNING;
  }

  /** 사거리 안이고 쿨다운이 끝난 스킬이 있으면 쓴다 (§9.1 UseSkill(offCooldown, inRange)). 스킬이 없는 종은 절대 성공하지 않는다. */
  BtStatus attackThreat() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0 || !mSkills.has(artemisId)) {
      return BtStatus.FAILURE;
    }
    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    float dx = threat.x - self.x;
    float dy = threat.y - self.y;
    float distanceSquared = dx * dx + dy * dy;

    Skills skills = mSkills.get(artemisId);
    for (int slot = 0; slot < skills.skillIds.length; slot++) {
      if (skills.cooldownRemainingTicks[slot] > 0) {
        continue;
      }
      Skill skill = gameData.skill(skills.skillIds[slot]);
      float range = attackRangePx(skill);
      if (distanceSquared > range * range) {
        continue;
      }
      brain().state = MonsterState.COMBAT;
      clearPath();
      stop();
      combatSystem.requestSkill(
          stableId, skill.id(), Angle.fromRadians((float) StrictMath.atan2(dy, dx)));
      return BtStatus.SUCCESS;
    }
    return BtStatus.FAILURE;
  }

  /** 실제로 맞을 수 있는 거리 — 판정 폭까지 감안하고, 여유를 조금 남겨 확실히 닿게 한다. */
  private static float attackRangePx(Skill skill) {
    float reach =
        skill.hitShape() == HitShape.PROJECTILE
            ? skill.rangePx()
            : Math.max(skill.rangePx(), skill.hitW());
    return Math.max(MonsterConstants.MIN_ATTACK_RANGE_PX, reach) * 0.85f;
  }

  BtStatus chaseThreat() {
    int threatArtemisId = threatArtemisId();
    if (threatArtemisId < 0) {
      return BtStatus.FAILURE;
    }
    Brain brain = brain();
    float speed = species().speedPxS() * MonsterConstants.CHASE_SPEED_MULT;
    brain.state = MonsterState.COMBAT;
    brain.speedPxS = speed;

    Position self = mPosition.get(artemisId);
    Position threat = mPosition.get(threatArtemisId);
    if (Sensing.hasLineOfSight(
        tileMap, tileOf(self.x), tileOf(self.y), tileOf(threat.x), tileOf(threat.y))) {
      clearPath();
      steer(threat.x - self.x, threat.y - self.y, speed);
      return BtStatus.RUNNING;
    }
    followOrRequestPath(threat.x, threat.y);
    return BtStatus.RUNNING;
  }

  /** §9.1 Idle 의 앞부분 — 12타일 안의 walkable 타일로 배회한다. 배회가 끝나면 SUCCESS 로 Wait 에 넘긴다. */
  BtStatus wander() {
    Brain brain = brain();
    if (brain.state == MonsterState.WAIT) {
      return BtStatus.SUCCESS;
    }
    if (brain.state != MonsterState.WANDER) {
      if (!startWander(brain)) {
        beginWait(brain);
        return BtStatus.SUCCESS;
      }
      return BtStatus.RUNNING;
    }
    brain.stateTicks++;
    PathComponent path = mPath.get(artemisId);
    if (path.length == 0 || brain.stateTicks > MonsterConstants.WANDER_MAX_TICKS) {
      beginWait(brain);
      return BtStatus.SUCCESS;
    }
    return BtStatus.RUNNING;
  }

  BtStatus waitIdle() {
    Brain brain = brain();
    stop();
    if (brain.waitTicks > 0) {
      brain.waitTicks--;
      return BtStatus.RUNNING;
    }
    brain.state = MonsterState.IDLE;
    return BtStatus.SUCCESS;
  }

  // ------------------------------------------------------------------ 보조

  private boolean startWander(Brain brain) {
    Position self = mPosition.get(artemisId);
    int selfTx = tileOf(self.x);
    int selfTy = tileOf(self.y);
    int radius = MonsterConstants.WANDER_RADIUS_TILES;

    for (int attempt = 0; attempt < MonsterConstants.WANDER_TARGET_ATTEMPTS; attempt++) {
      int targetTx = selfTx - radius + rng.nextInt(Rng.Stream.SPAWN, radius * 2 + 1);
      int targetTy = selfTy - radius + rng.nextInt(Rng.Stream.SPAWN, radius * 2 + 1);
      if (targetTx == selfTx && targetTy == selfTy) {
        continue;
      }
      if (!pathfinder.canRequest()) {
        return false;
      }
      if (pathfinder.requestPath(selfTx, selfTy, targetTx, targetTy)) {
        copyPathFromPathfinder();
        brain.state = MonsterState.WANDER;
        brain.stateTicks = 0;
        brain.speedPxS = species().speedPxS();
        return true;
      }
    }
    return false;
  }

  private void beginWait(Brain brain) {
    brain.state = MonsterState.WAIT;
    brain.stateTicks = 0;
    int span = MonsterConstants.WAIT_MAX_TICKS - MonsterConstants.WAIT_MIN_TICKS + 1;
    brain.waitTicks = MonsterConstants.WAIT_MIN_TICKS + rng.nextInt(Rng.Stream.SPAWN, span);
    clearPath();
    stop();
  }

  /** 이미 경로가 있으면 그대로 따라가고, 없으면(그리고 재요청 간격·틱 상한이 허용하면) 새로 요청한다. */
  private void followOrRequestPath(float targetX, float targetY) {
    PathComponent path = mPath.get(artemisId);
    if (path.length > 0 && path.index < path.length) {
      return; // PathFollowSystem 이 이어서 처리한다.
    }
    Brain brain = brain();
    if (clock.tick() < brain.nextRepathTick) {
      stop();
      return;
    }
    brain.nextRepathTick = clock.tick() + MonsterConstants.REPATH_INTERVAL_TICKS;

    Position self = mPosition.get(artemisId);
    if (pathfinder.requestPath(tileOf(self.x), tileOf(self.y), tileOf(targetX), tileOf(targetY))) {
      copyPathFromPathfinder();
    } else {
      stop();
    }
  }

  private void copyPathFromPathfinder() {
    PathComponent path = mPath.get(artemisId);
    int length = Math.min(pathfinder.pathLength(), PathComponent.MAX_TILES);
    for (int i = 0; i < length; i++) {
      path.tileX[i] = pathfinder.pathTileX(i);
      path.tileY[i] = pathfinder.pathTileY(i);
    }
    path.length = length;
    path.index = 0;
  }

  private void clearPath() {
    mPath.get(artemisId).clear();
  }

  private void stop() {
    Velocity velocity = mVelocity.get(artemisId);
    velocity.vx = 0f;
    velocity.vy = 0f;
  }

  private void steer(float dx, float dy, float speedPxS) {
    Velocity velocity = mVelocity.get(artemisId);
    float length = (float) StrictMath.sqrt(dx * dx + dy * dy);
    if (length < 0.0001f) {
      velocity.vx = 0f;
      velocity.vy = 0f;
      return;
    }
    velocity.vx = dx / length * speedPxS;
    velocity.vy = dy / length * speedPxS;
  }

  private int threatArtemisId() {
    int threatStableId = brain().threatStableId;
    if (threatStableId < 0) {
      return -1;
    }
    int threatArtemisId = index.artemisIdOrMissing(threatStableId);
    if (threatArtemisId < 0 || !mPosition.has(threatArtemisId) || mDead.has(threatArtemisId)) {
      brain().threatStableId = -1;
      return -1;
    }
    return threatArtemisId;
  }

  private static float distanceSquared(Position a, Position b) {
    float dx = b.x - a.x;
    float dy = b.y - a.y;
    return dx * dx + dy * dy;
  }

  static int tileOf(float worldPx) {
    return (int) StrictMath.floor(worldPx / SimConstants.TILE_SIZE_PX);
  }
}
