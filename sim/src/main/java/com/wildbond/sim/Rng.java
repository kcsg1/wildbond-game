package com.wildbond.sim;

/**
 * xoshiro256** (Blackman/Vigna, public domain), 스트림 3개로 분리. docs/architecture.md §4.1, §4.3.
 *
 * <p>같은 시드는 항상 같은 시퀀스를 낸다 — 리플레이 결정성의 전제. {@code java.util.Random}·{@code Math.random()} 은 sim 안에서
 * 금지되어 있다(ArchitectureTest).
 */
public final class Rng {

  /** 용도별로 분리된 난수열 — 하나를 더 뽑아도 다른 스트림의 결과에 영향을 주지 않는다. */
  public enum Stream {
    COMBAT,
    SPAWN,
    LOOT
  }

  private final long[][] state = new long[Stream.values().length][4];

  public Rng(long seed) {
    long accumulator = seed;
    for (Stream stream : Stream.values()) {
      long[] s = state[stream.ordinal()];
      for (int i = 0; i < 4; i++) {
        accumulator += 0x9E3779B97F4A7C15L;
        long z = accumulator;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        s[i] = z ^ (z >>> 31);
      }
    }
  }

  /** 다음 64bit 값. */
  public long nextLong(Stream stream) {
    long[] s = state[stream.ordinal()];
    long s0 = s[0];
    long s1 = s[1];
    long s2 = s[2];
    long s3 = s[3];

    long result = Long.rotateLeft(s1 * 5, 7) * 9;

    long t = s1 << 17;
    s2 ^= s0;
    s3 ^= s1;
    s1 ^= s2;
    s0 ^= s3;
    s2 ^= t;
    s3 = Long.rotateLeft(s3, 45);

    s[0] = s0;
    s[1] = s1;
    s[2] = s2;
    s[3] = s3;
    return result;
  }

  /** [0, bound) 균등 정수. bound 는 양수여야 한다. */
  public int nextInt(Stream stream, int bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound 는 양수여야 한다: " + bound);
    }
    long r = nextLong(stream) >>> 1;
    return (int) (r % bound);
  }

  /** [0, 1) 균등 실수, 24bit 정밀도. */
  public float nextFloat(Stream stream) {
    return (nextLong(stream) >>> 40) * (1.0f / (1 << 24));
  }
}
