-- MySQL dump 10.13  Distrib 8.3.0, for Linux (x86_64)
--
-- Host: 10.100.2.130    Database: gpas
-- ------------------------------------------------------
-- Server version	8.3.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE
DATABASE /*!32312 IF NOT EXISTS*/ `gpas` /*!40100 DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_bin */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE
`gpas`;

--
-- Table structure for table `domain`
--

DROP TABLE IF EXISTS `domain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `domain`
(
    `name`             varchar(255) COLLATE utf8mb3_bin NOT NULL,
    `label`            varchar(255) COLLATE utf8mb3_bin          DEFAULT NULL,
    `alphabet`         varchar(255) COLLATE utf8mb3_bin          DEFAULT NULL,
    `comment`          varchar(255) COLLATE utf8mb3_bin          DEFAULT NULL,
    `generatorClass`   varchar(255) COLLATE utf8mb3_bin          DEFAULT NULL,
    `properties`       varchar(1023) COLLATE utf8mb3_bin         DEFAULT NULL,
    `create_timestamp` timestamp(3)                     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_timestamp` timestamp(3)                     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `domain`
--

LOCK
TABLES `domain` WRITE;
/*!40000 ALTER TABLE `domain` DISABLE KEYS */;
INSERT INTO `domain`
VALUES ('MII', 'MII', 'org.emau.icmvc.ganimed.ttp.psn.alphabets.Numbers', '',
        'org.emau.icmvc.ganimed.ttp.psn.generator.Verhoeff',
        'FORCE_CACHE=DEFAULT;INCLUDE_PREFIX_IN_CHECK_DIGIT_CALCULATION=false;INCLUDE_SUFFIX_IN_CHECK_DIGIT_CALCULATION=false;MAX_DETECTED_ERRORS=2;PSN_LENGTH=8;PSN_PREFIX=;PSN_SUFFIX=;PSNS_DELETABLE=false;USE_LAST_CHAR_AS_DELIMITER_AFTER_X_CHARS=0;VALIDATE_VALUES_VIA_PARENTS=OFF;',
        '2024-06-28 09:22:57.871', '2024-06-28 09:22:57.871');
/*!40000 ALTER TABLE `domain` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `domain_parents`
--

DROP TABLE IF EXISTS `domain_parents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `domain_parents`
(
    `domain`       varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
    `parentDomain` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
    PRIMARY KEY (`domain`, `parentDomain`),
    KEY            `FK_domain_parents_domain_2` (`parentDomain`),
    CONSTRAINT `FK_domain_parents_domain` FOREIGN KEY (`domain`) REFERENCES `domain` (`name`),
    CONSTRAINT `FK_domain_parents_domain_2` FOREIGN KEY (`parentDomain`) REFERENCES `domain` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `domain_parents`
--

LOCK
TABLES `domain_parents` WRITE;
/*!40000 ALTER TABLE `domain_parents` DISABLE KEYS */;
/*!40000 ALTER TABLE `domain_parents` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `psn`
--

DROP TABLE IF EXISTS `psn`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `psn`
(
    `originalValue` varchar(255) COLLATE utf8mb3_bin NOT NULL,
    `pseudonym`     varchar(255) COLLATE utf8mb3_bin DEFAULT NULL,
    `domain`        varchar(255) COLLATE utf8mb3_bin NOT NULL,
    PRIMARY KEY (`domain`, `originalValue`),
    UNIQUE KEY `domain_pseudonym` (`domain`,`pseudonym`),
    CONSTRAINT `FK_DOMAIN` FOREIGN KEY (`domain`) REFERENCES `domain` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `psn`
--

LOCK
TABLES `psn` WRITE;
/*!40000 ALTER TABLE `psn` DISABLE KEYS */;
/*!40000 ALTER TABLE `psn` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Temporary view structure for view `psn_domain_count`
--

DROP TABLE IF EXISTS `psn_domain_count`;
/*!50001 DROP VIEW IF EXISTS `psn_domain_count`*/;
SET
@saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `psn_domain_count` AS SELECT 
 1 AS `attribut`,
 1 AS `value`*/;
SET
character_set_client = @saved_cs_client;

--
-- Table structure for table `sequence`
--

DROP TABLE IF EXISTS `sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sequence`
(
    `SEQ_NAME`  varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
    `SEQ_COUNT` decimal(38, 0) DEFAULT NULL,
    PRIMARY KEY (`SEQ_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sequence`
--

LOCK
TABLES `sequence` WRITE;
/*!40000 ALTER TABLE `sequence` DISABLE KEYS */;
/*!40000 ALTER TABLE `sequence` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `stat_entry`
--

DROP TABLE IF EXISTS `stat_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stat_entry`
(
    `STAT_ENTRY_ID` bigint NOT NULL AUTO_INCREMENT,
    `ENTRYDATE`     timestamp(3) NULL DEFAULT NULL,
    PRIMARY KEY (`STAT_ENTRY_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stat_entry`
--

LOCK
TABLES `stat_entry` WRITE;
/*!40000 ALTER TABLE `stat_entry` DISABLE KEYS */;
INSERT INTO `stat_entry`
VALUES (53, '2024-06-28 09:22:26.885'),
       (54, '2024-06-28 09:22:29.623'),
       (55, '2024-06-28 09:22:30.567'),
       (56, '2024-06-28 09:22:31.455');
/*!40000 ALTER TABLE `stat_entry` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `stat_value`
--

DROP TABLE IF EXISTS `stat_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stat_value`
(
    `stat_value_id` bigint       DEFAULT NULL,
    `stat_value`    bigint       DEFAULT NULL,
    `stat_attr`     varchar(255) DEFAULT NULL,
    KEY             `FK_stat_value_stat_value_id` (`stat_value_id`),
    CONSTRAINT `FK_stat_value_stat_value_id` FOREIGN KEY (`stat_value_id`) REFERENCES `stat_entry` (`STAT_ENTRY_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stat_value`
--

LOCK
TABLES `stat_value` WRITE;
/*!40000 ALTER TABLE `stat_value` DISABLE KEYS */;
INSERT INTO `stat_value`
VALUES (53, 0, 'anonyms'),
       (53, 0, 'calculation_time'),
       (53, 1, 'domains'),
       (53, 4528, 'pseudonyms_per_domain.MII'),
       (53, 4528, 'pseudonyms'),
       (53, 0, 'anonyms_per_domain.MII'),
       (54, 0, 'anonyms'),
       (54, 0, 'calculation_time'),
       (54, 1, 'domains'),
       (54, 4528, 'pseudonyms_per_domain.MII'),
       (54, 4528, 'pseudonyms'),
       (54, 0, 'anonyms_per_domain.MII'),
       (55, 0, 'anonyms'),
       (55, 0, 'calculation_time'),
       (55, 1, 'domains'),
       (55, 4528, 'pseudonyms_per_domain.MII'),
       (55, 4528, 'pseudonyms'),
       (55, 0, 'anonyms_per_domain.MII'),
       (56, 0, 'anonyms'),
       (56, 0, 'calculation_time'),
       (56, 1, 'domains'),
       (56, 4528, 'pseudonyms_per_domain.MII'),
       (56, 4528, 'pseudonyms'),
       (56, 0, 'anonyms_per_domain.MII');
/*!40000 ALTER TABLE `stat_value` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Final view structure for view `psn_domain_count`
--

/*!50001 DROP VIEW IF EXISTS `psn_domain_count`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = latin1 */;
/*!50001 SET character_set_results     = latin1 */;
/*!50001 SET collation_connection      = latin1_swedish_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `psn_domain_count` AS select concat('pseudonyms_per_domain.',`t1`.`name`) AS `attribut`,count(`t2`.`pseudonym`) AS `value` from (`domain` `t1` join `psn` `t2` on((`t2`.`domain` = `t1`.`name`))) where (`t1`.`name` <> 'internal_anonymisation_domain') group by `t1`.`name` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
