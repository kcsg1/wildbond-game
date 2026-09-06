package com.wildbond.tools.chunk;

import com.wildbond.data.chunk.ChunkObject;
import java.util.List;

/**
 * Tiled .tmx 파싱 결과 (컴파일 전 중간 표현). ground/detail 은 맵 전체 크기(width*height)의 타일 GID 배열, 오브젝트의
 * tileX/tileY 는 월드 타일 좌표다.
 */
record TmxMap(
    int width, int height, int firstGid, int[] ground, int[] detail, List<ChunkObject> objects) {}
