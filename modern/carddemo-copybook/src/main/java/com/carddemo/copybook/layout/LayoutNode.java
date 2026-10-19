package com.carddemo.copybook.layout;

import java.util.List;
import java.util.Objects;

/**
 * A node of the copybook AST with its resolved storage layout.
 *
 * @param level COBOL level number (01-49)
 * @param name data name ({@code FILLER} for unnamed items)
 * @param picture picture clause, {@code null} for groups
 * @param usage effective usage (inherited from the enclosing group when not declared)
 * @param offset byte offset of the first occurrence, relative to the start of the 01 record
 * @param length length in bytes of one occurrence
 * @param occurs maximum number of occurrences (1 when no {@code OCCURS})
 * @param minOccurs minimum number of occurrences ({@code OCCURS n TO m}), equal to {@code occurs}
 *     for fixed tables
 * @param dependingOn {@code OCCURS DEPENDING ON} counter name, or {@code null}
 * @param redefines name of the redefined sibling, or {@code null}
 * @param children subordinate items (empty for elementary items)
 * @param conditions level-88 condition names
 */
public record LayoutNode(
    int level,
    String name,
    Picture picture,
    Usage usage,
    int offset,
    int length,
    int occurs,
    int minOccurs,
    String dependingOn,
    String redefines,
    List<LayoutNode> children,
    List<Condition> conditions) {

  public LayoutNode {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(usage, "usage");
    children = List.copyOf(children);
    conditions = List.copyOf(conditions);
  }

  public boolean group() {
    return picture == null;
  }

  public boolean filler() {
    return "FILLER".equals(name);
  }

  /** Whether the item is a table ({@code OCCURS}). */
  public boolean table() {
    return occurs > 1 || dependingOn != null || minOccurs != occurs;
  }

  /** Total bytes of all (maximum) occurrences. */
  public int totalLength() {
    return length * occurs;
  }

  /** Whether the bytes of this item are numeric binary or packed (not printable text). */
  public boolean binaryStorage() {
    return picture != null
        && picture.numeric()
        && (usage == Usage.BINARY || usage == Usage.NATIVE_BINARY || usage == Usage.PACKED);
  }

  /** Children that own their storage (not {@code REDEFINES} of a sibling). */
  public List<LayoutNode> storageChildren() {
    return children.stream().filter(c -> c.redefines() == null).toList();
  }

  /** Copy with a different data name. */
  public LayoutNode withName(String newName) {
    return new LayoutNode(
        level,
        newName,
        picture,
        usage,
        offset,
        length,
        occurs,
        minOccurs,
        dependingOn,
        redefines,
        children,
        conditions);
  }

  /** Copy with a replaced child list. */
  public LayoutNode withChildren(List<LayoutNode> newChildren) {
    return new LayoutNode(
        level,
        name,
        picture,
        usage,
        offset,
        length,
        occurs,
        minOccurs,
        dependingOn,
        redefines,
        newChildren,
        conditions);
  }

  /** Copy that no longer redefines a sibling (it becomes the owner of the storage). */
  public LayoutNode asOwner() {
    return new LayoutNode(
        level,
        name,
        picture,
        usage,
        offset,
        length,
        occurs,
        minOccurs,
        dependingOn,
        null,
        children,
        conditions);
  }
}
