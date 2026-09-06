package com.wildbond.tools.datagen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 표를 검증한다 — 중복 id, 칸 수, 타입, 값 범위, 배열 길이, 열거형 값, 참조 무결성.
 *
 * <p>실패는 모아서 한 번에 보고한다. 파일·행·컬럼을 지목한다.
 */
final class Validator {

  private final Schema schema;
  private final List<String> problems = new ArrayList<>();

  Validator(Schema schema) {
    this.schema = schema;
  }

  void validate() {
    // 표별 id 집합 — 참조 무결성 검사에 쓴다.
    Map<String, Set<Integer>> ids = new java.util.LinkedHashMap<>();
    for (Schema.Table table : schema.tables().values()) {
      ids.put(table.name(), collectIds(table));
    }
    for (Schema.Table table : schema.tables().values()) {
      validateRows(table, ids);
    }
    validateElementChart();

    if (!problems.isEmpty()) {
      throw new GenException(
          "정적 데이터 검증 실패 " + problems.size() + "건:\n  - " + String.join("\n  - ", problems));
    }
  }

  private Set<Integer> collectIds(Schema.Table table) {
    Set<Integer> ids = new java.util.LinkedHashSet<>();
    for (Csv.Row row : table.rows()) {
      String where = where(table, row);
      if (row.cells().isEmpty() || row.cells().get(0).isBlank()) {
        problems.add(where + ": id 가 비어 있다");
        continue;
      }
      try {
        int id = Integer.parseInt(row.cells().get(0));
        if (!ids.add(id)) {
          problems.add(where + ": id 중복: " + id);
        }
      } catch (NumberFormatException e) {
        problems.add(where + ": id 가 정수가 아니다: '" + row.cells().get(0) + "'");
      }
    }
    return ids;
  }

  private void validateRows(Schema.Table table, Map<String, Set<Integer>> ids) {
    for (Csv.Row row : table.rows()) {
      String where = where(table, row);
      if (row.cells().size() != table.columns().size()) {
        problems.add(
            where + ": " + table.columns().size() + "칸이어야 하는데 " + row.cells().size() + "칸이다");
        continue;
      }
      for (int i = 0; i < table.columns().size(); i++) {
        Schema.Column column = table.columns().get(i);
        validateCell(where + " 컬럼 '" + column.csvName() + "'", column, row.cells().get(i), ids);
      }
    }
  }

  private void validateCell(
      String where, Schema.Column column, String cell, Map<String, Set<Integer>> ids) {
    ColumnType type = column.type();
    if (cell.isEmpty()) {
      if (!type.nullable()) {
        problems.add(where + ": 빈 칸을 허용하지 않는다 (타입에 ? 가 없다)");
      }
      return;
    }
    String[] parts = type.array() ? cell.split("\\|", -1) : new String[] {cell};
    if (type.array() && type.arrayLength() > 0 && parts.length != type.arrayLength()) {
      problems.add(where + ": 원소가 " + type.arrayLength() + "개여야 하는데 " + parts.length + "개다");
      return;
    }
    for (String part : parts) {
      validateScalar(where, type, part.strip(), ids);
    }
  }

  private void validateScalar(
      String where, ColumnType type, String value, Map<String, Set<Integer>> ids) {
    switch (type.kind()) {
      case INT, REF -> {
        int parsed;
        try {
          parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
          problems.add(where + ": 정수가 아니다: '" + value + "'");
          return;
        }
        checkRange(where, type, parsed);
        if (type.kind() == ColumnType.Kind.REF) {
          Set<Integer> target = ids.get(type.refName());
          if (target == null) {
            problems.add(where + ": 참조 대상 표가 없다: " + type.refName());
          } else if (!target.contains(parsed)) {
            problems.add(where + ": " + type.refName() + " 에 id " + parsed + " 가 없다");
          }
        }
      }
      case FLOAT -> {
        try {
          checkRange(where, type, Double.parseDouble(value));
        } catch (NumberFormatException e) {
          problems.add(where + ": 실수가 아니다: '" + value + "'");
        }
      }
      case BOOL -> {
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("true") && !lower.equals("false")) {
          problems.add(where + ": true/false 여야 한다: '" + value + "'");
        }
      }
      case STRING -> {
        // 제약 없음
      }
      case ENUM -> {
        List<String> values = schema.enums().get(type.refName());
        if (values == null) {
          problems.add(where + ": enums.csv 에 없는 열거형: " + type.refName());
        } else if (!values.contains(value)) {
          problems.add(
              where + ": " + type.refName() + " 에 없는 값: '" + value + "' (가능: " + values + ")");
        }
      }
    }
  }

  private void checkRange(String where, ColumnType type, double value) {
    if (type.min() != null && value < type.min()) {
      problems.add(where + ": " + value + " 는 최소 " + type.min() + " 보다 작다");
    }
    if (type.max() != null && value > type.max()) {
      problems.add(where + ": " + value + " 는 최대 " + type.max() + " 보다 크다");
    }
  }

  private void validateElementChart() {
    Schema.Matrix chart = schema.elementChart();
    List<String> element = schema.enums().get("Element");
    if (element == null) {
      problems.add("enums.csv: Element 열거형이 없다");
      return;
    }
    if (!chart.axis().equals(element)) {
      problems.add(
          chart.file().getFileName()
              + ": 축이 Element 열거형과 다르다 — 표 "
              + chart.axis()
              + " vs enums "
              + element);
    }
    Set<String> seen = new HashSet<>(chart.axis());
    if (seen.size() != chart.axis().size()) {
      problems.add(chart.file().getFileName() + ": 축에 중복이 있다");
    }
  }

  private static String where(Schema.Table table, Csv.Row row) {
    return table.file().getFileName() + ":" + row.lineNumber();
  }
}
