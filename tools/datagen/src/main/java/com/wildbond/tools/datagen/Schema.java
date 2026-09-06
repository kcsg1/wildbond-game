package com.wildbond.tools.datagen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** tables 디렉터리를 읽어 열거형·표 정의와 원본 셀을 담는다. 검증은 {@link Validator} 가 한다. */
final class Schema {

  record Column(String csvName, String javaName, ColumnType type) {}

  record Table(String name, List<Column> columns, List<Csv.Row> rows, Path file) {

    int columnIndex(String csvName) {
      for (int i = 0; i < columns.size(); i++) {
        if (columns.get(i).csvName().equals(csvName)) {
          return i;
        }
      }
      return -1;
    }
  }

  /** 행=공격, 열=방어 순서의 9x9 상성표. */
  record Matrix(String name, List<String> axis, double[][] values, Path file) {}

  private final Map<String, List<String>> enums = new LinkedHashMap<>();
  private final Map<String, Table> tables = new LinkedHashMap<>();
  private Matrix elementChart;

  Map<String, List<String>> enums() {
    return enums;
  }

  Map<String, Table> tables() {
    return tables;
  }

  Matrix elementChart() {
    return elementChart;
  }

  static Schema load(Path tablesDir) throws IOException {
    Schema schema = new Schema();
    Path enumsFile = tablesDir.resolve("enums.csv");
    if (!Files.isRegularFile(enumsFile)) {
      throw new GenException(enumsFile + ": enums.csv 가 없다");
    }
    schema.loadEnums(enumsFile);

    List<Path> csvFiles;
    try (var stream = Files.list(tablesDir)) {
      csvFiles = stream.filter(p -> p.getFileName().toString().endsWith(".csv")).sorted().toList();
    }
    for (Path file : csvFiles) {
      String fileName = file.getFileName().toString();
      String tableName = fileName.substring(0, fileName.length() - 4);
      if (tableName.equals("enums")) {
        continue;
      }
      if (tableName.equals("ElementChart")) {
        schema.elementChart = loadMatrix(file, tableName);
      } else {
        schema.tables.put(tableName, loadTable(file, tableName));
      }
    }
    if (schema.elementChart == null) {
      throw new GenException(tablesDir + ": ElementChart.csv 가 없다");
    }
    return schema;
  }

  private void loadEnums(Path file) throws IOException {
    List<Csv.Row> rows = Csv.read(file);
    if (rows.isEmpty()) {
      throw new GenException(file + ": 내용이 없다");
    }
    for (int i = 1; i < rows.size(); i++) {
      Csv.Row row = rows.get(i);
      String where = file.getFileName() + ":" + row.lineNumber();
      if (row.cells().size() < 2) {
        throw new GenException(where + ": name,values 두 칸이 필요하다");
      }
      String name = row.cells().get(0);
      List<String> values = List.of(row.cells().get(1).split("\\|"));
      if (values.isEmpty() || values.get(0).isBlank()) {
        throw new GenException(where + ": 값이 비어 있다");
      }
      if (enums.put(name, values) != null) {
        throw new GenException(where + ": 열거형 이름 중복: " + name);
      }
    }
  }

  private static Table loadTable(Path file, String tableName) throws IOException {
    List<Csv.Row> rows = Csv.read(file);
    if (rows.size() < 2) {
      throw new GenException(file + ": 헤더 행과 타입 행이 필요하다");
    }
    List<String> header = rows.get(0).cells();
    List<String> types = rows.get(1).cells();
    if (header.size() != types.size()) {
      throw new GenException(
          file + ": 헤더 " + header.size() + "칸, 타입 행 " + types.size() + "칸으로 개수가 다르다");
    }
    List<Column> columns = new ArrayList<>();
    for (int i = 0; i < header.size(); i++) {
      String where = file.getFileName() + " 컬럼 '" + header.get(i) + "'";
      columns.add(
          new Column(
              header.get(i), Names.camel(header.get(i)), ColumnType.parse(types.get(i), where)));
    }
    if (columns.isEmpty() || !columns.get(0).csvName().equals("id")) {
      throw new GenException(file + ": 첫 컬럼은 반드시 id 여야 한다");
    }
    return new Table(tableName, columns, rows.subList(2, rows.size()), file);
  }

  private static Matrix loadMatrix(Path file, String name) throws IOException {
    List<Csv.Row> rows = Csv.read(file);
    if (rows.size() < 2) {
      throw new GenException(file + ": 헤더 행과 최소 1행이 필요하다");
    }
    List<String> header = rows.get(0).cells();
    List<String> axis = new ArrayList<>(header.subList(1, header.size()));
    int n = axis.size();
    if (rows.size() - 1 != n) {
      throw new GenException(
          file + ": " + n + "x" + n + " 이어야 하는데 데이터 행이 " + (rows.size() - 1) + "개다");
    }
    double[][] values = new double[n][n];
    for (int r = 0; r < n; r++) {
      Csv.Row row = rows.get(r + 1);
      String where = file.getFileName() + ":" + row.lineNumber();
      if (row.cells().size() != n + 1) {
        throw new GenException(where + ": " + (n + 1) + "칸이어야 하는데 " + row.cells().size() + "칸이다");
      }
      if (!row.cells().get(0).equals(axis.get(r))) {
        throw new GenException(
            where + ": 행 순서가 헤더와 다르다 — '" + axis.get(r) + "' 자리에 '" + row.cells().get(0) + "'");
      }
      for (int c = 0; c < n; c++) {
        String cell = row.cells().get(c + 1);
        double v;
        try {
          v = Double.parseDouble(cell);
        } catch (NumberFormatException e) {
          throw new GenException(where + ": 숫자가 아니다: '" + cell + "'");
        }
        if (v < 0.25 || v > 4.0) {
          throw new GenException(where + ": 상성 배수는 0.25~4.0 이어야 한다: " + v);
        }
        values[r][c] = v;
      }
    }
    return new Matrix(name, List.copyOf(axis), values, file);
  }
}
