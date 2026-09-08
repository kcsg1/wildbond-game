package com.wildbond.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** data/tables/*.csv 가 생성 코드로 정확히 읽히는지 — docs/architecture.md §3.1, §8.1 (v0.5 표 기준). */
class GameDataTest {

  private static GameData data;

  @BeforeAll
  static void load() throws IOException {
    Path dir = Path.of(System.getProperty("wildbond.tables", "tables"));
    data = GameData.load(dir);
  }

  @Test
  void loadsEveryTableWithExpectedRowCounts() {
    assertThat(data.allMonster()).hasSize(3);
    assertThat(data.allSkill()).hasSize(4);
    assertThat(data.allItem()).hasSize(8);
    assertThat(data.allTile()).hasSize(5);
    assertThat(data.allLootTable()).hasSize(7);
  }

  @Test
  void parsesScalarsEnumsAndArrays() {
    Monster deer = data.monster(1);
    assertThat(deer.nameKey()).isEqualTo("monster.deer");
    assertThat(deer.temperament()).isEqualTo(Temperament.PASSIVE);
    assertThat(deer.element()).isEqualTo(Element.NONE);
    assertThat(deer.baseHp()).isEqualTo(40);
    assertThat(deer.skills()).as("사슴은 공격 스킬이 없다").isEmpty();
    assertThat(data.monster(2).skills()).containsExactly(1);
    assertThat(data.monster(3).footprint()).as("골렘은 2×2").isEqualTo(2);
  }

  @Test
  void optionalColumnsBecomeNull() {
    assertThat(data.item(2).buyPrice()).as("가죽은 살 수 없다").isNull();
    assertThat(data.item(7).buyPrice()).isEqualTo(30);
    assertThat(data.item(7).healHp()).isEqualTo(40);
    assertThat(data.item(7).healMp()).isNull();
  }

  @Test
  void referencesResolveToRealRows() {
    for (LootTable row : data.allLootTable()) {
      assertThat(data.monster(row.monster())).isNotNull();
      assertThat(data.item(row.item())).isNotNull();
      assertThat(row.minCount()).isLessThanOrEqualTo(row.maxCount());
    }
    for (int skillId : data.monster(2).skills()) {
      assertThat(data.skill(skillId)).isNotNull();
    }
  }

  @Test
  void deerLootIsCoinOrHideExclusive() {
    List<LootTable> deer = data.allLootTable().stream().filter(r -> r.monster() == 1).toList();
    assertThat(deer).extracting(LootTable::item).containsExactlyInAnyOrder(1, 2, 3);
    int totalWeight = deer.stream().mapToInt(LootTable::weight).sum();
    assertThat(totalWeight).isEqualTo(100);
    assertThat(data.item(1).category()).isEqualTo(ItemCategory.CURRENCY);
    assertThat(data.item(2).category()).isEqualTo(ItemCategory.MATERIAL);
  }

  @Test
  void unknownIdThrows() {
    assertThatThrownBy(() -> data.monster(9999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("9999");
  }
}
