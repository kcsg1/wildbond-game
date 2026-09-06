package com.wildbond.client.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;

/**
 * 가상 해상도 640×360, 정수 배율(2×/3×/4×), 정수 픽셀 스냅 (docs/architecture.md §5.3, §10). Y 는 아래로 증가한다(yDown) —
 * 타일 그리드·청크 포맷과 좌표계를 맞추기 위함.
 */
public final class GameCamera {

  public static final float VIRTUAL_WIDTH = 640f;
  public static final float VIRTUAL_HEIGHT = 360f;

  private final OrthographicCamera camera = new OrthographicCamera();
  private int pixelScale = 1;
  private int viewportX;
  private int viewportY;
  private int viewportWidth = (int) VIRTUAL_WIDTH;
  private int viewportHeight = (int) VIRTUAL_HEIGHT;

  public GameCamera() {
    camera.setToOrtho(true, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
  }

  /** 창 크기가 바뀔 때 호출 — 정수 배율과, 창 안에서 가운데 정렬된 GL 뷰포트를 다시 계산한다. */
  public void resize(int windowWidth, int windowHeight) {
    int scaleX = Math.max(1, windowWidth / (int) VIRTUAL_WIDTH);
    int scaleY = Math.max(1, windowHeight / (int) VIRTUAL_HEIGHT);
    pixelScale = Math.max(1, Math.min(scaleX, scaleY));

    viewportWidth = (int) VIRTUAL_WIDTH * pixelScale;
    viewportHeight = (int) VIRTUAL_HEIGHT * pixelScale;
    viewportX = (windowWidth - viewportWidth) / 2;
    viewportY = (windowHeight - viewportHeight) / 2;
  }

  /** 플레이어 보간 위치를 정수 픽셀로 스냅해 카메라 중심으로 삼는다. */
  public void trackPosition(float worldX, float worldY) {
    camera.position.set(Math.round(worldX), Math.round(worldY), 0f);
    camera.update();
  }

  public OrthographicCamera raw() {
    return camera;
  }

  /** 창 픽셀 좌표(마우스 등)를 이 카메라의 월드 좌표로 바꾼다 — 마우스 조준(§5.4)에 쓴다. */
  public Vector3 unproject(float screenX, float screenY) {
    Vector3 point = new Vector3(screenX, screenY, 0f);
    camera.unproject(point, viewportX, viewportY, viewportWidth, viewportHeight);
    return point;
  }

  public int pixelScale() {
    return pixelScale;
  }

  public int viewportX() {
    return viewportX;
  }

  public int viewportY() {
    return viewportY;
  }

  public int viewportWidth() {
    return viewportWidth;
  }

  public int viewportHeight() {
    return viewportHeight;
  }
}
