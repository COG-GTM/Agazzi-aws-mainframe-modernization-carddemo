package com.carddemo.ext.authorization.store;

import com.carddemo.ext.authorization.model.AuthFraud;
import com.carddemo.ext.authorization.model.AuthFraudId;
import org.springframework.data.jpa.repository.JpaRepository;

/** AUTHFRDS access (COPAUS2C INSERT / UPDATE on -803). */
public interface AuthFraudRepository extends JpaRepository<AuthFraud, AuthFraudId> {}
