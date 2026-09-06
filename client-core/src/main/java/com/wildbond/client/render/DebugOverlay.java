package com.wildbond.client.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/** F3 디버그 오버레이 — FPS, 틱 시간(평균/최대), 틱 해시, 드로우콜, 로드된 청크 수 (docs/m0-prompts.md 단계5). */
public final class DebugOverlay implements Disposable {

  /** 화면 표시용 스냅샷. drawCalls 는 게임 월드(청크+엔티티)만 — 오버레이 자체 그리기는 빼고 보여준다. */
  public record Stats(
      int fps,
      int tick,
      float avgTickMillis,
      float maxTickMillis,
      long stateHash,
      int drawCalls,
      int loadedChunks) {}

  // true = y 가 아래로 증가 — PlayScreen 의 yDown UI 카메라와 맞춘다(안 맞추면 글자가 뒤집힌다).
  private final BitmapFont font = new BitmapFont(true);
  private final StringBuilder text = new StringBuilder(128);

  public void render(SpriteBatch batch, Matrix4 uiProjection, Stats stats) {
    text.setLength(0);
    text.append("FPS ")
        .append(stats.fps())
        .append("  tick ")
        .append(stats.tick())
        .append("  avg ")
        .append(String.format("%.2f", stats.avgTickMillis()))
        .append("ms  max ")
        .append(String.format("%.2f", stats.maxTickMillis()))
        .append("ms\n")
        .append("hash ")
        .append(Long.toHexString(stats.stateHash()))
        .append("  draws ")
        .append(stats.drawCalls())
        .append("  chunks ")
        .append(stats.loadedChunks());

    batch.setProjectionMatrix(uiProjection);
    batch.begin();
    font.draw(batch, text, 8, 20);
    batch.end();
  }

  @Override
  public void dispose() {
    font.dispose();
  }
}
