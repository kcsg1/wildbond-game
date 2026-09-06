package com.wildbond.data.chunk;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** .wbc 바이너리를 {@link Chunk} 로 읽는다 (docs/architecture.md §8.2). */
public final class ChunkReader {

  private ChunkReader() {}

  public static Chunk read(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return read(in);
    }
  }

  public static Chunk read(InputStream in) throws IOException {
    DataInputStream data = new DataInputStream(in);

    byte[] magic = new byte[ChunkFormat.MAGIC.length()];
    data.readFully(magic);
    if (!ChunkFormat.MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
      throw new IOException("청크 magic 이 다르다: " + new String(magic, StandardCharsets.US_ASCII));
    }
    int version = data.readUnsignedByte();
    if (version != ChunkFormat.VERSION) {
      throw new IOException("청크 버전이 다르다: " + version + " (지원: " + ChunkFormat.VERSION + ")");
    }
    int cx = data.readInt();
    int cy = data.readInt();
    int payloadLength = data.readInt();
    int compressedLength = data.readInt();

    byte[] compressed = new byte[compressedLength];
    data.readFully(compressed);
    byte[] payload = Zstd.decompress(compressed, payloadLength);

    return decodePayload(new ChunkCoord(cx, cy), payload);
  }

  private static Chunk decodePayload(ChunkCoord coord, byte[] payload) throws IOException {
    DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));

    int[] ground = new int[ChunkFormat.TILE_COUNT];
    for (int i = 0; i < ground.length; i++) {
      ground[i] = data.readUnsignedShort();
    }
    int[] detail = new int[ChunkFormat.TILE_COUNT];
    for (int i = 0; i < detail.length; i++) {
      detail[i] = data.readUnsignedShort();
    }
    byte[] collision = new byte[ChunkFormat.TILE_COUNT];
    data.readFully(collision);

    int objectCount = data.readUnsignedShort();
    List<ChunkObject> objects = new ArrayList<>(objectCount);
    for (int i = 0; i < objectCount; i++) {
      String type = data.readUTF();
      int tileX = data.readUnsignedShort();
      int tileY = data.readUnsignedShort();
      int propCount = data.readUnsignedShort();
      Map<String, String> props = new LinkedHashMap<>();
      for (int p = 0; p < propCount; p++) {
        String key = data.readUTF();
        String value = data.readUTF();
        props.put(key, value);
      }
      objects.add(new ChunkObject(type, tileX, tileY, props));
    }

    return new Chunk(coord, ground, detail, collision, objects);
  }
}
