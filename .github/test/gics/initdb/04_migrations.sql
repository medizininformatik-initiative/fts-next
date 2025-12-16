-- Migration 2024.2.x --> 2024.3.x
-- Add column for policy changed check
ALTER TABLE `domain`
    ADD LAST_CHECK_TIMESTAMP TIMESTAMP NULL;

-- Fix default for stat_entry in gics installations before version 2.13
ALTER TABLE stat_entry MODIFY COLUMN ENTRYDATE timestamp (3) DEFAULT CURRENT_TIMESTAMP (3) NOT NULL;

-- for expirations of singedPolicies
ALTER TABLE signed_policy
    ADD EXPIRATION_PROPERTIES varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NULL;

-- Migration 2025.1.x --> 2025.2.x
START TRANSACTION;

-- 1. Drop foreign key, primary key, and indexes from signature
ALTER TABLE signature
DROP FOREIGN KEY FK_signature_CONSENT_DATE,
    DROP PRIMARY KEY,
DROP INDEX I_PRIMARY,
DROP INDEX I_FK_signature_CONSENT_DATE;

-- 2. Change column type of `TYPE` to varchar(50)
ALTER TABLE signature
    MODIFY COLUMN `TYPE` varchar(50) NOT NULL;

-- 3. Migrate old integer values to descriptive strings,
--    but only map to 'guardian' when both the flag is set AND the old type was '0'
UPDATE signature AS s
    JOIN consent AS c
ON s.CONSENT_DATE = c.CONSENT_DATE
    AND s.CONSENT_VIRTUAL_PERSON_ID = c.VIRTUAL_PERSON_ID
    AND s.CT_DOMAIN_NAME = c.CT_DOMAIN_NAME
    AND s.CT_NAME = c.CT_NAME
    AND s.CT_VERSION = c.CT_VERSION
    SET
        s.`TYPE` = CASE
        WHEN s.`TYPE` = '0' AND c.PATIENTSIGNATURE_IS_FROM_GUARDIAN = 1 THEN 'guardian'
        WHEN s.`TYPE` = '0' THEN 'participant'
        WHEN s.`TYPE` = '1' THEN 'physician'
        WHEN s.`TYPE` = '2' THEN 'guardian'
        ELSE s.`TYPE`
END
WHERE
    s.`TYPE` IN ('0','1','2');

-- 4. Remove the guardian-flag column from consent
ALTER TABLE consent
DROP COLUMN PATIENTSIGNATURE_IS_FROM_GUARDIAN;

-- 5. Add IDENTIFIERS column to signature
ALTER TABLE signature
    ADD COLUMN IDENTIFIERS varchar(255) AFTER TYPE;

-- 6. Populate IDENTIFIERS from non-empty consent.PHYSICIANID for physician-type signatures
UPDATE signature AS s
    JOIN consent AS c
ON s.CONSENT_DATE              = c.CONSENT_DATE
    AND s.CONSENT_VIRTUAL_PERSON_ID  = c.VIRTUAL_PERSON_ID
    AND s.CT_DOMAIN_NAME             = c.CT_DOMAIN_NAME
    AND s.CT_NAME                    = c.CT_NAME
    AND s.CT_VERSION                 = c.CT_VERSION
    SET
        s.IDENTIFIERS = CONCAT('{"physician_id":"', c.PHYSICIANID, '"}')
WHERE
    s.TYPE = 'physician'
  AND c.PHYSICIANID IS NOT NULL
  AND c.PHYSICIANID <> '';

-- 7. Remove PHYSICIANID column from consent
ALTER TABLE consent
DROP COLUMN PHYSICIANID;

-- 8. Recreate primary key, indexes, and foreign key on signature
ALTER TABLE signature
    ADD UNIQUE INDEX I_PRIMARY (
    `TYPE`,
    CONSENT_DATE,
    CONSENT_VIRTUAL_PERSON_ID,
    CT_DOMAIN_NAME,
    CT_NAME,
    CT_VERSION
    ),
    ADD PRIMARY KEY (
    `TYPE`,
    CONSENT_DATE,
    CONSENT_VIRTUAL_PERSON_ID,
    CT_DOMAIN_NAME,
    CT_NAME,
    CT_VERSION
    ),
    ADD INDEX I_FK_signature_CONSENT_DATE (
    CONSENT_DATE,
    CONSENT_VIRTUAL_PERSON_ID,
    CT_DOMAIN_NAME,
    CT_NAME,
    CT_VERSION
    ),
    ADD CONSTRAINT FK_signature_CONSENT_DATE
    FOREIGN KEY (
    CONSENT_DATE,
    CONSENT_VIRTUAL_PERSON_ID,
    CT_DOMAIN_NAME,
    CT_NAME,
    CT_VERSION
    )
    REFERENCES consent (
    CONSENT_DATE,
    VIRTUAL_PERSON_ID,
    CT_DOMAIN_NAME,
    CT_NAME,
    CT_VERSION
    );

ALTER TABLE consent_template
    ADD COLUMN CONFIG text;

COMMIT;

SHOW COLUMNS FROM consent;

ALTER TABLE free_text_def MODIFY COLUMN `LABEL` TEXT CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL NULL;

ALTER TABLE consent ADD COLUMN SCAN_STORAGE_LOCATION varchar(4096) DEFAULT NULL AFTER `EXPIRATION_PROPERTIES`;
ALTER TABLE consent_template CHANGE COLUMN `FINALISED` `STATE` TINYINT(1);
