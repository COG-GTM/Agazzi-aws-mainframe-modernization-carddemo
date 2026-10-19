package com.carddemo.copybook.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolved layout of one 01-level record: the AST with offsets, lengths and pictures.
 *
 * @param copybook source copybook (or program) name, e.g. {@code CVEXPORT}
 * @param root the 01-level node
 */
public record RecordLayout(String copybook, LayoutNode root) {

  /** Elementary item flattened from the tree, with the absolute offset of every occurrence. */
  public record Field(
      String path, LayoutNode node, int offset, int length, Picture picture, Usage usage) {}

  public String name() {
    return root.name();
  }

  /** Maximum record length in bytes. */
  public int length() {
    return root.totalLength();
  }

  /** Whether the record contains an {@code OCCURS DEPENDING ON} table. */
  public boolean variableLength() {
    return find(n -> n.dependingOn() != null).isPresent();
  }

  /** Finds a node by data name (first match, depth first). */
  public Optional<LayoutNode> find(String name) {
    return find(n -> n.name().equals(name));
  }

  private Optional<LayoutNode> find(java.util.function.Predicate<LayoutNode> p) {
    List<LayoutNode> stack = new ArrayList<>(List.of(root));
    while (!stack.isEmpty()) {
      LayoutNode n = stack.remove(0);
      if (p.test(n)) {
        return Optional.of(n);
      }
      stack.addAll(0, n.children());
    }
    return Optional.empty();
  }

  /** Condition name lookup across the whole record. */
  public Condition condition(String conditionName) {
    List<LayoutNode> stack = new ArrayList<>(List.of(root));
    while (!stack.isEmpty()) {
      LayoutNode n = stack.remove(0);
      for (Condition c : n.conditions()) {
        if (c.name().equals(conditionName)) {
          return c;
        }
      }
      stack.addAll(0, n.children());
    }
    throw new IllegalArgumentException(conditionName + " not in " + name());
  }

  /** Storage-owning elementary fields at maximum occurrence, in offset order. */
  public List<Field> fields() {
    List<Field> out = new ArrayList<>();
    flatten(root, root.name(), root.offset(), out);
    return out;
  }

  private static void flatten(LayoutNode n, String path, int base, List<Field> out) {
    for (int i = 0; i < n.occurs(); i++) {
      int start = base + i * n.length();
      String p = n.table() ? path + "(" + (i + 1) + ")" : path;
      if (!n.group()) {
        out.add(new Field(p, n, start, n.length(), n.picture(), n.usage()));
      } else {
        for (LayoutNode c : n.storageChildren()) {
          flatten(c, p + "." + c.name(), start + (c.offset() - n.offset()), out);
        }
      }
    }
  }

  /**
   * Variant view for a {@code REDEFINES}: the record where {@code baseItem} is replaced by the
   * redefining sibling {@code variantItem}, which becomes the storage owner. Other redefinitions of
   * {@code baseItem} are dropped.
   */
  public RecordLayout variant(String baseItem, String variantItem) {
    return new RecordLayout(copybook, rewrite(root, baseItem, variantItem));
  }

  private static LayoutNode rewrite(LayoutNode n, String base, String variant) {
    if (n.children().isEmpty()) {
      return n;
    }
    List<LayoutNode> kids = new ArrayList<>();
    boolean found = false;
    for (LayoutNode c : n.children()) {
      if (c.name().equals(base) && c.redefines() == null) {
        LayoutNode v =
            n.children().stream()
                .filter(s -> variant.equals(s.name()) && base.equals(s.redefines()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(variant + " !REDEFINES " + base));
        kids.add(v.asOwner());
        found = true;
      } else if (!base.equals(c.redefines())) {
        kids.add(c);
      }
    }
    if (found) {
      return n.withChildren(kids);
    }
    return n.withChildren(n.children().stream().map(c -> rewrite(c, base, variant)).toList());
  }
}
