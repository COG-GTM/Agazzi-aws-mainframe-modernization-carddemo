package com.carddemo.repository;

import com.carddemo.entity.AccountCardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountCardXrefRepository extends JpaRepository<AccountCardXref, String> {
    List<AccountCardXref> findByCustId(Long custId);
    List<AccountCardXref> findByAcctId(Long acctId);
}
