-- Seed data migrated from COBOL/VSAM sample data files (app/data/ASCII/)
-- Original data was fixed-width EBCDIC records; converted to SQL INSERT statements

-- Default users (from USRSEC file)
-- Original COBOL: ADMIN001/PASSWORD (type A), USER0001/PASSWORD (type U)
INSERT INTO users (user_id, first_name, last_name, password, user_type) VALUES
('ADMIN001', 'Admin', 'User', 'PASSWORD', 'A'),
('USER0001', 'Regular', 'User', 'PASSWORD', 'U');

-- Transaction Types (from trantype.txt / CVTRA03Y)
INSERT INTO transaction_types (tran_type, tran_type_desc) VALUES
('01', 'Purchase'),
('02', 'Payment'),
('03', 'Credit'),
('04', 'Authorization'),
('05', 'Refund');

-- Transaction Categories (from trancatg.txt / CVTRA04Y)
INSERT INTO transaction_categories (tran_type_cd, tran_cat_cd, tran_cat_type_desc) VALUES
('01', 1, 'Regular Sales Draft'),
('01', 2, 'Regular Cash Advance'),
('01', 3, 'Convenience Check Debit'),
('01', 4, 'ATM Cash Advance'),
('01', 5, 'Interest Amount'),
('02', 1, 'Payment - Thank You'),
('03', 1, 'Return'),
('03', 2, 'Adjustment'),
('05', 1, 'Purchase Refund'),
('05', 2, 'Cash Advance Refund');

-- Sample Customers (from custdata.txt / CVCUS01Y)
INSERT INTO customers (cust_id, first_name, middle_name, last_name, addr_line_1, addr_line_2, addr_line_3,
                       addr_state_cd, addr_country_cd, addr_zip, phone_num_1, phone_num_2,
                       ssn, govt_issued_id, dob, eft_account_id, pri_card_holder_ind, fico_credit_score)
VALUES
(1, 'Immanuel', 'Madeline', 'Kessler', '618 Deshaun Route', 'Apt. 802', 'Altenwerthshire', 'NC', 'USA', '12546', '(908)119-8310', '(373)693-8684', 20973888, '0493684371', '1961-06-08', '0053581756', 'Y', 274),
(2, 'Enrico', 'April', 'Rosenbaum', '4917 Myrna Flats', 'Apt. 453', 'West Bernita', 'IN', 'USA', '22770', '(429)706-9510', '(744)950-5272', 587518382, '5062103711', '1961-10-08', '0069194009', 'Y', 268),
(3, 'Larry', 'Cody', 'Homenick', '362 Esta Parks', 'Apt. 390', 'New Gladys', 'GA', 'USA', '19852-6716', '(950)396-9024', '(685)168-8826', 317460867, '0524193031', '1987-11-30', '0006465789', 'Y', 616),
(4, 'Delbert', 'Kaia', 'Parisian', '638 Blanda Gateway', 'Apt. 076', 'Lake Virginie', 'MI', 'USA', '39035-0455', '(801)603-4121', '(156)074-6837', 660354258, '0685792491', '1985-01-13', '0040802739', 'Y', 776),
(5, 'Treva', 'Manley', 'Schowalter', '5653 Legros Plaza', 'Apt. 968', 'Alvinaport', 'MI', 'USA', '02251-1698', '(978)775-4633', '(439)943-7644', 611264288, '6397997541', '1971-09-29', '0006365573', 'Y', 529);

-- Sample Accounts (from acctdata.txt / CVACT01Y)
INSERT INTO accounts (acct_id, active_status, curr_bal, credit_limit, cash_credit_limit,
                      open_date, expiration_date, reissue_date, curr_cyc_credit, curr_cyc_debit,
                      addr_zip, group_id)
VALUES
(1,  'Y', 19400.00, 202000.00, 102000.00, '2014-11-20', '2025-05-20', '2025-05-20', 0.00, 0.00, 'A000000000', NULL),
(2,  'Y', 15800.00, 613000.00, 544800.00, '2013-06-19', '2024-08-11', '2024-08-11', 0.00, 0.00, 'A000000000', NULL),
(3,  'Y', 14700.00, 490900.00, 53800.00,  '2013-08-23', '2024-01-10', '2024-01-10', 0.00, 0.00, 'A000000000', NULL),
(4,  'Y', 4000.00,  350300.00, 278900.00, '2012-11-17', '2023-12-16', '2023-12-16', 0.00, 0.00, 'A000000000', NULL),
(5,  'Y', 34500.00, 381900.00, 243000.00, '2012-10-03', '2025-03-09', '2025-03-09', 0.00, 0.00, 'A000000000', NULL),
(12, 'Y', 10000.00, 100000.00, 50000.00,  '2015-01-01', '2026-01-01', '2026-01-01', 0.00, 0.00, 'A000000000', NULL),
(20, 'Y', 15000.00, 200000.00, 100000.00, '2016-03-15', '2027-03-15', '2027-03-15', 0.00, 0.00, 'A000000000', NULL),
(27, 'Y', 20000.00, 300000.00, 150000.00, '2017-06-01', '2028-06-01', '2028-06-01', 0.00, 0.00, 'A000000000', NULL),
(50, 'Y', 25000.00, 400000.00, 200000.00, '2018-09-10', '2029-09-10', '2029-09-10', 0.00, 0.00, 'A000000000', NULL);

-- Sample Cards (from carddata.txt / CVACT02Y)
INSERT INTO cards (card_num, acct_id, cvv_cd, embossed_name, expiration_date, active_status)
VALUES
('0500024453765740', 50, 747, 'Aniya Von', '2023-03-09', 'Y'),
('0683586198171516', 27, 567, 'Ward Jones', '2025-07-13', 'Y'),
('0923877193247330', 2,  28,  'Enrico Rosenbaum', '2024-08-11', 'Y'),
('0927987108636232', 20, 3,   'Carter Veum', '2024-03-13', 'Y'),
('0982496213629795', 12, 75,  'Maci Robel', '2023-07-07', 'Y');

-- Card Cross References (from cardxref.txt / CVACT03Y)
INSERT INTO account_card_xref (card_num, cust_id, acct_id) VALUES
('0500024453765740', 5, 50),
('0683586198171516', 2, 27),
('0923877193247330', 2, 2),
('0927987108636232', 2, 20),
('0982496213629795', 1, 12);

-- Disclosure Groups (from discgrp.txt / CVTRA02Y)
INSERT INTO disclosure_groups (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES
('A000000000', '01', 1, 15.00),
('A000000000', '01', 2, 25.00),
('A000000000', '01', 3, 25.00),
('A000000000', '01', 4, 25.00),
('A000000000', '02', 1, 0.00);

-- Transaction Category Balances (from tcatbal.txt / CVTRA01Y)
INSERT INTO transaction_category_balances (acct_id, tran_type_cd, tran_cat_cd, balance) VALUES
(1, '01', 1, 0.00),
(2, '01', 1, 0.00),
(3, '01', 1, 0.00),
(4, '01', 1, 0.00),
(5, '01', 1, 0.00);
