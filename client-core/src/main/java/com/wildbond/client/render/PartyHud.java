package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.client.ViewState;
import com.wildbond.data.GameData;

/** 파티 슬롯 5칸 HUD — 종 이름과 HP 바 (docs/m0-prompts.md 단계7). 창 전체 좌표(yDown UI 카메라)로 왼쪽 아래에 쌓아 그린다. */
public final class PartyHud implements Disposable {

  private static final int SLOT_HEIGHT_PX = 22;
  private static final int SLOT_WIDTH_PX = 190;
  private static final int MARGIN_PX = 10;
  private static final int BAR_HEIGHT_PX = 6;
  private static final int BAR_WIDTH_PX = 96;

  private final GameData gameData;
  private final BitmapFont font = new BitmapFont(true); // yDown UI 카메라 (T-006)
  private final Texture pixel;
  private final StringBuilder text = new StringBuilder(32);

  public PartyHud(GameData gameData) {
    this.gameData = gameData;
    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    pixel = new Texture(pixmap);
    pixmap.dispose();
  }

  public void render(
      SpriteBatch batch, Matrix4 uiProjection, ViewState viewState, int windowHeight) {
    batch.setProjectionMatrix(uiProjection);
    batch.begin();

    int slots = viewState.partySlotCount();
    int blockHeight = slots * SLOT_HEIGHT_PX;
    int top = windowHeight - MARGIN_PX - blockHeight;

    for (int slot = 0; slot < slots; slot++) {
      int rowY = top + slot * SLOT_HEIGHT_PX;
      batch.setColor(0f, 0f, 0f, 0.45f);
      batch.draw(pixel, MARGIN_PX, rowY, SLOT_WIDTH_PX, SLOT_HEIGHT_PX - 2);
      batch.setColor(Color.WHITE);

      int entityId = viewState.partyEntityId(slot);
      text.setLength(0);
      text.append(slot + 1).append(". ");
      if (entityId < 0) {
        text.append("- empty");
        font.draw(batch, text, MARGIN_PX + 6, rowY + 4);
        continue;
      }

      ViewState.Snapshot pal = viewState.snapshot(entityId);
      if (pal == null) {
        text.append("- empty");
        font.draw(batch, text, MARGIN_PX + 6, rowY + 4);
        continue;
      }
      text.append(displayName(pal.speciesId()));
      font.draw(batch, text, MARGIN_PX + 6, rowY + 4);

      int barX = MARGIN_PX + SLOT_WIDTH_PX - BAR_WIDTH_PX - 6;
      int barY = rowY + (SLOT_HEIGHT_PX - 2 - BAR_HEIGHT_PX) / 2;
      batch.setColor(0.15f, 0.15f, 0.15f, 1f);
      batch.draw(pixel, barX, barY, BAR_WIDTH_PX, BAR_HEIGHT_PX);
      float ratio = pal.maxHp() > 0 ? Math.max(0f, (float) pal.hp() / pal.maxHp()) : 0f;
      batch.setColor(ratio > 0.3f ? Color.LIME : Color.SCARLET);
      batch.draw(pixel, barX, barY, BAR_WIDTH_PX * ratio, BAR_HEIGHT_PX);
      batch.setColor(Color.WHITE);
    }

    batch.end();
  }

  /**
   * {@code pal.mossling} → {@code mossling} — 로컬라이즈 테이블은 M1 이후다.
   *
   * <p>HUD 문자열은 전부 ASCII 로 둔다. LibGDX 기본 {@code BitmapFont} 에는 한글 글리프가 없어 한글을 쓰면 □ 로 나온다 — 커스텀 폰트
   * 에셋은 M0 범위 밖이다(단계 5 "글꼴은 LibGDX 기본 내장 폰트").
   */
  private String displayName(int speciesId) {
    if (speciesId < 0) {
      return "?";
    }
    String nameKey = gameData.palSpecies(speciesId).nameKey();
    int dot = nameKey.lastIndexOf('.');
    return dot >= 0 ? nameKey.substring(dot + 1) : nameKey;
  }

  @Override
  public void dispose() {
    font.dispose();
    pixel.dispose();
  }
}
