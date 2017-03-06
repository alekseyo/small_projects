CREATE TABLE CLIENT (
        id NUMBER,
        login VARCHAR2(100) NOT NULL,
        password VARCHAR2(100) NOT NULL,
        balance NUMBER(10, 2)
);

ALTER TABLE client ADD CONSTRAINT pk_client PRIMARY KEY (id);
ALTER TABLE client ADD CONSTRAINT uk_client_login UNIQUE(login);

CREATE SEQUENCE seq_client START WITH 1;