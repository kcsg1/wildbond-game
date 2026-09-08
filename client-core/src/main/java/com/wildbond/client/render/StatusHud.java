package com.wildbond.client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.wildbond.client.ViewState;

/**
 * 화면 아래 상태창 — HP·MP 막대와 소지금 (docs/architecture.md §3.1 Player "HP/MP, 소지금").
 *
 * <p>창 전체 좌표(yDown UI 카메라)로 그린다. 문자열은 전부 ASCII 다 — 기본 BitmapFont 에 한글 글리프가 없다(T-009).
 */
public final class StatusHud implements Disposable {

  private static final int MARGIN_PX = 10;
  private static final int BAR_W = 168;
  private static final int BAR_H = 12;
  private static final int GAP = 4;

  private final BitmapFont font = new BitmapFont(true); // yDown UI 카메라 (T-006)
  private final Texture pixel;
  private final StringBuilder text = new StringBuilder(48);

  public StatusHud() {
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
      int windowWidth,
      int windowHeight,
      String zoneName) {
    ViewState.Snapshot player = viewState.snapshot(playerId);
    batch.setProjectionMatrix(uiProjection);
    batch.begin();

    int panelW = BAR_W + 84;
    int panelH = BAR_H * 2 + GAP * 3 + 16;
    int x = windowWidth - MARGIN_PX - panelW;
    int y = windowHeight - MARGIN_PX - panelH;

    batch.setColor(0f, 0f, 0f, 0.5f);
    batch.draw(pixel, x, y, panelW, panelH);
    batch.setColor(Color.WHITE);

    int hp = player != null ? player.hp() : 0;
    int maxHp = player != null && player.maxHp() > 0 ? player.maxHp() : 1;
    int mp = player != null && player.mp() >= 0 ? player.mp() : 0;
    int maxMp = player != null && player.maxMp() > 0 ? player.maxMp() : 1;
    int coins = player != null && player.coins() >= 0 ? player.coins() : 0;

    int barX = x + 40;
    drawLabelledBar(batch, "HP", barX, y + GAP, hp, maxHp, Color.valueOf("D64545FF"), x + 8);
    drawLabelledBar(
        batch, "MP", barX, y + GAP + BAR_H + GAP, mp, maxMp, Color.valueOf("3F7FE0FF"), x + 8);

    text.setLength(0);
    text.append(zoneName).append("   coin ").append(coins);
    font.draw(batch, text, x + 8, y + GAP * 2 + BAR_H * 2 + 2);

    batch.end();
  }

  private void drawLabelledBar(
      SpriteBatch batch, String label, int x, int y, int value, int max, Color fill, int labelX) {
    font.draw(batch, label, labelX, y - 1);

    batch.setColor(0.12f, 0.12f, 0.14f, 1f);
    batch.draw(pixel, x, y, BAR_W, BAR_H);
    float ratio = Math.max(0f, Math.min(1f, (float) value / max));
    batch.setColor(fill);
    batch.draw(pixel, x + 1, y + 1, (BAR_W - 2) * ratio, BAR_H - 2);
    batch.setColor(Color.WHITE);

    text.setLength(0);
    text.append(value).append(" / ").append(max);
    font.draw(batch, text, x + BAR_W / 2 - 20, y - 1);
  }

  @Override
  public void dispose() {
    font.dispose();
    pixel.dispose();
  }
}
