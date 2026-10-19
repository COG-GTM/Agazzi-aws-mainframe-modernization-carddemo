package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.repository.CardXrefRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** JPA implementation of {@link XrefFile} on table {@code card_xref}. */
@Component
public class JpaXrefFile implements XrefFile {

  private final CardXrefRepository repository;

  public JpaXrefFile(CardXrefRepository repository) {
    this.repository = repository;
  }

  @Override
  public ReadResult<CardXref> readByCard(String cardNum) {
    return repository
        .findById(cardNum)
        .map(ReadResult::normal)
        .orElseGet(() -> ReadResult.of(Resp.NOTFND));
  }

  @Override
  public ReadResult<CardXref> readByAcct(long acctId) {
    List<CardXref> rows =
        repository.findByXrefAcctId(acctId).stream()
            .sorted(Comparator.comparing(CardXref::getXrefCardNum))
            .toList();
    if (rows.isEmpty()) {
      return ReadResult.of(Resp.NOTFND);
    }
    return new ReadResult<>(rows.size() > 1 ? Resp.DUPKEY : Resp.NORMAL, rows.get(0));
  }
}
