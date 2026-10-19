package com.carddemo.online.card.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

/**
 * Input fields of map CCRDLIA. Local adapter of {@code CocrdliScreen.Input}, which lacks the row
 * selection fields CRDSEL1..7 (see CONTRACT-CHANGE in the O3 PR).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardListFields(
    @JsonProperty("ACCTSID") String acctsid,
    @JsonProperty("CARDSID") String cardsid,
    @JsonProperty("CRDSEL1") String crdsel1,
    @JsonProperty("CRDSEL2") String crdsel2,
    @JsonProperty("CRDSEL3") String crdsel3,
    @JsonProperty("CRDSEL4") String crdsel4,
    @JsonProperty("CRDSEL5") String crdsel5,
    @JsonProperty("CRDSEL6") String crdsel6,
    @JsonProperty("CRDSEL7") String crdsel7) {

  public static CardListFields empty() {
    return new CardListFields(null, null, null, null, null, null, null, null, null);
  }

  public List<String> selections() {
    return Arrays.asList(crdsel1, crdsel2, crdsel3, crdsel4, crdsel5, crdsel6, crdsel7);
  }
}
