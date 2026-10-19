package com.carddemo.datamigration.validate;

import com.carddemo.datamigration.model.Dataset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Parent keys already in the target database (datasets not part of the current load). */
@FunctionalInterface
public interface KeyLookup {

  KeyLookup NONE = (ds, cols) -> Optional.empty();

  Optional<Set<String>> keys(Dataset dataset, List<String> columns);
}
