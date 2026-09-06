package com.wildbond.data.chunk;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** {@link Chunk} 를 .wbc 바이너리로 쓴다 (docs/architecture.md §8.2). */
public final class ChunkWriter {

  private ChunkWriter() {}

  public static void write(Chunk chunk, Path file) throws IOException {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (OutputStream out = Files.newOutputStream(file)) {
      write(chunk, out);
    }
  }

  public static void write(Chunk chunk, OutputStream out) throws IOException {
    byte[] payload = encodePayload(chunk);
    byte[] compressed = Zstd.compress(payload);

    DataOutputStream data = new DataOutputStream(out);
    data.write(ChunkFormat.MAGIC.getBytes(StandardCharsets.US_ASCII));
    data.writeByte(ChunkFormat.VERSION);
    data.writeInt(chunk.coord().cx());
    data.writeInt(chunk.coord().cy());
    data.writeInt(payload.length);
    data.writeInt(compressed.length);
    data.write(compressed);
    data.flush();
  }

  private static byte[] encodePayload(Chunk chunk) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(buffer);

    for (int gid : chunk.ground()) {
      data.writeShort(gid);
    }
    for (int gid : chunk.detail()) {
      data.writeShort(gid);
    }
    data.write(chunk.collision());

    data.writeShort(chunk.objects().size());
    for (ChunkObject object : chunk.objects()) {
      data.writeUTF(object.type());
      data.writeShort(object.tileX());
      data.writeShort(object.tileY());
      Map<String, String> props = object.props();
      data.writeShort(props.size());
      for (Map.Entry<String, String> entry : props.entrySet()) {
        data.writeUTF(entry.getKey());
        data.writeUTF(entry.getValue());
      }
    }

    data.flush();
    return buffer.toByteArray();
  }
}
