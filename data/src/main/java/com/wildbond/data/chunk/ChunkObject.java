package com.wildbond.data.chunk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 청크 안의 배치 오브젝트(자원 노드·스폰 포인트 등). docs/architecture.md §8.2. */
public record ChunkObject(String type, int tileX, int tileY, Map<String, String> props) {

  public ChunkObject {
    // Map.copyOf 는 순서를 보장하지 않는다 — 청크 바이트를 결정적으로 쓰려면 삽입 순서를 지켜야 한다.
    props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
  }
}
