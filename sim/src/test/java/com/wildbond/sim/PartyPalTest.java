package com.wildbond.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.wildbond.data.Element;
import com.wildbond.data.TileCollision;
import com.wildbond.sim.events.PalCaptured;
import com.wildbond.sim.systems.PalConstants;
import com.wildbond.sim.systems.SimConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/m0-prompts.md 단계7 파티 팰 AI — FollowOwner(3~6타일)와 "주인이 공격한 대상 Combat".
 *
 * <p>포획 자체를 결정적으로 만들기 위해 팰을 절벽 우리에 가둔 채 잡고(={@code CaptureSystemTest} 와 같은 방법), 잡은 뒤 우리를 허물어 따라다니게
 * 한다. 지형 배열을 그대로 들고 있는 {@link ArrayTileMap} 특성을 이용한 것이다.
 */
class PartyPalTest {

  private static final int SIZE = 30;
  private static final int PLAYER_TX = 15;
  private static final int PAL_TX = 18;
  private static final int ROW = 15;
  private static final int MOSSLING = 1;
  private static final int SPHERE_GIGA = 3;

  @Test
  void capturedPalFollowsItsOwnerAndFightsTheOwnersTarget() {
    byte[] grid = new byte[SIZE * SIZE];
    for (int ty = ROW - 1; ty <= ROW + 1; ty++) {
      for (int tx = PAL_TX - 1; tx <= PAL_TX + 1; tx++) {
        if (tx != PAL_TX || ty != ROW) {
          grid[ty * SIZE + tx] = (byte) TileCollision.CLIFF.ordinal();
        }
      }
    }
    ArrayTileMap map = new ArrayTileMap(SIZE, SIZE, grid);
    Sim sim = TestSupport.newSim(map, 20260907L);

    List<PalCaptured> captured = new ArrayList<>();
    sim.subscribe(
        event -> {
          if (event instanceof PalCaptured c) {
            captured.add(c);
          }
        });

    sim.step(
        0,
        List.of(
            new Command.SpawnPlayer(center(PLAYER_TX), center(ROW)),
            new Command.SpawnPal(center(PAL_TX), center(ROW), MOSSLING, 1)));
    SimView view = sim.view();
    int playerId = view.stableIdAt(0);
    int palId = view.stableIdAt(1);

    int tick = 1;
    for (int attempt = 0; attempt < 12 && captured.isEmpty(); attempt++) {
      float dx = view.x(palId) - view.x(playerId);
      float dy = view.y(palId) - view.y(playerId);
      float distancePx = (float) StrictMath.sqrt(dx * dx + dy * dy);
      int aim = Angle.fromRadians((float) StrictMath.atan2(dy, dx));
      sim.step(tick++, List.of(new Command.ThrowSphere(playerId, SPHERE_GIGA, aim, distancePx)));
      for (int t = 0; t < 20 && captured.isEmpty(); t++) {
        sim.step(tick++, List.of());
      }
    }
    assertThat(captured).as("3단계 포획구(기대 확률 0.9)로 12번 안에 잡힌다").hasSize(1);
    assertThat(view.partyEntityId(0)).isEqualTo(palId);

    // 우리를 허문다 — 이제 따라다닐 수 있다.
    for (int ty = ROW - 1; ty <= ROW + 1; ty++) {
      for (int tx = PAL_TX - 1; tx <= PAL_TX + 1; tx++) {
        grid[ty * SIZE + tx] = (byte) TileCollision.NONE.ordinal();
      }
    }

    float palStartX = view.x(palId);
    for (int t = 0; t < 80; t++) {
      sim.step(tick++, List.of(new Command.MoveInput(playerId, Dir8.W, false)));
    }

    assertThat(view.x(palId)).as("주인을 따라 서쪽으로 움직였다").isLessThan(palStartX - 100f);
    float followDistance = distance(view, playerId, palId);
    assertThat(followDistance)
        .as("FollowOwner 는 3~6타일 안에 붙어 있는다 (경로 재계산 여유 1타일 허용)")
        .isLessThan((PalConstants.FOLLOW_MAX_TILES + 1) * (float) SimConstants.TILE_SIZE_PX);

    // 주인이 때린 대상을 함께 공격한다. 먼저 멈춰 세운다 — MoveInput 이 없으면 직전 속도가 그대로 남아 계속 서쪽으로 흐른다.
    sim.step(tick++, List.of(new Command.MoveInput(playerId, Dir8.NONE, false)));
    float dummyX = view.x(playerId) + 32f;
    float dummyY = view.y(playerId);
    sim.step(
        tick++, List.of(new Command.SpawnDummy(dummyX, dummyY, Element.NONE, 100_000, 0, 0, 1)));
    int dummyId = view.stableIdAt(view.entityCount() - 1);
    assertThat(view.kind(dummyId)).isEqualTo(EntityKind.DUMMY);

    int aimEast = Angle.fromRadians(0f);
    sim.step(tick++, List.of(new Command.UseSkill(playerId, 1, aimEast)));
    int hpAfterOwnerHit = view.health(dummyId);
    assertThat(hpAfterOwnerHit).as("주인의 한 방이 들어갔다").isLessThan(100_000);

    for (int t = 0; t < 120; t++) {
      sim.step(tick++, List.of()); // 주인은 더 이상 아무것도 하지 않는다.
    }

    assertThat(view.health(dummyId))
        .as("주인이 손을 놓은 뒤에도 파티 팰이 같은 대상을 계속 때린다")
        .isLessThan(hpAfterOwnerHit);
    assertThat(view.health(playerId))
        .as("파티 팰의 범위 스킬이 주인을 때리지 않는다")
        .isEqualTo(SimConstants.PLAYER_MAX_HP);
  }

  private static float center(int tile) {
    return tile * (float) SimConstants.TILE_SIZE_PX + SimConstants.TILE_SIZE_PX / 2f;
  }

  private static float distance(SimView view, int a, int b) {
    float dx = view.x(a) - view.x(b);
    float dy = view.y(a) - view.y(b);
    return (float) StrictMath.sqrt(dx * dx + dy * dy);
  }
}
