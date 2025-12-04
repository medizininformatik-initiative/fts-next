-- Migration 2024.1.x --> 2024.2.0
CREATE TABLE `mpsn` (
                        `originalValue` varchar(255) NOT NULL,
                        `pseudonym` varchar(255) NOT NULL,
                        `domain` varchar(255) NOT NULL,
                        PRIMARY KEY (`domain`,`originalValue`,`pseudonym`),
                        UNIQUE KEY `domain_pseudonym` (`domain`,`pseudonym`),
                        INDEX `domain_originalValue` (`domain`,`originalValue`),
                        CONSTRAINT `FK_DOMAIN_MPSN` FOREIGN KEY (`domain`) REFERENCES `domain` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

-- Migration 2024.2.x --> 2024.3.x
-- Fix default for stat_entry in gpas installations before version 1.11
ALTER TABLE stat_entry MODIFY COLUMN ENTRYDATE timestamp(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL;

DROP VIEW IF EXISTS `psn_domain_count`;
CREATE VIEW `psn_domain_count` AS
SELECT
    CONCAT('pseudonyms_per_domain.', `d`.`name`) AS `attribut`,
    (COUNT(`p`.`pseudonym`) + COUNT(`m`.`pseudonym`)) AS `value`
FROM
    `domain` `d`
        LEFT JOIN `psn` `p` ON `p`.`domain` = `d`.`name`
        LEFT JOIN `mpsn` `m` ON `m`.`domain` = `d`.`name`
WHERE
    `d`.`name` != 'internal_anonymisation_domain'
GROUP BY `d`.`name`;

-- Migration 2024.3.x --> 2025.1.x
ALTER TABLE `domain`
    ADD COLUMN `expiration_properties` varchar(255) DEFAULT NULL;

ALTER TABLE `psn`
    ADD COLUMN `encoded_expiration_date` smallint DEFAULT NULL;

ALTER TABLE `mpsn`
    ADD COLUMN `encoded_expiration_date` smallint DEFAULT NULL;

-- Migration 2025.1.x --> 2025.1.2
-- Correction DB-schema
ALTER TABLE `domain` MODIFY COLUMN `name` VARCHAR(255) NOT NULL;
ALTER TABLE `domain` MODIFY COLUMN `properties` VARCHAR(1023) DEFAULT NULL;
ALTER TABLE `domain_parents` MODIFY COLUMN `domain` VARCHAR(255) NOT NULL;
ALTER TABLE `domain_parents` MODIFY COLUMN `parentDomain` VARCHAR(255) NOT NULL;
ALTER TABLE `psn` MODIFY COLUMN `pseudonym` VARCHAR(255) NOT NULL;
ALTER TABLE `sequence` DEFAULT COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `sequence` MODIFY COLUMN `SEQ_NAME` VARCHAR(50) NOT NULL;

-- add stored procedure
DELIMITER $
CREATE PROCEDURE convert_to_multi_psn_domain(
    IN in_domain VARCHAR(255)
)
BEGIN
START TRANSACTION;

REPLACE INTO mpsn (originalValue, pseudonym, domain, encoded_expiration_date)
SELECT originalValue, pseudonym, domain, encoded_expiration_date FROM psn WHERE domain = in_domain;

DELETE FROM psn WHERE domain = in_domain;

UPDATE domain
SET properties = CONCAT(
        REGEXP_REPLACE(REGEXP_REPLACE(
                               IFNULL(properties,''), ';?MULTI_PSN_DOMAIN=[^;]+', ''
                       ), '^;', ''),
        IF(IFNULL(properties,'')='' OR RIGHT(properties, 1)=';', '', ';'),
        'MULTI_PSN_DOMAIN=true;'
                 )
WHERE name = in_domain;

COMMIT;
END$
DELIMITER ;
