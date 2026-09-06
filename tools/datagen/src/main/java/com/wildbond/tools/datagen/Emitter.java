package com.wildbond.tools.datagen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 스키마를 Java 소스로 내보낸다. 생성 코드는 손으로 고치지 않는다 (CLAUDE.md). */
final class Emitter {

  private static final String PKG = "com.wildbond.data";
  private static final String HEADER =
      """
      // 생성된 파일 — 손으로 고치지 않는다.
      // 원천: data/tables/%s
      // 생성기: tools/datagen (docs/architecture.md §8.1)
      """;

  private final Schema schema;
  private final Path outDir;

  Emitter(Schema schema, Path outDir) {
    this.schema = schema;
    this.outDir = outDir;
  }

  void emitAll() throws IOException {
    Path pkgDir = outDir.resolve(PKG.replace('.', '/'));
    Files.createDirectories(pkgDir);
    for (var entry : schema.enums().entrySet()) {
      emitEnum(pkgDir, entry.getKey(), entry.getValue());
    }
    for (Schema.Table table : schema.tables().values()) {
      emitRecord(pkgDir, table);
    }
    emitElementChart(pkgDir);
    emitGameData(pkgDir);
  }

  private void write(Path pkgDir, String typeName, String source) throws IOException {
    Files.writeString(pkgDir.resolve(typeName + ".java"), source, StandardCharsets.UTF_8);
  }

  private void emitEnum(Path pkgDir, String name, List<String> values) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER.formatted("enums.csv"));
    sb.append("\npackage ").append(PKG).append(";\n\n");
    sb.append("/** enums.csv 에서 생성. */\npublic enum ").append(name).append(" {\n");
    List<String> constants = new ArrayList<>();
    for (String v : values) {
      constants.add("  " + Names.constant(v) + "(\"" + v + "\")");
    }
    sb.append(String.join(",\n", constants)).append(";\n\n");
    sb.append("  private final String csvValue;\n\n");
    sb.append("  ")
        .append(name)
        .append("(String csvValue) {\n    this.csvValue = csvValue;\n  }\n\n");
    sb.append(
        "  /** CSV 에 적히는 문자열. */\n  public String csvValue() {\n    return csvValue;\n  }\n\n");
    sb.append("  /** CSV 문자열로 상수를 찾는다. */\n");
    sb.append("  public static ").append(name).append(" fromCsv(String value) {\n");
    sb.append("    for (").append(name).append(" candidate : values()) {\n");
    sb.append(
        "      if (candidate.csvValue.equals(value)) {\n        return candidate;\n      }\n    }\n");
    sb.append("    throw new IllegalArgumentException(\"")
        .append(name)
        .append(" 값이 아니다: \" + value);\n  }\n}\n");
    write(pkgDir, name, sb.toString());
  }

  private void emitRecord(Path pkgDir, Schema.Table table) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER.formatted(table.name() + ".csv"));
    sb.append("\npackage ").append(PKG).append(";\n\n");
    sb.append("/** ")
        .append(table.name())
        .append(".csv 한 행. */\npublic record ")
        .append(table.name())
        .append("(\n");
    List<String> components = new ArrayList<>();
    for (Schema.Column c : table.columns()) {
      components.add("    " + c.type().javaType() + " " + c.javaName());
    }
    sb.append(String.join(",\n", components)).append(") {}\n");
    write(pkgDir, table.name(), sb.toString());
  }

  private void emitElementChart(Path pkgDir) throws IOException {
    Schema.Matrix chart = schema.elementChart();
    int n = chart.axis().size();
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER.formatted("ElementChart.csv"));
    sb.append("\npackage ").append(PKG).append(";\n\n");
    sb.append(
        """
        /**
         * 속성 상성표 (docs/architecture.md §3.2). 행=공격, 열=방어.
         *
         * <p>대칭이 아니다 — 상성은 방향에 따라 다르다.
         */
        public final class ElementChart {

        """);
    sb.append("  private static final float[][] TABLE = {\n");
    for (int r = 0; r < n; r++) {
      List<String> cells = new ArrayList<>();
      for (int c = 0; c < n; c++) {
        cells.add(String.format(java.util.Locale.ROOT, "%.2ff", chart.values()[r][c]));
      }
      sb.append("    {")
          .append(String.join(", ", cells))
          .append("}, // ")
          .append(chart.axis().get(r))
          .append("\n");
    }
    sb.append("  };\n\n");
    sb.append(
        """
          private ElementChart() {}

          /** 공격 속성이 방어 속성에 주는 배수. */
          public static float multiplier(Element attacker, Element defender) {
            return TABLE[attacker.ordinal()][defender.ordinal()];
          }

          /** 축 길이. Element 상수 개수와 같다. */
          public static int size() {
            return TABLE.length;
          }
        }
        """);
    write(pkgDir, "ElementChart", sb.toString());
  }

  private void emitGameData(Path pkgDir) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER.formatted("*.csv"));
    sb.append(
        """

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

        """);

    List<Schema.Table> tables = new ArrayList<>(schema.tables().values());
    for (Schema.Table t : tables) {
      String field = Names.camel(t.name());
      sb.append("  private final Map<Integer, ")
          .append(t.name())
          .append("> ")
          .append(field)
          .append(";\n");
    }
    sb.append("\n  private GameData(");
    List<String> params = new ArrayList<>();
    for (Schema.Table t : tables) {
      params.add("Map<Integer, " + t.name() + "> " + Names.camel(t.name()));
    }
    sb.append(String.join(", ", params)).append(") {\n");
    for (Schema.Table t : tables) {
      String field = Names.camel(t.name());
      sb.append("    this.").append(field).append(" = Map.copyOf(").append(field).append(");\n");
    }
    sb.append("  }\n\n");

    // 접근자
    for (Schema.Table t : tables) {
      String field = Names.camel(t.name());
      sb.append("  /** id 로 찾는다. 없으면 예외. */\n");
      sb.append("  public ").append(t.name()).append(" ").append(field).append("(int id) {\n");
      sb.append("    ").append(t.name()).append(" found = ").append(field).append(".get(id);\n");
      sb.append("    if (found == null) {\n      throw new IllegalArgumentException(\"")
          .append(t.name())
          .append(" id 없음: \" + id);\n    }\n    return found;\n  }\n\n");
      sb.append("  /** 전체 행. */\n");
      sb.append("  public Collection<")
          .append(t.name())
          .append("> all")
          .append(t.name())
          .append("() {\n    return ")
          .append(field)
          .append(".values();\n  }\n\n");
    }

    sb.append(
        """
          /** tables 디렉터리의 CSV 를 모두 읽는다. */
          public static GameData load(Path tablesDir) throws IOException {
        """);
    for (Schema.Table t : tables) {
      String field = Names.camel(t.name());
      sb.append("    Map<Integer, ")
          .append(t.name())
          .append("> ")
          .append(field)
          .append(" = new LinkedHashMap<>();\n");
      sb.append("    for (List<String> row : rows(tablesDir.resolve(\"")
          .append(t.name())
          .append(".csv\"))) {\n");
      sb.append("      ")
          .append(t.name())
          .append(" parsed = parse")
          .append(t.name())
          .append("(row);\n");
      sb.append("      ").append(field).append(".put(parsed.id(), parsed);\n    }\n");
    }
    sb.append("    return new GameData(");
    List<String> args = new ArrayList<>();
    for (Schema.Table t : tables) {
      args.add(Names.camel(t.name()));
    }
    sb.append(String.join(", ", args)).append(");\n  }\n\n");

    // 표별 파서
    for (Schema.Table t : tables) {
      sb.append("  private static ")
          .append(t.name())
          .append(" parse")
          .append(t.name())
          .append("(List<String> row) {\n    return new ")
          .append(t.name())
          .append("(\n");
      List<String> exprs = new ArrayList<>();
      for (int i = 0; i < t.columns().size(); i++) {
        exprs.add("        " + parseExpr(t.columns().get(i), i));
      }
      sb.append(String.join(",\n", exprs)).append(");\n  }\n\n");
    }

    sb.append(
        """
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
            String[] parts = cell.split("\\\\|", -1);
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
            String[] parts = cell.split("\\\\|", -1);
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
            return cell.split("\\\\|", -1);
          }
        }
        """);
    write(pkgDir, "GameData", sb.toString());
  }

  /** 한 컬럼을 row.get(i) 로부터 만드는 식. */
  private static String parseExpr(Schema.Column column, int index) {
    ColumnType type = column.type();
    String cell = "row.get(" + index + ")";
    if (type.array()) {
      return switch (type.kind()) {
        case INT, REF -> "intArray(" + cell + ")";
        case FLOAT -> "floatArray(" + cell + ")";
        default -> "stringArray(" + cell + ")";
      };
    }
    String nonNull =
        switch (type.kind()) {
          case INT, REF -> "Integer.parseInt(" + cell + ")";
          case FLOAT -> "Float.parseFloat(" + cell + ")";
          case BOOL -> "Boolean.parseBoolean(" + cell + ")";
          case STRING -> cell;
          case ENUM -> type.refName() + ".fromCsv(" + cell + ")";
        };
    if (!type.nullable()) {
      return nonNull;
    }
    if (type.kind() == ColumnType.Kind.STRING) {
      return cell + ".isEmpty() ? null : " + cell;
    }
    return cell + ".isEmpty() ? null : " + nonNull;
  }
}
