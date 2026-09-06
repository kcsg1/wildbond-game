package com.wildbond.tools.datagen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 최소 CSV 리더. '#' 로 시작하는 줄과 빈 줄은 건너뛰고, 큰따옴표 인용을 지원한다. */
final class Csv {

  private Csv() {}

  /** 의미 있는 줄만 남긴 2차원 셀 목록. 원본 줄 번호를 함께 돌려준다. */
  record Row(int lineNumber, List<String> cells) {}

  static List<Row> read(Path file) throws IOException {
    List<Row> rows = new ArrayList<>();
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      rows.add(new Row(i + 1, splitLine(line)));
    }
    return rows;
  }

  private static List<String> splitLine(String line) {
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
}
