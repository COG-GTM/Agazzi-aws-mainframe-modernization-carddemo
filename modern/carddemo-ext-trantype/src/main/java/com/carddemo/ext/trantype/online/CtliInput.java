package com.carddemo.ext.trantype.online;

import com.carddemo.contracts.screen.map.CotrtliScreen;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Local adapter of {@link CotrtliScreen.Input}: same fields plus the row selection column
 * TRTSEL1..7 (BMS {@code FSET} fields missing from contracts-v1, see CONTRACT-CHANGE in the PR).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CtliInput(
    @JsonProperty("TRTYPE") String trtype,
    @JsonProperty("TRDESC") String trdesc,
    @JsonProperty("TRTSEL1") String trtsel1,
    @JsonProperty("TRTSEL2") String trtsel2,
    @JsonProperty("TRTSEL3") String trtsel3,
    @JsonProperty("TRTSEL4") String trtsel4,
    @JsonProperty("TRTSEL5") String trtsel5,
    @JsonProperty("TRTSEL6") String trtsel6,
    @JsonProperty("TRTSEL7") String trtsel7,
    @JsonProperty("TRTYPD1") String trtypd1,
    @JsonProperty("TRTYPD2") String trtypd2,
    @JsonProperty("TRTYPD3") String trtypd3,
    @JsonProperty("TRTYPD4") String trtypd4,
    @JsonProperty("TRTYPD5") String trtypd5,
    @JsonProperty("TRTYPD6") String trtypd6,
    @JsonProperty("TRTYPD7") String trtypd7) {

  public static final CtliInput EMPTY =
      new CtliInput(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
          null);

  public String[] selections() {
    return new String[] {trtsel1, trtsel2, trtsel3, trtsel4, trtsel5, trtsel6, trtsel7};
  }

  public String[] descriptions() {
    return new String[] {trtypd1, trtypd2, trtypd3, trtypd4, trtypd5, trtypd6, trtypd7};
  }

  public CotrtliScreen.Input toContract() {
    return new CotrtliScreen.Input(
        trtype, trdesc, trtypd1, trtypd2, trtypd3, trtypd4, trtypd5, trtypd6, trtypd7);
  }

  public static CtliInput fromContract(CotrtliScreen.Input in) {
    return new CtliInput(
        in.trtype(),
        in.trdesc(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        in.trtypd1(),
        in.trtypd2(),
        in.trtypd3(),
        in.trtypd4(),
        in.trtypd5(),
        in.trtypd6(),
        in.trtypd7());
  }
}
