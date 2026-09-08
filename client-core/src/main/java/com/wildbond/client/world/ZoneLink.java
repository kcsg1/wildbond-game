package com.wildbond.client.world;

/**
 * 존 가장자리를 넘었을 때 어디로 가는지 (docs/architecture.md D-16).
 *
 * <p>M0 배치: 마을 —(동/서)— 들판. 굴은 들판 안의 포털 오브젝트로만 드나든다.
 */
public final class ZoneLink {

  /** 존의 어느 가장자리인가. */
  public enum Edge {
    NORTH,
    EAST,
    SOUTH,
    WEST
  }

  private ZoneLink() {}

  /** 이 방향으로 나가면 도착할 존. 막힌 가장자리면 null. */
  public static Zone target(Zone from, Edge edge) {
    return switch (from) {
      case VILLAGE -> edge == Edge.EAST ? Zone.FIELD : null;
      case FIELD -> edge == Edge.WEST ? Zone.VILLAGE : null;
      case CAVE -> null; // 굴 가장자리는 전부 바위다.
    };
  }
}
