package com.wildbond.tools.chunk;

import com.wildbond.data.chunk.ChunkObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Tiled .tmx(CSV 레이어 인코딩)를 읽어 {@link TmxMap} 으로 바꾼다. docs/architecture.md §8.2. */
final class TmxParser {

  private TmxParser() {}

  static TmxMap parse(Path tmxFile) {
    Document doc = loadDocument(tmxFile);
    Element mapEl = doc.getDocumentElement();

    int width = requiredInt(mapEl, "width");
    int height = requiredInt(mapEl, "height");
    int tileWidth = requiredInt(mapEl, "tilewidth");
    int tileHeight = requiredInt(mapEl, "tileheight");

    List<Element> tilesets = elements(mapEl.getElementsByTagName("tileset"));
    if (tilesets.isEmpty()) {
      throw new TmxParseException(tmxFile + ": <tileset> 이 없다");
    }
    int firstGid = requiredInt(tilesets.get(0), "firstgid");

    int[] ground = new int[width * height];
    int[] detail = new int[width * height];
    boolean groundFound = false;
    for (Element layerEl : elements(mapEl.getElementsByTagName("layer"))) {
      String name = layerEl.getAttribute("name");
      List<Element> dataEls = elements(layerEl.getElementsByTagName("data"));
      if (dataEls.isEmpty()) {
        continue;
      }
      int[] gids = parseCsvLayer(dataEls.get(0).getTextContent(), width * height, tmxFile, name);
      if ("ground".equals(name)) {
        ground = gids;
        groundFound = true;
      } else if ("detail".equals(name)) {
        detail = gids;
      }
    }
    if (!groundFound) {
      throw new TmxParseException(tmxFile + ": \"ground\" 레이어가 없다");
    }

    List<ChunkObject> objects = new ArrayList<>();
    for (Element groupEl : elements(mapEl.getElementsByTagName("objectgroup"))) {
      for (Element objEl : elements(groupEl.getElementsByTagName("object"))) {
        objects.add(parseObject(objEl, tileWidth, tileHeight));
      }
    }

    return new TmxMap(width, height, firstGid, ground, detail, objects);
  }

  private static ChunkObject parseObject(Element objEl, int tileWidth, int tileHeight) {
    String type = objEl.getAttribute("type");
    double px = Double.parseDouble(objEl.getAttribute("x"));
    double py = Double.parseDouble(objEl.getAttribute("y"));
    int tileX = (int) Math.floor(px / tileWidth);
    int tileY = (int) Math.floor(py / tileHeight);

    Map<String, String> props = new LinkedHashMap<>();
    for (Element propsEl : elements(objEl.getElementsByTagName("properties"))) {
      for (Element propEl : elements(propsEl.getElementsByTagName("property"))) {
        props.put(propEl.getAttribute("name"), propEl.getAttribute("value"));
      }
    }
    return new ChunkObject(type, tileX, tileY, props);
  }

  private static int[] parseCsvLayer(
      String text, int expectedCount, Path tmxFile, String layerName) {
    String[] tokens = text.trim().split("\\s*,\\s*");
    if (tokens.length != expectedCount) {
      throw new TmxParseException(
          tmxFile
              + ": \""
              + layerName
              + "\" 레이어 타일 수가 다르다 (기대 "
              + expectedCount
              + ", 실제 "
              + tokens.length
              + ")");
    }
    int[] result = new int[expectedCount];
    for (int i = 0; i < expectedCount; i++) {
      result[i] = Integer.parseInt(tokens[i]);
    }
    return result;
  }

  private static int requiredInt(Element element, String attribute) {
    String value = element.getAttribute(attribute);
    if (value.isEmpty()) {
      throw new TmxParseException("<" + element.getTagName() + "> 에 " + attribute + " 속성이 없다");
    }
    return Integer.parseInt(value);
  }

  private static List<Element> elements(NodeList nodes) {
    List<Element> list = new ArrayList<>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      list.add((Element) nodes.item(i));
    }
    return list;
  }

  private static Document loadDocument(Path tmxFile) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(tmxFile.toFile());
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new TmxParseException(tmxFile + " 파싱 실패: " + e.getMessage(), e);
    }
  }
}
