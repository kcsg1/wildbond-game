package com.wildbond.client;

import java.nio.file.Path;

/** 플랫폼 런처(client-desktop)가 넘겨주는 콘텐츠 경로. client-core 는 이 경로가 어떻게 정해지는지 모른다. */
public record GameConfig(
    Path tablesDir,
    Path chunksDir,
    Path tilesetFile,
    Path tinyTownSheet,
    Path tinyDungeonSheet,
    Path lpcWalkSheet,
    Path lpcSlashSheet) {}
