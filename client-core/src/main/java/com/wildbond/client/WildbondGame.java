package com.wildbond.client;

import com.badlogic.gdx.Game;
import com.wildbond.client.screen.BootScreen;

/** 게임 진입점 (docs/architecture.md §5). Screen 스택: BootScreen → PlayScreen. */
public final class WildbondGame extends Game {

  private final GameConfig config;

  public WildbondGame(GameConfig config) {
    this.config = config;
  }

  @Override
  public void create() {
    setScreen(new BootScreen(this, config));
  }
}
