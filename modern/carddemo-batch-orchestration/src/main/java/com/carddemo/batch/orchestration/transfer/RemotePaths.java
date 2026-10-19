package com.carddemo.batch.orchestration.transfer;

/** {@code cd /ftpfolder} + {@code PUT x name}: normalised, no traversal outside the root. */
final class RemotePaths {

  private RemotePaths() {}

  static String relative(String dir) {
    String d = dir == null ? "" : dir.replace('\\', '/');
    StringBuilder out = new StringBuilder();
    for (String part : d.split("/")) {
      if (part.isEmpty() || part.equals(".")) {
        continue;
      }
      if (part.equals("..")) {
        throw new IllegalArgumentException("remote dir escapes root: " + dir);
      }
      out.append(out.isEmpty() ? "" : "/").append(part);
    }
    return out.toString();
  }

  static String name(String name) {
    if (name == null
        || name.isBlank()
        || name.contains("/")
        || name.contains("\\")
        || name.equals("..")) {
      throw new IllegalArgumentException("invalid remote name: " + name);
    }
    return name;
  }

  static String key(String dir, String name) {
    String rel = relative(dir);
    return rel.isEmpty() ? name(name) : rel + "/" + name(name);
  }
}
