package com.carddemo.copybook.layout;

import java.util.ArrayList;
import java.util.List;

/** Compact factory used by the generated {@code LAYOUT} constants. */
public final class LayoutNodes {

  private LayoutNodes() {}

  /** Node factory: {@code pic == null} for groups, {@code usage} is the {@link Usage} name. */
  public static LayoutNode n(
      int level,
      String name,
      String pic,
      String usage,
      int offset,
      int length,
      int occurs,
      int minOccurs,
      String dependingOn,
      String redefines,
      List<Condition> conditions,
      LayoutNode... children) {
    return new LayoutNode(
        level,
        name,
        pic == null ? null : Picture.parse(pic),
        Usage.valueOf(usage),
        offset,
        length,
        occurs,
        minOccurs,
        dependingOn,
        redefines,
        List.of(children),
        conditions);
  }

  /** Condition factory: {@code bounds} are {@code from, to} pairs. */
  public static Condition cond(String name, String... bounds) {
    List<Condition.Range> ranges = new ArrayList<>();
    for (int i = 0; i < bounds.length; i += 2) {
      ranges.add(new Condition.Range(bounds[i], bounds[i + 1]));
    }
    return new Condition(name, ranges);
  }
}
