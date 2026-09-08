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
import com.wildbond.data.Item;
import com.wildbond.sim.SimView;
import java.util.HashMap;
import java.util.Map;

/**
 * 화면 왼쪽 아래 인벤토리 목록 — 채워진 슬롯만 "이름 xN" 으로 보여 준다 (docs/architecture.md §3.1 Inventory, §12 M0 "인벤토리
 * 표시").
 *
 * <p>아이템 이름은 Item.name_key 의 접두어를 뗀 것("item.deer_hide" → "deer hide") — 로컬라이즈 표는 아직 없고, 기본
 * BitmapFont 는 ASCII 만 그린다(T-009).
 */
public final class InventoryHud implements Disposable {

  private static final int MARGIN_PX = 10;
  private static final int LINE_H = 16;
  private static final int PANEL_W = 150;

  private final GameData gameData;
  private final BitmapFont font = new BitmapFont(true); // yDown UI 카메라 (T-006)
  private final Texture pixel;
  private final Map<Integer, String> names = new HashMap<>();
  private final StringBuilder text = new StringBuilder(48);

  public InventoryHud(GameData gameData) {
    this.gameData = gameData;
    Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixmap.setColor(Color.WHITE);
    pixmap.fill();
    pixel = new Texture(pixmap);
    pixmap.dispose();
  }

  public void render(
      SpriteBatch batch,
      Matrix4 uiProjection,
      ViewState viewState,
      int playerId,
      int windowHeight) {
    ViewState.Snapshot player = viewState.snapshot(playerId);
    int filled = 0;
    if (player != null) {
      for (int slot = 0; slot < SimView.INVENTORY_SLOTS; slot++) {
        if (player.inventoryItemId(slot) != 0) {
          filled++;
        }
      }
    }

    batch.setProjectionMatrix(uiProjection);
    batch.begin();

    int panelH = LINE_H * (filled + 1) + 8;
    int x = MARGIN_PX;
    int y = windowHeight - MARGIN_PX - panelH;
    batch.setColor(0f, 0f, 0f, 0.5f);
    batch.draw(pixel, x, y, PANEL_W, panelH);
    batch.setColor(Color.WHITE);

    int rowY = y + 4;
    text.setLength(0);
    text.append("BAG ").append(filled).append("/").append(SimView.INVENTORY_SLOTS);
    font.draw(batch, text, x + 6, rowY);
    rowY += LINE_H;

    if (player != null) {
      for (int slot = 0; slot < SimView.INVENTORY_SLOTS; slot++) {
        int itemId = player.inventoryItemId(slot);
        if (itemId == 0) {
          continue;
        }
        text.setLength(0);
        text.append(nameOf(itemId)).append(" x").append(player.inventoryCount(slot));
        font.draw(batch, text, x + 6, rowY);
        rowY += LINE_H;
      }
    }
    batch.end();
  }

  private String nameOf(int itemId) {
    return names.computeIfAbsent(
        itemId,
        id -> {
          Item item = gameData.item(id);
          String key = item.nameKey();
          int dot = key.indexOf('.');
          return (dot >= 0 ? key.substring(dot + 1) : key).replace('_', ' ');
        });
  }

  @Override
  public void dispose() {
    font.dispose();
    pixel.dispose();
  }
}
