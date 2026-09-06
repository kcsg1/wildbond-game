package com.wildbond.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 생성된 정적 테이블과 로더를 검증한다 (docs/architecture.md §8.1).
 *
 * <p>CSV 경로는 Gradle 이 시스템 프로퍼티 {@code wildbond.tables} 로 넘긴다.
 */
class GameDataTest {

  private static GameData data;

  @BeforeAll
  static void loadTables() throws IOException {
    Path dir = Path.of(System.getProperty("wildbond.tables", "tables"));
    assertThat(Files.isDirectory(dir)).as("tables 디렉터리: %s", dir.toAbsolutePath()).isTrue();
    data = GameData.load(dir);
  }

  @Test
  void loadsSampleRowCounts() {
    assertThat(data.allPalSpecies()).hasSize(3);
    assertThat(data.allSkill()).hasSize(4);
    assertThat(data.allItem()).hasSize(6);
    assertThat(data.allTile()).hasSize(5);
  }

  @Test
  void parsesScalarsAndEnums() {
    PalSpecies mossling = data.palSpecies(1);
    assertThat(mossling.nameKey()).isEqualTo("pal.mossling");
    assertThat(mossling.element1()).isEqualTo(Element.GRASS);
    assertThat(mossling.temperament()).isEqualTo(Temperament.TIMID);
    assertThat(mossling.baseHp()).isEqualTo(70);
    assertThat(mossling.captureRate()).isEqualTo(0.5f);
    assertThat(mossling.rideable()).isFalse();
  }

  @Test
  void emptyCellsBecomeNullForNullableColumns() {
    assertThat(data.palSpecies(1).element2()).as("mossling 은 단일 속성").isNull();
    assertThat(data.palSpecies(3).partnerSkill()).as("boulderox 는 파트너 스킬 없음").isNull();
    assertThat(data.item(4).sphereTier()).as("나무는 포획구가 아니다").isNull();
    assertThat(data.tile(1).itemDrop()).as("풀 타일은 채굴 산출물 없음").isNull();
  }

  @Test
  void parsesArraysWithFixedLength() {
    PalSpecies mossling = data.palSpecies(1);
    assertThat(mossling.work()).as("노동 적성은 WorkType 개수만큼").hasSize(WorkType.values().length);
    assertThat(mossling.work()[WorkType.PLANTING.ordinal()]).isEqualTo(3);
    assertThat(mossling.skills()).containsExactly(4, 1);
  }

  @Test
  void referencesPointAtExistingRows() {
    Integer partnerSkill = data.palSpecies(1).partnerSkill();
    assertThat(partnerSkill).isNotNull();
    assertThat(data.skill(partnerSkill).nameKey()).isEqualTo("skill.vine_whip");
    Integer drop = data.tile(4).itemDrop();
    assertThat(drop).isNotNull();
    assertThat(data.item(drop).nameKey()).isEqualTo("item.stone");
  }

  @Test
  void sphereTiersFollowCaptureFormula() {
    // §3.2 — 포획구 단계별 배수 1.0 / 1.4 / 1.8
    assertThat(data.item(1).sphereMultiplier()).isEqualTo(1.0f);
    assertThat(data.item(2).sphereMultiplier()).isEqualTo(1.4f);
    assertThat(data.item(3).sphereMultiplier()).isEqualTo(1.8f);
  }

  @Test
  void unknownIdFails() {
    assertThatThrownBy(() -> data.palSpecies(9999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("9999");
  }
}
