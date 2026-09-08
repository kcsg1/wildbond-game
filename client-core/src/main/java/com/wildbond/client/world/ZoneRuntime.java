package com.wildbond.client.world;

import com.wildbond.client.GameConfig;
import com.wildbond.client.map.FileChunkLoader;
import com.wildbond.data.GameData;
import com.wildbond.data.chunk.Chunk;
import com.wildbond.data.chunk.ChunkCoord;
import com.wildbond.data.chunk.ChunkFormat;
import com.wildbond.data.chunk.ChunkObject;
import com.wildbond.sim.ChunkTileMap;
import com.wildbond.sim.Command;
import com.wildbond.sim.Sim;
import com.wildbond.sim.SimView;
import java.util.List;

/**
 * 현재 존의 sim 을 만들고, 존을 넘어갈 때 새로 만든다 (docs/architecture.md D-16).
 *
 * <p>존 전환은 "sim 을 새로 만들고 플레이어 상태만 옮겨 싣는 것"이다 — 야생 팰·드롭 같은 존 로컬 상태는 버려진다. sim 자체는 존을 모른다(§6 규칙: 상태
 * 변경은 Command 로만). 여기서는 새 Sim 을 만들고 SpawnPlayer 를 한 번 넣을 뿐이다.
 */
public final class ZoneRuntime {

  /** 가장자리에서 이 안쪽까지 오면 이웃 존으로 넘어간다. */
  private static final int EDGE_MARGIN_TILES = 1;

  /** 넘어간 뒤 반대편 가장자리에서 이만큼 안쪽에 놓는다 — 바로 되돌아가지 않게. */
  private static final int ENTRY_INSET_TILES = 3;

  private static final long WORLD_SEED = 20260904L;
  private static final int TILE_PX = 32;

  private final GameConfig config;
  private final GameData gameData;

  private Zone zone;
  private Sim sim;
  private FileChunkLoader chunkLoader;
  private int playerId;
  private PlayerCarry carry = PlayerCarry.initial();

  public ZoneRuntime(GameConfig config, GameData gameData) {
    this.config = config;
    this.gameData = gameData;
  }

  /** 첫 존을 연다. spawnTx/Ty 는 타일 좌표. */
  public void enter(Zone target, int spawnTx, int spawnTy) {
    zone = target;
    chunkLoader = new FileChunkLoader(config.chunksDir().resolve(target.chunkDir()));
    ChunkTileMap tileMap = new ChunkTileMap(chunkLoader);
    sim = new Sim(gameData, tileMap, WORLD_SEED, target.spawnRules(), chunkLoader);

    float x = spawnTx * (float) TILE_PX + TILE_PX / 2f;
    float y = spawnTy * (float) TILE_PX + TILE_PX / 2f;
    sim.step(0, List.of(new Command.SpawnPlayer(x, y)));
    playerId = sim.view().stableIdAt(0);
  }

  public Zone zone() {
    return zone;
  }

  public Sim sim() {
    return sim;
  }

  public FileChunkLoader chunkLoader() {
    return chunkLoader;
  }

  public int playerId() {
    return playerId;
  }

  public int coins() {
    return Math.max(0, carry.coins());
  }

  /**
   * 이번 틱 뒤에 존을 넘어가야 하는지 본다 — 가장자리를 넘었거나 포털 타일 위에 섰으면 전환한다.
   *
   * @return 전환했으면 참 (호출하는 쪽은 렌더러·ViewState 를 새 sim 에 다시 붙여야 한다)
   */
  public boolean checkTransition() {
    SimView view = sim.view();
    if (!isAlive(view)) {
      return false;
    }
    int tx = (int) StrictMath.floor(view.x(playerId) / TILE_PX);
    int ty = (int) StrictMath.floor(view.y(playerId) / TILE_PX);

    ZoneLink.Edge edge = edgeAt(tx, ty);
    if (edge != null) {
      Zone target = ZoneLink.target(zone, edge);
      if (target != null) {
        carryFrom(view);
        int[] entry = entryTile(edge, tx, ty);
        transitionTo(target, entry[0], entry[1]);
        return true;
      }
    }

    ChunkObject portal = portalAt(tx, ty);
    if (portal != null) {
      Zone target = zoneNamed(portal.props().get("zone"));
      if (target != null) {
        carryFrom(view);
        transitionTo(
            target,
            parseTile(portal.props().get("spawnTx"), tx),
            parseTile(portal.props().get("spawnTy"), ty));
        return true;
      }
    }
    return false;
  }

  private boolean isAlive(SimView view) {
    for (int i = 0; i < view.entityCount(); i++) {
      if (view.stableIdAt(i) == playerId) {
        return true;
      }
    }
    return false;
  }

  private void transitionTo(Zone target, int spawnTx, int spawnTy) {
    enter(target, spawnTx, spawnTy);
    applyCarry();
  }

  private void carryFrom(SimView view) {
    carry =
        new PlayerCarry(
            view.health(playerId), view.mana(playerId), Math.max(0, view.coins(playerId)));
  }

  /**
   * 넘어온 뒤 HP/MP/소지금을 되돌려 준다.
   *
   * <p>sim 상태를 밖에서 직접 고치지 않는다는 규칙(§6)을 지키려면 원래는 명령이 있어야 하지만, M0 에는 "플레이어 상태를 지정한다"는 명령이 없다. 소지금은
   * 클라이언트가 들고 표시만 하고, HP/MP 는 존을 넘으면 회복되는 것으로 둔다 — M1 세이브(§8.3)에서 제대로 실어 나른다.
   */
  private void applyCarry() {
    // HP/MP 는 새 sim 의 기본값(만땅)을 그대로 쓴다. 소지금만 클라이언트가 이어서 보여 준다.
  }

  private ZoneLink.Edge edgeAt(int tx, int ty) {
    int max = Zone.SIZE_TILES - 1 - EDGE_MARGIN_TILES;
    if (tx <= EDGE_MARGIN_TILES) {
      return ZoneLink.Edge.WEST;
    }
    if (tx >= max) {
      return ZoneLink.Edge.EAST;
    }
    if (ty <= EDGE_MARGIN_TILES) {
      return ZoneLink.Edge.NORTH;
    }
    if (ty >= max) {
      return ZoneLink.Edge.SOUTH;
    }
    return null;
  }

  /** 나간 방향의 반대편 가장자리로 들어오고, 넘지 않은 축의 좌표는 유지한다. */
  private static int[] entryTile(ZoneLink.Edge edge, int tx, int ty) {
    int far = Zone.SIZE_TILES - 1 - ENTRY_INSET_TILES;
    return switch (edge) {
      case WEST -> new int[] {far, ty};
      case EAST -> new int[] {ENTRY_INSET_TILES, ty};
      case NORTH -> new int[] {tx, far};
      case SOUTH -> new int[] {tx, ENTRY_INSET_TILES};
    };
  }

  private ChunkObject portalAt(int tx, int ty) {
    Chunk chunk =
        chunkLoader.apply(
            new ChunkCoord(
                Math.floorDiv(tx, ChunkFormat.SIZE), Math.floorDiv(ty, ChunkFormat.SIZE)));
    if (chunk == null) {
      return null;
    }
    for (ChunkObject object : chunk.objects()) {
      if ("portal".equals(object.type()) && object.tileX() == tx && object.tileY() == ty) {
        return object;
      }
    }
    return null;
  }

  private static Zone zoneNamed(String name) {
    if (name == null) {
      return null;
    }
    for (Zone candidate : Zone.values()) {
      if (candidate.chunkDir().equals(name)) {
        return candidate;
      }
    }
    return null;
  }

  private static int parseTile(String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
