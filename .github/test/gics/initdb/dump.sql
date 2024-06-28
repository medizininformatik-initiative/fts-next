-- MySQL dump 10.13  Distrib 8.3.0, for Linux (x86_64)
--
-- Host: 10.100.3.130    Database: gics
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

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `gics` /*!40100 DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_bin */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `gics`;

--
-- Table structure for table `alias`
--

DROP TABLE IF EXISTS `alias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alias` (
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL,
  `ORIG_SI_VALUE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ORIG_SIT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ORIG_SIT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ALIAS_SI_VALUE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ALIAS_SIT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `ALIAS_SIT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `DEACTIVATE_TIMESTAMP` timestamp(3) NULL DEFAULT NULL,
  PRIMARY KEY (`CREATE_TIMESTAMP`,`ALIAS_SI_VALUE`,`ORIG_SI_VALUE`,`ALIAS_SIT_NAME`,`ALIAS_SIT_DOMAIN_NAME`,`ORIG_SIT_DOMAIN_NAME`,`ORIG_SIT_NAME`),
  KEY `FK_ALIAS_SIGNER_ID` (`ALIAS_SI_VALUE`,`ALIAS_SIT_DOMAIN_NAME`,`ALIAS_SIT_NAME`),
  KEY `FK_ORIG_SIGNER_ID` (`ORIG_SI_VALUE`,`ORIG_SIT_DOMAIN_NAME`,`ORIG_SIT_NAME`),
  CONSTRAINT `FK_ALIAS_SIGNER_ID` FOREIGN KEY (`ALIAS_SI_VALUE`, `ALIAS_SIT_DOMAIN_NAME`, `ALIAS_SIT_NAME`) REFERENCES `signer_id` (`VALUE`, `SIT_DOMAIN_NAME`, `SIT_NAME`),
  CONSTRAINT `FK_ORIG_SIGNER_ID` FOREIGN KEY (`ORIG_SI_VALUE`, `ORIG_SIT_DOMAIN_NAME`, `ORIG_SIT_NAME`) REFERENCES `signer_id` (`VALUE`, `SIT_DOMAIN_NAME`, `SIT_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consent`
--

DROP TABLE IF EXISTS `consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent` (
  `PATIENTSIGNATURE_IS_FROM_GUARDIAN` bit(1) DEFAULT b'0',
  `PHYSICIANID` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `VIRTUAL_PERSON_ID` bigint NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXPIRATION_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `VALID_FROM` datetime(3) DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  KEY `I_FK_consent_CT_NAME` (`CT_NAME`,`CT_VERSION`,`CT_DOMAIN_NAME`),
  KEY `I_FK_consent_VIRTUAL_PERSON_ID` (`VIRTUAL_PERSON_ID`),
  CONSTRAINT `FK_consent_CT_NAME` FOREIGN KEY (`CT_NAME`, `CT_VERSION`, `CT_DOMAIN_NAME`) REFERENCES `consent_template` (`NAME`, `VERSION`, `DOMAIN_NAME`),
  CONSTRAINT `FK_consent_VIRTUAL_PERSON_ID` FOREIGN KEY (`VIRTUAL_PERSON_ID`) REFERENCES `virtual_person` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consent_scan`
--

DROP TABLE IF EXISTS `consent_scan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent_scan` (
  `SCANBASE64` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  `FILETYPE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `VIRTUAL_PERSON_ID` bigint NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `FILENAME` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  `UPLOAD_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`FHIR_ID`),
  KEY `FK_scan_CONSENT` (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  CONSTRAINT `FK_scan_CONSENT` FOREIGN KEY (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `consent_template`
--

DROP TABLE IF EXISTS `consent_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent_template` (
  `TITLE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXPIRATION_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VERSION` int NOT NULL,
  `DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TYPE` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `FOOTER` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `HEADER` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SCAN` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `VERSION_LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FINALISED` tinyint(1) DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`NAME`,`VERSION`,`DOMAIN_NAME`),
  UNIQUE KEY `I_PRIMARY` (`NAME`,`VERSION`,`DOMAIN_NAME`),
  KEY `FK_consent_template_SCAN_BASE64` (`SCAN`),
  KEY `I_FK_consent_template_TITLE` (`TITLE`),
  KEY `I_FK_module_TITLE` (`TITLE`),
  KEY `I_FK_consent_template_HEADER` (`HEADER`),
  KEY `I_FK_consent_template_FOOTER` (`FOOTER`),
  KEY `I_FK_consent_template_DOMAIN_NAME` (`DOMAIN_NAME`),
  CONSTRAINT `FK_consent_template_DOMAIN_NAME` FOREIGN KEY (`DOMAIN_NAME`) REFERENCES `domain` (`NAME`),
  CONSTRAINT `FK_consent_template_FOOTER` FOREIGN KEY (`FOOTER`) REFERENCES `text` (`ID`),
  CONSTRAINT `FK_consent_template_HEADER` FOREIGN KEY (`HEADER`) REFERENCES `text` (`ID`),
  CONSTRAINT `FK_consent_template_SCAN_BASE64` FOREIGN KEY (`SCAN`) REFERENCES `consent_template_scan` (`ID`),
  CONSTRAINT `FK_consent_template_TITLE` FOREIGN KEY (`TITLE`) REFERENCES `text` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `consent_template`
--

LOCK TABLES `consent_template` WRITE;
/*!40000 ALTER TABLE `consent_template` DISABLE KEYS */;
INSERT INTO `consent_template` VALUES ('MII_###_Patienteneinwilligung MII_###_1006003_###_TEMPLATE_TITLE','Version 1.6d der MII Vorlage, Stand: 16.04.2020, allgemeine Version (PatDat,KKDat,BioMat,ZModul) mit Platzhaltern  und den OIDs der ArtDecor Version von Juli 2021 (questionOIDs) gemäß ArtDecor und Rückmeldung AG Consent','study=mii;optional=false;orderNr=1;fhirPolicyCodesystem=2.16.840.1.113883.3.1937.777.24.5.3;fhirAnswerCodeSystem=2.16.840.1.113883.3.1937.777.24.5.2;fhirAnswerValueSet=https://www.medizininformatik-initiative.de/fhir/ValueSet/MiiConsentAnswerValueSet;fhirAnswerCodeYes=2.16.840.1.113883.3.1937.777.24.5.2.1;fhirAnswerCodeNo=2.16.840.1.113883.3.1937.777.24.5.2.2;fhirAnswerCodeUnknown=2.16.840.1.113883.3.1937.777.24.5.2.3;fhirAnswerDisplayYes=gültig;fhirAnswerDisplayNo=ungültig;fhirAnswerDisplayUnknown=unbekannt','VALIDITY_PERIOD=P30Y;','Patienteneinwilligung MII',1006003,'MII','CONSENT','MII_###_Patienteneinwilligung MII_###_1006003_###_FOOTER','MII_###_Patienteneinwilligung MII_###_1006003_###_HEADER','MII_###_Patienteneinwilligung MII_###_1006003','2023-06-29 08:03:30.444','2023-06-29 08:03:38.099','Patienteninformation und -einwilligung','AG Consent',1,'5f7af761-e3e0-42a6-a7de-d800ee11b85c');
/*!40000 ALTER TABLE `consent_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `consent_template_scan`
--

DROP TABLE IF EXISTS `consent_template_scan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consent_template_scan` (
  `ID` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SCANBASE64` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  `FILETYPE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `consent_template_scan`
--

LOCK TABLES `consent_template_scan` WRITE;
/*!40000 ALTER TABLE `consent_template_scan` DISABLE KEYS */;
INSERT INTO `consent_template_scan` VALUES ('MII_###_Patienteneinwilligung MII_###_1006003','','');
/*!40000 ALTER TABLE `consent_template_scan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `domain`
--

DROP TABLE IF EXISTS `domain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `domain` (
  `NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CT_VERSION_CONVERTER` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXPIRATION_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `MODULE_VERSION_CONVERTER` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `POLICY_VERSION_CONVERTER` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LOGO` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  `FINALISED` tinyint(1) DEFAULT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`NAME`),
  UNIQUE KEY `I_PRIMARY` (`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `domain`
--

LOCK TABLES `domain` WRITE;
/*!40000 ALTER TABLE `domain` DISABLE KEYS */;
INSERT INTO `domain` VALUES ('MII','Medizininformatik Initiative','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorCharVersionConverter','SAFE_SIGNERID_TYPE = Pseudonym','','MII','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorVersionConverter','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorVersionConverter','REVOKE_IS_PERMANENT=false;TAKE_HIGHEST_VERSION_INSTEAD_OF_NEWEST=false;SCANS_ARE_NOT_MANDATORY_FOR_ACCEPTED_CONSENTS=false;SCANS_SIZE_LIMIT=10485760;TAKE_MOST_SPECIFIC_PERIOD_OF_VALIDITY_INSTEAD_OF_SHORTEST=false;VALID_QC_TYPES=not_checked,checked_minor_faults,checked_no_faults,###_auto_generated_###;INVALID_QC_TYPES=checked_major_faults,invalidated;DEFAULT_QC_TYPE=not_checked',NULL,1,'2023-06-29 08:03:30.154','2023-06-29 08:03:30.219','8ceeb823-cbb8-4113-97df-f73c3d8a37f4');
/*!40000 ALTER TABLE `domain` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `free_text_def`
--

DROP TABLE IF EXISTS `free_text_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `free_text_def` (
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CONVERTERSTRING` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `POS` int DEFAULT NULL,
  `REQUIRED` bit(1) DEFAULT b'0',
  `TYPE` int DEFAULT NULL,
  `FREETEXT_NAME` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FINALISED` tinyint(1) DEFAULT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`FREETEXT_NAME`,`DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`FREETEXT_NAME`,`DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  KEY `I_FK_free_text_def_CT_NAME` (`CT_NAME`,`CT_VERSION`,`DOMAIN_NAME`),
  CONSTRAINT `FK_free_text_def_CT_NAME` FOREIGN KEY (`CT_NAME`, `CT_VERSION`, `DOMAIN_NAME`) REFERENCES `consent_template` (`NAME`, `VERSION`, `DOMAIN_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `free_text_def`
--

LOCK TABLES `free_text_def` WRITE;
/*!40000 ALTER TABLE `free_text_def` DISABLE KEYS */;
/*!40000 ALTER TABLE `free_text_def` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `free_text_val`
--

DROP TABLE IF EXISTS `free_text_val`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `free_text_val` (
  `VALUE` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  `FREETEXTDEV_NAME` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `CONSENT_VIRTUAL_PERSON_ID` bigint NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`FREETEXTDEV_NAME`,`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`FREETEXTDEV_NAME`,`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  KEY `I_FK_free_text_val_CONSENT_DATE` (`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  CONSTRAINT `FK_free_text_val_CONSENT_DATE` FOREIGN KEY (`CONSENT_DATE`, `CONSENT_VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `free_text_val`
--

LOCK TABLES `free_text_val` WRITE;
/*!40000 ALTER TABLE `free_text_val` DISABLE KEYS */;
/*!40000 ALTER TABLE `free_text_val` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mapped_consent_template`
--

DROP TABLE IF EXISTS `mapped_consent_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mapped_consent_template` (
  `FROM_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `FROM_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `FROM_VERSION` int NOT NULL,
  `TO_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TO_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TO_VERSION` int NOT NULL,
  PRIMARY KEY (`FROM_NAME`,`FROM_DOMAIN_NAME`,`FROM_VERSION`,`TO_NAME`,`TO_DOMAIN_NAME`,`TO_VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mapped_consent_template`
--

LOCK TABLES `mapped_consent_template` WRITE;
/*!40000 ALTER TABLE `mapped_consent_template` DISABLE KEYS */;
/*!40000 ALTER TABLE `mapped_consent_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `module`
--

DROP TABLE IF EXISTS `module`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `module` (
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TITLE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VERSION` int NOT NULL,
  `DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TEXT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SHORT_TEXT` varchar(5000) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FINALISED` tinyint(1) DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`NAME`,`VERSION`,`DOMAIN_NAME`),
  UNIQUE KEY `I_PRIMARY` (`NAME`,`VERSION`,`DOMAIN_NAME`),
  KEY `FK_module_TITLE` (`TITLE`),
  KEY `I_FK_module_TEXT` (`TEXT`),
  KEY `I_FK_module_DOMAIN_NAME` (`DOMAIN_NAME`),
  CONSTRAINT `FK_module_DOMAIN_NAME` FOREIGN KEY (`DOMAIN_NAME`) REFERENCES `domain` (`NAME`),
  CONSTRAINT `FK_module_TEXT` FOREIGN KEY (`TEXT`) REFERENCES `text` (`ID`),
  CONSTRAINT `FK_module_TITLE` FOREIGN KEY (`TITLE`) REFERENCES `text` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `module`
--

LOCK TABLES `module` WRITE;
/*!40000 ALTER TABLE `module` DISABLE KEYS */;
INSERT INTO `module` VALUES ('','','MII_###_Geltungsdauer_###_1006_###_MODULE_TITLE','Geltungsdauer',1006,'MII','MII_###_Geltungsdauer_###_1006_###_MODUL','','2023-06-29 08:03:30.328','2023-06-29 08:03:38.099','Geltungsdauer',1,'ae44d58-4b73b-49d1-bc9b-b1d951f500f6'),('','','MII_###_KKDAT_Intro_###_1006_###_MODULE_TITLE','KKDAT_Intro',1006,'MII','MII_###_KKDAT_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.347','2023-06-29 08:03:38.097','KKDAT_Intro',1,'b99516e-9ede6-4b8f-ad9e-399392cc5901'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1594','MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE','KKDAT_prospektiv_uebertragen_speichern_nutzen',1008,'MII','MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.353','2023-06-29 08:03:38.098','KKDAT_prospektiv_uebertragen_speichern_nutzen',1,'0401356-ee5a7-46cb-93ce-602f267b2a8d'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1593','MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1008,'MII','MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.370','2023-06-29 08:03:38.098','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1,'b424ece-975f0-4c17-9ffd-a05edab4f7d7'),('','','MII_###_PATDAT_Intro_###_1006_###_MODULE_TITLE','PATDAT_Intro',1006,'MII','MII_###_PATDAT_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.381','2023-06-29 08:03:38.096','PATDAT_Intro',1,'52bb88e-ce3d6-4c03-abc1-41977e6ef924'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1567','MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODULE_TITLE','PATDAT_erheben_speichern_nutzen',1008,'MII','MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.387','2023-06-29 08:03:38.097','PATDAT_erheben_speichern_nutzen',1,'b24f571-dc68f-4401-9862-923f7ab35165'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1597','MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODULE_TITLE','Rekontaktierung_Ergaenzungen',1007,'MII','MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODUL','','2023-06-29 08:03:30.403','2023-06-29 08:03:38.099','Rekontaktierung_Ergaenzungen',1,'49d5779-8ccba-469e-90d1-3df7f4b9ded9'),('','','MII_###_Rekontaktierung_Intro_###_1006_###_MODULE_TITLE','Rekontaktierung_Intro',1006,'MII','MII_###_Rekontaktierung_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.413','2023-06-29 08:03:38.098','Rekontaktierung_Intro',1,'88353e1-0a453-4fb4-a883-5911b5d1bc54'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1598','MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODULE_TITLE','Rekontaktierung_Zusatzbefund',1008,'MII','MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODUL','','2023-06-29 08:03:30.420','2023-06-29 08:03:38.099','Rekontaktierung_Zusatzbefund',1,'e05820e-33a2b-4352-bc4c-3067a835a2d2'),('','','MII_###_Widerrufsrecht_###_1006_###_MODULE_TITLE','Widerrufsrecht',1006,'MII','MII_###_Widerrufsrecht_###_1006_###_MODUL','','2023-06-29 08:03:30.428','2023-06-29 08:03:38.099','Widerrufsrecht',1,'ed1fa1b-f372e-4902-9e9f-78b9a2ffc0d5');
/*!40000 ALTER TABLE `module` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `module_consent_template`
--

DROP TABLE IF EXISTS `module_consent_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `module_consent_template` (
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `DEFAULTCONSENTSTATUS` int DEFAULT NULL,
  `DISPLAYCHECKBOXES` bigint DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `MANDATORY` bit(1) DEFAULT b'0',
  `ORDER_NUMBER` int DEFAULT NULL,
  `CT_DOMAIN` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `M_DOMAIN` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `M_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `M_VERSION` int NOT NULL,
  `PARENT_M_DOMAIN` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_M_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `PARENT_M_VERSION` int DEFAULT NULL,
  `EXPIRATION_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`CT_DOMAIN`,`CT_NAME`,`CT_VERSION`,`M_DOMAIN`,`M_NAME`,`M_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`CT_DOMAIN`,`CT_NAME`,`CT_VERSION`,`M_DOMAIN`,`M_NAME`,`M_VERSION`),
  KEY `I_FK_module_consent_template_PARENT_M_NAME` (`PARENT_M_NAME`,`PARENT_M_VERSION`,`PARENT_M_DOMAIN`),
  KEY `I_FK_module_consent_template_CT_NAME` (`CT_NAME`,`CT_VERSION`,`CT_DOMAIN`),
  KEY `I_FK_module_consent_template_M_NAME` (`M_NAME`,`M_VERSION`,`M_DOMAIN`),
  CONSTRAINT `FK_module_consent_template_CT_NAME` FOREIGN KEY (`CT_NAME`, `CT_VERSION`, `CT_DOMAIN`) REFERENCES `consent_template` (`NAME`, `VERSION`, `DOMAIN_NAME`),
  CONSTRAINT `FK_module_consent_template_M_NAME` FOREIGN KEY (`M_NAME`, `M_VERSION`, `M_DOMAIN`) REFERENCES `module` (`NAME`, `VERSION`, `DOMAIN_NAME`),
  CONSTRAINT `FK_module_consent_template_PARENT_M_NAME` FOREIGN KEY (`PARENT_M_NAME`, `PARENT_M_VERSION`, `PARENT_M_DOMAIN`) REFERENCES `module` (`NAME`, `VERSION`, `DOMAIN_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `module_consent_template`
--

LOCK TABLES `module_consent_template` WRITE;
/*!40000 ALTER TABLE `module_consent_template` DISABLE KEYS */;
INSERT INTO `module_consent_template` VALUES (NULL,NULL,0,NULL,_binary '\0',11,'MII','Patienteneinwilligung MII',1006003,'MII','Geltungsdauer',1006,NULL,NULL,NULL,'','2fed9e5d-e9af-481a-a00f-6e1f13aa25d2'),(NULL,NULL,0,NULL,_binary '\0',2,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_Intro',1006,NULL,NULL,NULL,'','3d396549-c717-4e01-8afa-672dfefe4d6e'),(NULL,NULL,3,NULL,_binary '\0',4,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_prospektiv_uebertragen_speichern_nutzen',1008,NULL,NULL,NULL,'','30420045-b334-42b2-bf7e-88946473c0fd'),(NULL,NULL,3,NULL,_binary '\0',3,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1008,NULL,NULL,NULL,'','7fd43530-877d-451b-8949-1efbd64820dc'),(NULL,NULL,0,NULL,_binary '\0',0,'MII','Patienteneinwilligung MII',1006003,'MII','PATDAT_Intro',1006,NULL,NULL,NULL,'','afd4e142-a202-46b1-b2da-af0af4bf0512'),(NULL,NULL,3,NULL,_binary '',1,'MII','Patienteneinwilligung MII',1006003,'MII','PATDAT_erheben_speichern_nutzen',1008,NULL,NULL,NULL,'','3f7c5c6e-48b5-42cc-bf3b-762f49f00079'),(NULL,NULL,3,NULL,_binary '\0',9,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Ergaenzungen',1007,NULL,NULL,NULL,'','3742c8ac-016f-4234-b226-b0364691b805'),(NULL,NULL,0,NULL,_binary '\0',8,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Intro',1006,NULL,NULL,NULL,'','f4089717-5317-4522-a978-a918f21765b2'),(NULL,NULL,3,NULL,_binary '\0',10,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Zusatzbefund',1008,NULL,NULL,NULL,'','3f1dec8b-9afc-4ce3-b9a6-ecb23975ca20'),(NULL,NULL,0,NULL,_binary '\0',12,'MII','Patienteneinwilligung MII',1006003,'MII','Widerrufsrecht',1006,NULL,NULL,NULL,'','e87d39d3-0736-4976-be9c-39fcbd952dcd');
/*!40000 ALTER TABLE `module_consent_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `module_policy`
--

DROP TABLE IF EXISTS `module_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `module_policy` (
  `P_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `P_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `P_VERSION` int NOT NULL,
  `M_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `M_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `M_VERSION` int NOT NULL,
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXPIRATION_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`P_NAME`,`P_DOMAIN_NAME`,`P_VERSION`,`M_NAME`,`M_DOMAIN_NAME`,`M_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`P_NAME`,`P_DOMAIN_NAME`,`P_VERSION`,`M_NAME`,`M_DOMAIN_NAME`,`M_VERSION`),
  KEY `I_FK_MODULE_POLICY_M_NAME` (`M_NAME`,`M_VERSION`,`M_DOMAIN_NAME`),
  KEY `I_FK_MODULE_POLICY_P_NAME` (`P_NAME`,`P_VERSION`,`P_DOMAIN_NAME`),
  CONSTRAINT `FK_MODULE_POLICY_M_NAME` FOREIGN KEY (`M_NAME`, `M_VERSION`, `M_DOMAIN_NAME`) REFERENCES `module` (`NAME`, `VERSION`, `DOMAIN_NAME`),
  CONSTRAINT `FK_MODULE_POLICY_P_NAME` FOREIGN KEY (`P_NAME`, `P_VERSION`, `P_DOMAIN_NAME`) REFERENCES `policy` (`NAME`, `VERSION`, `DOMAIN_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `module_policy`
--

LOCK TABLES `module_policy` WRITE;
/*!40000 ALTER TABLE `module_policy` DISABLE KEYS */;
INSERT INTO `module_policy` VALUES ('IDAT_bereitstellen_EU_DSGVO_konform','MII',1001,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','79013dcc-569e-4dfd-b53c-e161bbc2a4ee'),('IDAT_erheben','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','fac2e451-a66d-4a62-9f33-aef79c99c9a6'),('IDAT_speichern_verarbeiten','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','84df98ba-3115-4c35-b945-43d4428c08b2'),('IDAT_zusammenfuehren_Dritte','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','462f4dcf-492f-47ed-9c3e-b9a13feb0d82'),('KKDAT_5J_pro_speichern_verarbeiten','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','10edaa59-9f10-4d7f-8d56-ea853d0f41a8'),('KKDAT_5J_pro_uebertragen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','934e30b2-6f72-4c9e-810e-765062d854fa'),('KKDAT_5J_pro_wissenschaftlich_nutzen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','6d560695-0ada-424d-9a9d-94fef5d69b2f'),('KKDAT_5J_retro_speichern_verarbeiten','MII',1001,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','7ec163ac-244c-4b4f-ad3b-95396c82d384'),('KKDAT_5J_retro_uebertragen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,'singleUseOnly=true','','8e5e163e-2845-48bf-888b-30091ac253a4'),('KKDAT_5J_retro_wissenschaftlich_nutzen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','acdb25cc-c257-4981-9657-82a5e1b3449c'),('KKDAT_KVNR_5J_pro_uebertragen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','a8e43166-4171-4a84-8c8f-30c5e777da43'),('KKDAT_KVNR_5J_retro_uebertragen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','306fae23-c6c4-41f6-b155-203a03559cb7'),('MDAT_erheben','MII',1001,'PATDAT_erheben_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','e24f12be-9adc-44f1-b3f8-357cac463bd8'),('MDAT_speichern_verarbeiten','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','a40e8901-c643-4558-bccb-4c937e4ec1a1'),('MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','1d59e1dd-3782-436f-8834-d98f07e94832'),('MDAT_zusammenfuehren_Dritte','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','d0d49784-3d9c-4967-9e79-86cbfab1a439'),('Rekontaktierung_Ergebnisse_erheblicher_Bedeutung','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','80c262ad-981f-4bb2-8ec6-8a212ed354a5'),('Rekontaktierung_Verknuepfung_Datenbanken','MII',1000,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','70d18001-467d-42ab-aefb-961c44c826e5'),('Rekontaktierung_Zusatzbefund','MII',1000,'Rekontaktierung_Zusatzbefund','MII',1008,NULL,NULL,'','7a0b2b08-b9b5-42b0-8bde-ee4446c52b99'),('Rekontaktierung_weitere_Erhebung','MII',1001,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','21f0c8e4-8446-41ce-8d69-35d823d5bda2'),('Rekontaktierung_weitere_Studien','MII',1001,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','79a20c2f-cab5-4b9f-b214-7047a09ddeb5');
/*!40000 ALTER TABLE `module_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `policy`
--

DROP TABLE IF EXISTS `policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `policy` (
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `EXTERN_PROPERTIES` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VERSION` int NOT NULL,
  `DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `FINALISED` tinyint(1) DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`NAME`,`VERSION`,`DOMAIN_NAME`),
  UNIQUE KEY `I_PRIMARY` (`NAME`,`VERSION`,`DOMAIN_NAME`),
  KEY `I_FK_policy_DOMAIN_NAME` (`DOMAIN_NAME`),
  CONSTRAINT `FK_policy_DOMAIN_NAME` FOREIGN KEY (`DOMAIN_NAME`) REFERENCES `domain` (`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `policy`
--

LOCK TABLES `policy` WRITE;
/*!40000 ALTER TABLE `policy` DISABLE KEYS */;
INSERT INTO `policy` VALUES ('Herausgabe identifizierender Daten (IDAT) an verantwortliche Stelle zur weiteren Verarbeitung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.5','IDAT_bereitstellen_EU_DSGVO_konform',1001,'MII','2023-06-29 08:03:30.230','2023-06-29 08:03:38.097','IDAT_bereitstellen_EU_DSGVO_konform',1,'15299f7b-b878-410c-8dc0-8c0dec3c485c'),('Erfassung neuer identifizierender Daten (IDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.2','IDAT_erheben',1000,'MII','2023-06-29 08:03:30.240','2023-06-29 08:03:38.097','IDAT_erheben',1,'977a635c-759b-45ec-bde7-3b0285d764ae'),('Speicherung und Verarbeitung identifizierender Daten (IDAT)  in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.3','IDAT_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.245','2023-06-29 08:03:38.096','IDAT_speichern_verarbeiten',1,'54fa6eca-72f5-433c-a512-9c65aea61ed4'),('Zusammenführung identifizierender Daten (IDAT) mit Dritten Forschungspartnern','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.4','IDAT_zusammenfuehren_Dritte',1000,'MII','2023-06-29 08:03:30.250','2023-06-29 08:03:38.097','IDAT_zusammenfuehren_Dritte',1,'df35530b-2654-4c26-8de6-50bdaccdfcf0'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv speichern und verarbeiten in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.16','KKDAT_5J_pro_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.254','2023-06-29 08:03:38.098','KKDAT_5J_pro_speichern_verarbeiten',1,'754f5ef7-0f3a-4ec5-a834-1017023a32ca'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv übertragen','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.15','KKDAT_5J_pro_uebertragen',1000,'MII','2023-06-29 08:03:30.258','2023-06-29 08:03:38.098','KKDAT_5J_pro_uebertragen',1,'ebf71aa9-886d-4b6d-ad02-6fa525a75135'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv wissenschaftlich nutzen ','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.17','KKDAT_5J_pro_wissenschaftlich_nutzen',1000,'MII','2023-06-29 08:03:30.263','2023-06-29 08:03:38.098','KKDAT_5J_pro_wissenschaftlich_nutzen',1,'e1c2e4fe-9ce6-479f-ac61-c1e2a5adc0c6'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv speichern und verarbeiten in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.12','KKDAT_5J_retro_speichern_verarbeiten',1001,'MII','2023-06-29 08:03:30.267','2023-06-29 08:03:38.098','KKDAT_5J_retro_speichern_verarbeiten',1,'e975dd55-c63b-430c-9518-e5d66cf7bfc1'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv übertragen','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.11','KKDAT_5J_retro_uebertragen',1000,'MII','2023-06-29 08:03:30.271','2023-06-29 08:03:38.098','KKDAT_5J_retro_uebertragen',1,'f89d3fac-bd9f-4ff7-a063-038937be51c8'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv wissenschaftlich nutzen ','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.13','KKDAT_5J_retro_wissenschaftlich_nutzen',1000,'MII','2023-06-29 08:03:30.275','2023-06-29 08:03:38.098','KKDAT_5J_retro_wissenschaftlich_nutzen',1,'57f95ff2-c244-4095-a96f-73542de68ac1'),('Erlaubnis zur prospektiven Übermittlung der KVNr. an zuständige Stelle\n','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.39','KKDAT_KVNR_5J_pro_uebertragen',1000,'MII','2023-06-29 08:03:30.280','2023-06-29 08:03:38.098','KKDAT_KVNR_5J_pro_uebertragen',1,'80341c97-752b-4ab2-abac-2b580bc0f3d1'),('Erlaubnis zur retrospektiven Übermittlung der KVNr. an zuständige Stelle\n','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.38','KKDAT_KVNR_5J_retro_uebertragen',1000,'MII','2023-06-29 08:03:30.284','2023-06-29 08:03:38.098','KKDAT_KVNR_5J_retro_uebertragen',1,'5fcf72fe-77ce-4cb8-8ad9-3710a7f2385d'),('Erfassung medizinischer Daten (MDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.6','MDAT_erheben',1001,'MII','2023-06-29 08:03:30.288','2023-06-29 08:03:38.097','MDAT_erheben',1,'d113ec2c-47a3-4dcd-90dd-4d41ddf03d4c'),('Speicherung und Verarbeitung von medizinischen Daten innerhalb der verantwortlichen Stelle (MDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.7','MDAT_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.292','2023-06-29 08:03:38.097','MDAT_speichern_verarbeiten',1,'4a394fed-ccd1-4ca5-af86-e69406b822ed'),('Bereitstellung medizinischer Daten (MDAT) für wissenschaftliche Nutzung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.8','MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform',1000,'MII','2023-06-29 08:03:30.297','2023-06-29 08:03:38.096','MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform',1,'41c51dc8-0753-4cd5-8b6f-6db3ce65466a'),('Zusammenführung medizinischer Daten (MDAT) mit Dritten Forschungspartnern','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.9','MDAT_zusammenfuehren_Dritte',1000,'MII','2023-06-29 08:03:30.301','2023-06-29 08:03:38.097','MDAT_zusammenfuehren_Dritte',1,'740cd82b-2047-4942-8c8b-9deaee58ffd6'),('Rekontaktierung des Betroffenen bei Ergebnissen von erheblicher Bedeutung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.37','Rekontaktierung_Ergebnisse_erheblicher_Bedeutung',1000,'MII','2023-06-29 08:03:30.306','2023-06-29 08:03:38.097','Rekontaktierung_Ergebnisse_erheblicher_Bedeutung',1,'eb55a9ac-a518-4805-b2d0-7f7183470617'),('Rekontaktierung zur Verknüpfung von PatDat mit Info anderer Dbs','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.27','Rekontaktierung_Verknuepfung_Datenbanken',1000,'MII','2023-06-29 08:03:30.310','2023-06-29 08:03:38.099','Rekontaktierung_Verknuepfung_Datenbanken',1,'c4864b47-652a-456d-9876-3af7443d311f'),('Rekontaktierung bezüglich Zusatzbefund','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.31','Rekontaktierung_Zusatzbefund',1000,'MII','2023-06-29 08:03:30.315','2023-06-29 08:03:38.099','Rekontaktierung_Zusatzbefund',1,'94c5589e-c8aa-47cc-a2a8-33d884b9e94d'),('Rekontaktierung bezüglich Erhebung zusätzlicher Daten','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.28','Rekontaktierung_weitere_Erhebung',1001,'MII','2023-06-29 08:03:30.319','2023-06-29 08:03:38.099','Rekontaktierung_weitere_Erhebung',1,'31b8aeec-eb41-48af-b74d-a4e1a8338185'),('Rekontaktierung bezüglich Information zu neuen Forschungsvorhaben oder Studien','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.29','Rekontaktierung_weitere_Studien',1001,'MII','2023-06-29 08:03:30.323','2023-06-29 08:03:38.099','Rekontaktierung_weitere_Studien',1,'958af868-bdb7-42a2-9bd0-7313bacd676f');
/*!40000 ALTER TABLE `policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qc`
--

DROP TABLE IF EXISTS `qc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qc` (
  `COMMENT` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `INSPECTOR` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `VIRTUAL_PERSON_ID` bigint NOT NULL,
  `TYPE` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`) USING BTREE,
  KEY `I_qc_CONSENT_DATE` (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`) USING BTREE,
  CONSTRAINT `FK_qc_CONSENT_DATE` FOREIGN KEY (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qc_hist`
--

DROP TABLE IF EXISTS `qc_hist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qc_hist` (
  `COMMENT` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `EXTERN_PROPERTIES` varchar(4095) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `INSPECTOR` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `VIRTUAL_PERSON_ID` bigint NOT NULL,
  `TYPE` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `START_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `END_DATE` timestamp(3) NULL DEFAULT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`,`START_DATE`) USING BTREE,
  KEY `I_qc_CONSENT_DATE` (`CONSENT_DATE`,`VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`) USING BTREE,
  CONSTRAINT `FK_qc_hist_CONSENT_DATE` FOREIGN KEY (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sequence`
--

DROP TABLE IF EXISTS `sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sequence` (
  `SEQ_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SEQ_COUNT` decimal(38,0) DEFAULT NULL,
  PRIMARY KEY (`SEQ_NAME`),
  UNIQUE KEY `I_PRIMARY` (`SEQ_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sequence`
--

LOCK TABLES `sequence` WRITE;
/*!40000 ALTER TABLE `sequence` DISABLE KEYS */;
INSERT INTO `sequence` VALUES ('statistic_index',0),('virtual_person_index',50);
/*!40000 ALTER TABLE `sequence` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `signature`
--

DROP TABLE IF EXISTS `signature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signature` (
  `SIGNATUREDATE` timestamp(3) NULL DEFAULT NULL,
  `SIGNATUREPLACE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `SIGNATURESCANBASE64` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  `TYPE` int NOT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `CONSENT_VIRTUAL_PERSON_ID` bigint NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`TYPE`,`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`TYPE`,`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  KEY `I_FK_signature_CONSENT_DATE` (`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`),
  CONSTRAINT `FK_signature_CONSENT_DATE` FOREIGN KEY (`CONSENT_DATE`, `CONSENT_VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `signed_policy`
--

DROP TABLE IF EXISTS `signed_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signed_policy` (
  `STATUS` int DEFAULT NULL,
  `CONSENT_DATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `CONSENT_VIRTUAL_PERSON_ID` bigint NOT NULL,
  `CT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CT_VERSION` int NOT NULL,
  `POLICY_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `POLICY_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `POLICY_VERSION` int NOT NULL,
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`,`POLICY_DOMAIN_NAME`,`POLICY_NAME`,`POLICY_VERSION`),
  UNIQUE KEY `I_PRIMARY` (`CONSENT_DATE`,`CONSENT_VIRTUAL_PERSON_ID`,`CT_DOMAIN_NAME`,`CT_NAME`,`CT_VERSION`,`POLICY_DOMAIN_NAME`,`POLICY_NAME`,`POLICY_VERSION`),
  KEY `I_FK_signed_policy_POLICY_NAME` (`POLICY_NAME`,`POLICY_VERSION`,`POLICY_DOMAIN_NAME`),
  CONSTRAINT `FK_signed_policy_CONSENT_DATE` FOREIGN KEY (`CONSENT_DATE`, `CONSENT_VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`) REFERENCES `consent` (`CONSENT_DATE`, `VIRTUAL_PERSON_ID`, `CT_DOMAIN_NAME`, `CT_NAME`, `CT_VERSION`),
  CONSTRAINT `FK_signed_policy_POLICY_NAME` FOREIGN KEY (`POLICY_NAME`, `POLICY_VERSION`, `POLICY_DOMAIN_NAME`) REFERENCES `policy` (`NAME`, `VERSION`, `DOMAIN_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `signer_id`
--

DROP TABLE IF EXISTS `signer_id`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signer_id` (
  `VALUE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SIT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SIT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`VALUE`,`SIT_DOMAIN_NAME`,`SIT_NAME`),
  UNIQUE KEY `I_PRIMARY` (`VALUE`,`SIT_DOMAIN_NAME`,`SIT_NAME`),
  KEY `I_FK_signer_id_SIT_NAME` (`SIT_NAME`,`SIT_DOMAIN_NAME`),
  CONSTRAINT `FK_signer_id_SIT_NAME` FOREIGN KEY (`SIT_NAME`, `SIT_DOMAIN_NAME`) REFERENCES `signer_id_type` (`NAME`, `DOMAIN_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `signer_id_type`
--

DROP TABLE IF EXISTS `signer_id_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signer_id_type` (
  `NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `UPDATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `LABEL` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `COMMENT` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin DEFAULT NULL,
  `ORDER_NUMBER` int NOT NULL DEFAULT '0',
  `FHIR_ID` varchar(41) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`NAME`,`DOMAIN_NAME`),
  UNIQUE KEY `I_PRIMARY` (`NAME`,`DOMAIN_NAME`),
  KEY `I_FK_signer_id_type_DOMAIN_NAME` (`DOMAIN_NAME`),
  CONSTRAINT `FK_signer_id_type_DOMAIN_NAME` FOREIGN KEY (`DOMAIN_NAME`) REFERENCES `domain` (`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `signer_id_type`
--

LOCK TABLES `signer_id_type` WRITE;
/*!40000 ALTER TABLE `signer_id_type` DISABLE KEYS */;
INSERT INTO `signer_id_type` VALUES ('Pseudonym','MII','2023-06-29 08:03:30.154','2023-06-29 08:03:30.154',NULL,NULL,1,'4068957a-b7ac-4aaa-a839-950a995a0c94');
/*!40000 ALTER TABLE `signer_id_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stat_entry`
--

DROP TABLE IF EXISTS `stat_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stat_entry` (
  `STAT_ENTRY_ID` bigint NOT NULL AUTO_INCREMENT,
  `ENTRYDATE` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`STAT_ENTRY_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stat_entry`
--

LOCK TABLES `stat_entry` WRITE;
/*!40000 ALTER TABLE `stat_entry` DISABLE KEYS */;
/*!40000 ALTER TABLE `stat_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stat_value`
--

DROP TABLE IF EXISTS `stat_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stat_value` (
  `stat_value_id` bigint DEFAULT NULL,
  `stat_value` bigint DEFAULT NULL,
  `stat_attr` varchar(255) DEFAULT NULL,
  KEY `FK_stat_value_stat_value_id` (`stat_value_id`),
  CONSTRAINT `FK_stat_value_stat_value_id` FOREIGN KEY (`stat_value_id`) REFERENCES `stat_entry` (`STAT_ENTRY_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stat_value`
--

LOCK TABLES `stat_value` WRITE;
/*!40000 ALTER TABLE `stat_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `stat_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `text`
--

DROP TABLE IF EXISTS `text`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `text` (
  `ID` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `TEXT` longtext CHARACTER SET utf8mb3 COLLATE utf8mb3_bin,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `I_PRIMARY` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text`
--

LOCK TABLES `text` WRITE;
/*!40000 ALTER TABLE `text` DISABLE KEYS */;
INSERT INTO `text` VALUES ('MII_###_Geltungsdauer_###_1006_###_MODUL','<div style=\"text-align: justify;\">Meine Einwilligung in die Erhebung von Patientendaten bei Aufenthalten in der Universitätsmedizin Essen gilt für einen <strong>Zeitraum von fünf Jahren</strong> ab meiner Einwilligungserklärung. Sollte ich nach Ablauf von fünf Jahren wieder in der Universitätsmedizin Essen vorstellig werden, kann ich erneut meine Einwilligung erteilen. Die Nutzung der von mir erhobenen Daten bleibt über diesen Zeitraum hinaus zulässig (Punkt 5 der Patienteninformation). </div>'),('MII_###_Geltungsdauer_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">5. Geltungsdauer meiner Einwilligung</h3>'),('MII_###_KKDAT_Intro_###_1006_###_MODUL','<div style=\"text-align: justify;\">Hiermit ermächtige ich meine Krankenkasse auf Anforderung durch die Universitätsmedizin Essen, Daten über von mir in Anspruch genommene ärztliche Leistungen in der ambulanten Versorgung und bei stationären Aufenthalten, über verordnete Heil- und Hilfsmittel sowie Arzneimittel und Angaben zum Bereich Pflege an die Universitätsmedizin Essen sowie in der Patienteninformation beschrieben, zu übermitteln, und zwar:</div><div><br></div>'),('MII_###_KKDAT_Intro_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">2. Übertragung und wissenschaftliche Nutzung meiner Krankenkassendaten</h3>'),('MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>2.2 </strong>Für Daten <strong>ab dem Datum meiner Unterschrift über einen Zeitraum von 5 Jahren</strong>. Mit der dafür nötigen Übermittlung meiner Krankenversicherungsnummer. an die Universitätsmedizin Essen bin ich einverstanden.</div>'),('MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>2.1 </strong>Einmalig <strong>rückwirkend für die Daten der vergangenen 5 Kalenderjahre</strong>. Mit der dafür nötigen Übermittlung meiner Krankenversicherungsnummer an die Universitätsmedizin Essen bin ich einverstanden.</div>'),('MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_PATDAT_Intro_###_1006_###_MODUL','<div style=\"text-align: justify;\"><strong>1.1 </strong>die Verarbeitung und Nutzung meiner Patientendaten für die medizinische Forschung ausschließlich wie in der Patienteninformation beschrieben und mit getrennter Verwaltung des Namens und anderer direkt identifizierender Daten (Codierung). Unter der Adresse <strong>www.medizininformatik-initiative.de/datennutzung</strong> kann ich mich für einen E-Mail-Verteiler registrieren, der per E-Mail über alle neuen Studien, die mit den Patientendaten durchgeführt werden, vor deren Durchführung informiert (siehe Punkte 1.1, 1.2 und 1.3 der Patienteninformation).</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>1.2 </strong>die wissenschaftliche Analyse und Nutzung meiner codierten Patientendaten durch Dritte wie z.B. durch andere Universitäten/Institute/forschende Unternehmen; dies kann auch eine Weitergabe für Forschungsprojekte im Ausland umfassen, wenn in diesen europäisches Datenschutzrecht gilt oder die Europäische Kommission ein angemessenes Datenschutzniveau bestätigt hat. An einem etwaigen kommerziellen Nutzen aus der Forschung werde ich nicht beteiligt. Vor einer Weitergabe an Forscher außerhalb meiner behandelnden Einrichtung erfolgt zudem eine weitere Ersetzung des internen Kennzeichens durch eine neue Zeichenkombination.</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>1.3 </strong>die Möglichkeit einer Zusammenführung meiner Patientendaten mit Daten in Datenbanken anderer Forschungspartner. <strong>Voraussetzung ist, dass ich dieser Nutzung bei den entsprechenden Forschungspartnern auch zugestimmt habe.</strong></div>'),('MII_###_PATDAT_Intro_###_1006_###_MODULE_TITLE','<div><br></div><h3 style=\"text-align: justify;\">1. Erhebung, Verarbeitung und wissenschaftliche Nutzung meiner Patientendaten, wie in der Patienteninformation beschrieben; dies umfasst</h3>'),('MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\">Ich willige ein in die Erhebung, Verarbeitung, Speicherung und wissenschaftliche Nutzung meiner <strong>Patientendaten </strong>wie in Punkt 1.1 bis 1.3 der Einwilligungserklärung und Punkt 1 der Patienteninformation beschrieben.</div>'),('MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_Patienteneinwilligung MII_###_1006003_###_FOOTER',''),('MII_###_Patienteneinwilligung MII_###_1006003_###_HEADER','<h2 style=\"text-align: justify;\">Einwilligung in die Nutzung von Patientendaten und Krankenkassendaten für medizinische Forschungszwecke</h2>'),('MII_###_Patienteneinwilligung MII_###_1006003_###_TEMPLATE_TITLE','<h1>Patienteninformation und - einwilligung</h1>'),('MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODUL','<div style=\"text-align: justify;\"><strong>3.1. </strong>Ich willige ein, dass ich von der Universitätsmedizin Essen erneut kontaktiert werden darf, um gegebenenfalls zusätzliche für wissenschaftliche Fragen relevante Informationen zur Verfügung zu stellen, um über neue Forschungsvorhaben/Studien informiert zu werden, und/oder um meine Einwilligung in die Verknüpfung meiner Patientendaten mit medizinischen Informationen aus anderen Datenbanken einzuholen (siehe Punkt 3.1 der Patienteninformation).</div>'),('MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODULE_TITLE',NULL),('MII_###_Rekontaktierung_Intro_###_1006_###_MODUL',NULL),('MII_###_Rekontaktierung_Intro_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">3. Möglichkeit einer erneuten Kontaktaufnahme</h3>'),('MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>3.2 </strong>Ich willige ein, dass ich von die Universitätsmedizin Essen wieder kontaktiert werden darf, um über medizinische Zusatzbefunde informiert zu werden (siehe Punkt 3.2 der Patienteninformation).</div>'),('MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODULE_TITLE',NULL),('MII_###_Widerrufsrecht_###_1006_###_MODUL','<div style=\"text-align: justify;\">Meine Einwilligung ist <strong>freiwillig</strong>!</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\">Ich kann meine Einwilligung jederzeit ohne Angabe von Gründen bei der Universitätsmedizin Essen</div><div style=\"text-align: justify;\">vollständig oder in Teilen widerrufen, ohne dass mir irgendwelche Nachteile entstehen.&nbsp;</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\">Beim Widerruf werden die auf Grundlage dieser Einwilligung gespeicherten Daten gelöscht oder anonymisiert, sofern dies gesetzlich zulässig ist. Daten aus bereits durchgeführten Analysen können nicht mehr entfernt werden (Punkt 6 der Patienteninformation).</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>Ich wurde über die Nutzung meiner Patientendaten und Krankenkassendaten sowie die damit verbundenen Risiken informiert und erteile im vorgenannten Rahmen meine Einwilligung. Ich hatte ausreichend Bedenkzeit und alle meine Fragen wurden zufriedenstellend beantwortet.</strong></div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>Ich wurde darüber informiert, dass ich ein Exemplar der Patienteninformation und eine Kopie der unterschriebenen Einwilligungserklärung erhalten werde.</strong></div>'),('MII_###_Widerrufsrecht_###_1006_###_MODULE_TITLE','<div style=\"text-align: justify;\"><br></div><h3 style=\"text-align: justify;\">6. Widerrufsrecht</h3>');
/*!40000 ALTER TABLE `text` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `virtual_person`
--

DROP TABLE IF EXISTS `virtual_person`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `virtual_person` (
  `ID` bigint NOT NULL,
  `CREATE_TIMESTAMP` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`ID`),
  UNIQUE KEY `I_PRIMARY` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `virtual_person_signer_id`
--

DROP TABLE IF EXISTS `virtual_person_signer_id`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `virtual_person_signer_id` (
  `SIT_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SIT_DOMAIN_NAME` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `SI_VALUE` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  `VP_ID` bigint NOT NULL,
  PRIMARY KEY (`SIT_NAME`,`SIT_DOMAIN_NAME`,`SI_VALUE`,`VP_ID`),
  UNIQUE KEY `I_PRIMARY` (`SIT_NAME`,`SIT_DOMAIN_NAME`,`SI_VALUE`,`VP_ID`),
  KEY `I_FK_VIRTUAL_PERSON_SIGNER_ID_SI_VALUE` (`SI_VALUE`,`SIT_DOMAIN_NAME`,`SIT_NAME`),
  KEY `I_FK_VIRTUAL_PERSON_SIGNER_ID_VP_ID` (`VP_ID`),
  CONSTRAINT `FK_VIRTUAL_PERSON_SIGNER_ID_SI_VALUE` FOREIGN KEY (`SI_VALUE`, `SIT_DOMAIN_NAME`, `SIT_NAME`) REFERENCES `signer_id` (`VALUE`, `SIT_DOMAIN_NAME`, `SIT_NAME`),
  CONSTRAINT `FK_VIRTUAL_PERSON_SIGNER_ID_VP_ID` FOREIGN KEY (`VP_ID`) REFERENCES `virtual_person` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
