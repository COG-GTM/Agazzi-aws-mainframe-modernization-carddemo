package com.carddemo.batch.orchestration.csd;

import java.util.Map;

/** One {@code DEFINE <type>(<name>) GROUP(<group>) attr(value)...} statement. */
public record CsdDefinition(
    String type, String name, String group, Map<String, String> attributes) {

  public String attributesText() {
    StringBuilder b = new StringBuilder();
    attributes.forEach(
        (k, v) -> b.append(b.isEmpty() ? "" : " ").append(k).append('(').append(v).append(')'));
    return b.toString();
  }
}
