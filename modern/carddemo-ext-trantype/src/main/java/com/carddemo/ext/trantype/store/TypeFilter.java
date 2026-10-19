package com.carddemo.ext.trantype.store;

/**
 * CTLI filters: exact type code (WS-EDIT-TYPE-FLAG) and {@code LIKE '%desc%'} on the description
 * (WS-EDIT-DESC-FLAG). A {@code null} member means "no filter".
 */
public record TypeFilter(String typeCode, String descriptionContains) {

  public static final TypeFilter NONE = new TypeFilter(null, null);

  public boolean isEmpty() {
    return typeCode == null && descriptionContains == null;
  }
}
