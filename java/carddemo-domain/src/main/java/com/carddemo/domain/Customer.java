package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Customer record RECLN 500. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @Column(name = "cust_id")
    private Long customerId;

    @Column(name = "cust_first_name", length = 25, nullable = false)
    private String firstName;
    @Column(name = "cust_middle_name", length = 25, nullable = false)
    private String middleName;
    @Column(name = "cust_last_name", length = 25, nullable = false)
    private String lastName;
    @Column(name = "cust_addr_line_1", length = 50, nullable = false)
    private String addressLine1;
    @Column(name = "cust_addr_line_2", length = 50, nullable = false)
    private String addressLine2;
    @Column(name = "cust_addr_line_3", length = 50, nullable = false)
    private String addressLine3;
    @Column(name = "cust_addr_state_cd", length = 2, nullable = false)
    private String stateCode;
    @Column(name = "cust_addr_country_cd", length = 3, nullable = false)
    private String countryCode;
    @Column(name = "cust_addr_zip", length = 10, nullable = false)
    private String zip;
    @Column(name = "cust_phone_num_1", length = 15, nullable = false)
    private String phone1;
    @Column(name = "cust_phone_num_2", length = 15, nullable = false)
    private String phone2;
    @Column(name = "cust_ssn", nullable = false)
    private Long ssn;
    @Column(name = "cust_govt_issued_id", length = 20, nullable = false)
    private String govtIssuedId;
    @Column(name = "cust_dob_yyyy_mm_dd", length = 10, nullable = false)
    private String dateOfBirth;
    @Column(name = "cust_eft_account_id", length = 10, nullable = false)
    private String eftAccountId;
    @Column(name = "cust_pri_card_holder_ind", length = 1, nullable = false)
    private String primaryCardHolder;
    @Column(name = "cust_fico_credit_score", nullable = false)
    private Integer ficoCreditScore;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getAddressLine3() {
        return addressLine3;
    }

    public void setAddressLine3(String addressLine3) {
        this.addressLine3 = addressLine3;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getPhone1() {
        return phone1;
    }

    public void setPhone1(String phone1) {
        this.phone1 = phone1;
    }

    public String getPhone2() {
        return phone2;
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    public Long getSsn() {
        return ssn;
    }

    public void setSsn(Long ssn) {
        this.ssn = ssn;
    }

    public String getGovtIssuedId() {
        return govtIssuedId;
    }

    public void setGovtIssuedId(String govtIssuedId) {
        this.govtIssuedId = govtIssuedId;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEftAccountId() {
        return eftAccountId;
    }

    public void setEftAccountId(String eftAccountId) {
        this.eftAccountId = eftAccountId;
    }

    public String getPrimaryCardHolder() {
        return primaryCardHolder;
    }

    public void setPrimaryCardHolder(String primaryCardHolder) {
        this.primaryCardHolder = primaryCardHolder;
    }

    public Integer getFicoCreditScore() {
        return ficoCreditScore;
    }

    public void setFicoCreditScore(Integer ficoCreditScore) {
        this.ficoCreditScore = ficoCreditScore;
    }
}
