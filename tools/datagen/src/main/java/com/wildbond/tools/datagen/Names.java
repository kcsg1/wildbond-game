package com.wildbond.tools.datagen;

/** snake_case 컬럼 이름을 Java 식별자로 바꾼다. */
final class Names {

  private Names() {}

  /** base_hp -> baseHp */
  static String camel(String snake) {
    StringBuilder out = new StringBuilder();
    boolean upper = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_' || c == '-' || c == ' ') {
        upper = true;
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(i == 0 ? Character.toLowerCase(c) : c);
      }
    }
    return out.toString();
  }

  /** none -> NONE, vine_whip -> VINE_WHIP */
  static String constant(String value) {
    return value.replace('-', '_').replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
  }
}
