package com.carddemo.online.card.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input fields of map CCRDUPA. Local adapter of {@code CocrdupScreen.Input}, which lacks ACCTSID
 * (unprotected while searching, see CONTRACT-CHANGE in the O3 PR).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardUpdateFields(
    @JsonProperty("ACCTSID") String acctsid,
    @JsonProperty("CARDSID") String cardsid,
    @JsonProperty("CRDNAME") String crdname,
    @JsonProperty("CRDSTCD") String crdstcd,
    @JsonProperty("EXPMON") String expmon,
    @JsonProperty("EXPYEAR") String expyear) {

  public static CardUpdateFields empty() {
    return new CardUpdateFields(null, null, null, null, null, null);
  }
}
