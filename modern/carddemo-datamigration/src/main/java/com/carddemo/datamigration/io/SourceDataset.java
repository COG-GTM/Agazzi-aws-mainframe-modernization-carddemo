package com.carddemo.datamigration.io;

import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.SourceFormat;
import java.util.List;

/**
 * One legacy dataset after decoding: decoded records plus decode errors (record ordinal and
 * message) and the physical layout to rebuild the file.
 */
public record SourceDataset(
    Dataset dataset,
    SourceFormat format,
    String file,
    String sha256,
    PhysicalLayout layout,
    List<SourceRecord> records,
    List<DecodeError> decodeErrors,
    List<DecodeError> lowValues) {

  /** A record that could not be decoded with the copybook layout. */
  public record DecodeError(long ordinal, String message) {}
}
