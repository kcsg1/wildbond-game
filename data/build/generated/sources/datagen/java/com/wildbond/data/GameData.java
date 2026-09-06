// 생성된 파일 — 손으로 고치지 않는다.
// 원천: data/tables/*.csv
// 생성기: tools/datagen (docs/architecture.md §8.1)

package com.wildbond.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정적 게임 데이터 (docs/architecture.md §8.1). CSV 를 읽어 불변 표로 보관한다.
 *
 * <p>M0 에서는 CSV 를 런타임에 읽는다. M1 에서 FlatBuffers 바이너리로 바꾼다.
 */
public final class GameData {

  private final Map<Integer, Item> item;
  private final Map<Integer, PalSpecies> palSpecies;
  private final Map<Integer, Skill> skill;
  private final Map<Integer, Tile> tile;

  private GameData(Map<Integer, Item> item, Map<Integer, PalSpecies> palSpecies, Map<Integer, Skill> skill, Map<Integer, Tile> tile) {
    this.item = Map.copyOf(item);
    this.palSpecies = Map.copyOf(palSpecies);
    this.skill = Map.copyOf(skill);
    this.tile = Map.copyOf(tile);
  }

  /** id 로 찾는다. 없으면 예외. */
  public Item item(int id) {
    Item found = item.get(id);
    if (found == null) {
      throw new IllegalArgumentException("Item id 없음: " + id);
    }
    return found;
  }

  /** 전체 행. */
  public Collection<Item> allItem() {
    return item.values();
  }

  /** id 로 찾는다. 없으면 예외. */
  public PalSpecies palSpecies(int id) {
    PalSpecies found = palSpecies.get(id);
    if (found == null) {
      throw new IllegalArgumentException("PalSpecies id 없음: " + id);
    }
    return found;
  }

  /** 전체 행. */
  public Collection<PalSpecies> allPalSpecies() {
    return palSpecies.values();
  }

  /** id 로 찾는다. 없으면 예외. */
  public Skill skill(int id) {
    Skill found = skill.get(id);
    if (found == null) {
      throw new IllegalArgumentException("Skill id 없음: " + id);
    }
    return found;
  }

  /** 전체 행. */
  public Collection<Skill> allSkill() {
    return skill.values();
  }

  /** id 로 찾는다. 없으면 예외. */
  public Tile tile(int id) {
    Tile found = tile.get(id);
    if (found == null) {
      throw new IllegalArgumentException("Tile id 없음: " + id);
    }
    return found;
  }

  /** 전체 행. */
  public Collection<Tile> allTile() {
    return tile.values();
  }

  /** tables 디렉터리의 CSV 를 모두 읽는다. */
  public static GameData load(Path tablesDir) throws IOException {
    Map<Integer, Item> item = new LinkedHashMap<>();
    for (List<String> row : rows(tablesDir.resolve("Item.csv"))) {
      Item parsed = parseItem(row);
      item.put(parsed.id(), parsed);
    }
    Map<Integer, PalSpecies> palSpecies = new LinkedHashMap<>();
    for (List<String> row : rows(tablesDir.resolve("PalSpecies.csv"))) {
      PalSpecies parsed = parsePalSpecies(row);
      palSpecies.put(parsed.id(), parsed);
    }
    Map<Integer, Skill> skill = new LinkedHashMap<>();
    for (List<String> row : rows(tablesDir.resolve("Skill.csv"))) {
      Skill parsed = parseSkill(row);
      skill.put(parsed.id(), parsed);
    }
    Map<Integer, Tile> tile = new LinkedHashMap<>();
    for (List<String> row : rows(tablesDir.resolve("Tile.csv"))) {
      Tile parsed = parseTile(row);
      tile.put(parsed.id(), parsed);
    }
    return new GameData(item, palSpecies, skill, tile);
  }

  private static Item parseItem(List<String> row) {
    return new Item(
        Integer.parseInt(row.get(0)),
        row.get(1),
        ItemCategory.fromCsv(row.get(2)),
        Integer.parseInt(row.get(3)),
        Float.parseFloat(row.get(4)),
        Integer.parseInt(row.get(5)),
        row.get(6).isEmpty() ? null : Integer.parseInt(row.get(6)),
        row.get(7).isEmpty() ? null : Integer.parseInt(row.get(7)),
        row.get(8).isEmpty() ? null : Integer.parseInt(row.get(8)),
        row.get(9).isEmpty() ? null : Float.parseFloat(row.get(9)),
        row.get(10));
  }

  private static PalSpecies parsePalSpecies(List<String> row) {
    return new PalSpecies(
        Integer.parseInt(row.get(0)),
        row.get(1),
        Element.fromCsv(row.get(2)),
        row.get(3).isEmpty() ? null : Element.fromCsv(row.get(3)),
        Temperament.fromCsv(row.get(4)),
        Integer.parseInt(row.get(5)),
        Integer.parseInt(row.get(6)),
        Integer.parseInt(row.get(7)),
        intArray(row.get(8)),
        row.get(9).isEmpty() ? null : Integer.parseInt(row.get(9)),
        intArray(row.get(10)),
        Integer.parseInt(row.get(11)),
        Boolean.parseBoolean(row.get(12)),
        Float.parseFloat(row.get(13)),
        Float.parseFloat(row.get(14)),
        Integer.parseInt(row.get(15)),
        row.get(16));
  }

  private static Skill parseSkill(List<String> row) {
    return new Skill(
        Integer.parseInt(row.get(0)),
        row.get(1),
        Element.fromCsv(row.get(2)),
        Integer.parseInt(row.get(3)),
        Integer.parseInt(row.get(4)),
        Integer.parseInt(row.get(5)),
        Integer.parseInt(row.get(6)),
        HitShape.fromCsv(row.get(7)),
        Integer.parseInt(row.get(8)),
        Integer.parseInt(row.get(9)),
        row.get(10));
  }

  private static Tile parseTile(List<String> row) {
    return new Tile(
        Integer.parseInt(row.get(0)),
        row.get(1),
        TileCollision.fromCsv(row.get(2)),
        Biome.fromCsv(row.get(3)),
        Float.parseFloat(row.get(4)),
        Float.parseFloat(row.get(5)),
        Boolean.parseBoolean(row.get(6)),
        row.get(7).isEmpty() ? null : Integer.parseInt(row.get(7)));
  }

  private static List<List<String>> rows(Path file) throws IOException {
    List<List<String>> out = new ArrayList<>();
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    int kept = 0;
    for (String line : lines) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      kept++;
      if (kept <= 2) {
        continue; // 헤더 행, 타입 행
      }
      out.add(split(line));
    }
    return out;
  }

  private static List<String> split(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        cells.add(cur.toString().strip());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    cells.add(cur.toString().strip());
    return cells;
  }

  private static int[] intArray(String cell) {
    if (cell.isEmpty()) {
      return new int[0];
    }
    String[] parts = cell.split("\\|", -1);
    int[] out = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = Integer.parseInt(parts[i].strip());
    }
    return out;
  }

  private static float[] floatArray(String cell) {
    if (cell.isEmpty()) {
      return new float[0];
    }
    String[] parts = cell.split("\\|", -1);
    float[] out = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = Float.parseFloat(parts[i].strip());
    }
    return out;
  }

  private static String[] stringArray(String cell) {
    if (cell.isEmpty()) {
      return new String[0];
    }
    return cell.split("\\|", -1);
  }
}
