package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.TileCollision;
import com.wildbond.sim.events.Damaged;
import com.wildbond.sim.events.PalCaptureFailed;
import com.wildbond.sim.events.PalCaptured;
import com.wildbond.sim.systems.CaptureConstants;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/architecture.md §3.2 포획 — 투척·낙하 판정·성공/실패 처리.
 *
 * <p>팰을 절벽으로 둘러싼 1타일 우리에 넣어 검증을 결정적으로 만든다. 절벽은 시야도 이동도 막으므로 팰은 (1) 플레이어를 감지하지 못하고 (2) 배회할 곳이 없어
 * 제자리에 머문다 — 포획구가 항상 같은 타일에 떨어진다. 포획구 자체는 "가상 높이 z 를 가진 포물선"이라 지형을 넘어간다.
 */
class CaptureSystemTest {

  private static final int SIZE = 30;
  private static final int PLAYER_TX = 15;
  private static final int PAL_TX = 18;
  private static final int ROW = 15;

  private static final int MOSSLING = 1; // captureRate 0.5
  private static final int SPHERE_BASIC = 1; // 배수 1.0
  private static final int SPHERE_GIGA = 3; // 3단계, 배수 1.8

  @Test
  void successfulCaptureAssignsOwnerAndPartySlot() {
    Fixture fixture = pennedPal(1234L, 1);

    PalCaptured captured = fixture.throwUntilResolved(SPHERE_GIGA, 12).captured();

    assertThat(captured).as("기대 확률 0.9 (0.5 × 1.8) 로 12번 안에는 잡힌다").isNotNull();
    assertThat(captured.palEntityId()).isEqualTo(fixture.palId);
    assertThat(captured.ownerEntityId()).isEqualTo(fixture.playerId);
    assertThat(captured.partySlot()).isZero();
    assertThat(captured.shakes())
        .isBetween(CaptureConstants.MIN_SHAKES, CaptureConstants.MAX_SHAKES);

    SimView view = fixture.sim.view();
    assertThat(view.ownerId(fixture.palId)).isEqualTo(fixture.playerId);
    assertThat(view.partyEntityId(0)).isEqualTo(fixture.palId);
    assertThat(view.partyEntityId(1)).isEqualTo(-1);
    assertThat(view.kind(fixture.palId)).isEqualTo(EntityKind.PAL);
    assertThat(view.speciesId(fixture.palId)).isEqualTo(MOSSLING);
  }

  @Test
  void failedCaptureLeavesPalWildAndTurnsItHostile() {
    // 레벨 30 팰 + 1단계 포획구 → 레벨 페널티 0.8^5 로 성공률 약 16%.
    Fixture fixture = pennedPal(555L, 30);

    for (int t = 1; t <= 40; t++) {
      fixture.sim.step(fixture.nextTick++, List.of());
    }
    assertThat(fixture.damaged).as("절벽에 가려 던지기 전까지는 플레이어를 감지하지 못한다").isEmpty();

    PalCaptureFailed failed = fixture.throwUntilResolved(SPHERE_BASIC, 1).failed();

    assertThat(failed).as("첫 투척은 실패한다").isNotNull();
    assertThat(failed.palEntityId()).isEqualTo(fixture.palId);
    assertThat(failed.throwerEntityId()).isEqualTo(fixture.playerId);
    assertThat(failed.shakes()).isBetween(CaptureConstants.MIN_SHAKES, CaptureConstants.MAX_SHAKES);

    SimView view = fixture.sim.view();
    assertThat(view.ownerId(fixture.palId)).as("주인이 생기지 않는다").isEqualTo(-1);
    assertThat(view.partyEntityId(0)).isEqualTo(-1);

    for (int t = 1; t <= 40; t++) {
      fixture.sim.step(fixture.nextTick++, List.of());
    }
    assertThat(fixture.damaged).as("실패한 팰은 Combat 상태가 되어 던진 사람을 공격한다").isNotEmpty();
    assertThat(fixture.damaged.get(0).entityId()).isEqualTo(fixture.playerId);
  }

  @Test
  void captureRateOverManyThrowsMatchesTheFormula() {
    int trials = 1000;
    int successes = 0;
    for (int seed = 0; seed < trials; seed++) {
      Fixture fixture = pennedPal(seed, 1);
      if (fixture.throwUntilResolved(SPHERE_BASIC, 1).captured() != null) {
        successes++;
      }
    }
    // mossling 0.5 × 1단계 배수 1.0 × HP 만땅(보너스 1.0) × 레벨 동일(페널티 1.0) = 0.5
    assertThat((float) successes / trials)
        .as("%d회 시뮬 성공률이 공식 기대값 0.5 의 ±3%%p 안 (§4.4)", trials)
        .isBetween(0.47f, 0.53f);
  }

  /** 절벽 우리에 갇힌 팰 하나와 플레이어 하나. */
  private static Fixture pennedPal(long seed, int palLevel) {
    byte[] grid = new byte[SIZE * SIZE];
    for (int ty = ROW - 1; ty <= ROW + 1; ty++) {
      for (int tx = PAL_TX - 1; tx <= PAL_TX + 1; tx++) {
        if (tx != PAL_TX || ty != ROW) {
          grid[ty * SIZE + tx] = (byte) TileCollision.CLIFF.ordinal();
        }
      }
    }
    ArrayTileMap map = new ArrayTileMap(SIZE, SIZE, grid);
    Sim sim = TestSupport.newSim(map, seed);
    Fixture fixture = new Fixture(sim);

    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(center(PLAYER_TX), center(ROW)),
            new Command.SpawnPal(center(PAL_TX), center(ROW), MOSSLING, palLevel)));
    fixture.playerId = sim.view().stableIdAt(0);
    fixture.palId = sim.view().stableIdAt(1);
    return fixture;
  }

  private static float center(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }

  /** 한 번의 투척이 낳은 결과 (둘 중 하나만 non-null). */
  private record Outcome(PalCaptured captured, PalCaptureFailed failed) {}

  private static final class Fixture {
    final Sim sim;
    final List<Damaged> damaged = new ArrayList<>();
    final List<PalCaptured> captured = new ArrayList<>();
    final List<PalCaptureFailed> failed = new ArrayList<>();

    int playerId;
    int palId;
    int nextTick = 1;

    Fixture(Sim sim) {
      this.sim = sim;
      sim.subscribe(
          event -> {
            switch (event) {
              case Damaged d -> damaged.add(d);
              case PalCaptured c -> captured.add(c);
              case PalCaptureFailed f -> failed.add(f);
              default -> {
                // 이동·스폰 이벤트는 이 테스트와 무관하다.
              }
            }
          });
    }

    /** 결과(성공/실패 이벤트)가 나올 때까지 최대 maxThrows 번 던진다. */
    Outcome throwUntilResolved(int sphereItemId, int maxThrows) {
      SimView view = sim.view();
      for (int attempt = 0; attempt < maxThrows; attempt++) {
        float dx = view.x(palId) - view.x(playerId);
        float dy = view.y(palId) - view.y(playerId);
        float distancePx = (float) StrictMath.sqrt(dx * dx + dy * dy);
        int aim = Angle.fromRadians((float) StrictMath.atan2(dy, dx));

        sim.step(
            nextTick++, List.of(new Command.ThrowSphere(playerId, sphereItemId, aim, distancePx)));
        for (int t = 0; t < 20 && captured.isEmpty() && failed.isEmpty(); t++) {
          sim.step(nextTick++, List.of());
        }
        if (!captured.isEmpty() || !failed.isEmpty()) {
          break;
        }
      }
      return new Outcome(
          captured.isEmpty() ? null : captured.get(0), failed.isEmpty() ? null : failed.get(0));
    }
  }
}
