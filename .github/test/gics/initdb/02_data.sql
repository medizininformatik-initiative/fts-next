-- MySQL dump 10.13  Distrib 9.0.1, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: gics
-- ------------------------------------------------------
-- Server version	9.0.1

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

--
-- Current Database: `gics`
--

USE `gics`;

--
-- Dumping data for table `alias`
--


--
-- Dumping data for table `consent`
--


--
-- Dumping data for table `consent_scan`
--


--
-- Dumping data for table `consent_template`
--

INSERT INTO `consent_template` VALUES ('MII_###_Patienteneinwilligung MII_###_1006003_###_TEMPLATE_TITLE','Version 1.6d der MII Vorlage, Stand: 16.04.2020, allgemeine Version (PatDat,KKDat,BioMat,ZModul) mit Platzhaltern  und den OIDs der ArtDecor Version von Juli 2021 (questionOIDs) gemäß ArtDecor und Rückmeldung AG Consent','study=mii;optional=false;orderNr=1;fhirPolicyCodesystem=2.16.840.1.113883.3.1937.777.24.5.3;fhirAnswerCodeSystem=2.16.840.1.113883.3.1937.777.24.5.2;fhirAnswerValueSet=https://www.medizininformatik-initiative.de/fhir/ValueSet/MiiConsentAnswerValueSet;fhirAnswerCodeYes=2.16.840.1.113883.3.1937.777.24.5.2.1;fhirAnswerCodeNo=2.16.840.1.113883.3.1937.777.24.5.2.2;fhirAnswerCodeUnknown=2.16.840.1.113883.3.1937.777.24.5.2.3;fhirAnswerDisplayYes=gültig;fhirAnswerDisplayNo=ungültig;fhirAnswerDisplayUnknown=unbekannt','VALIDITY_PERIOD=P30Y;','Patienteneinwilligung MII',1006003,'MII','CONSENT','MII_###_Patienteneinwilligung MII_###_1006003_###_FOOTER','MII_###_Patienteneinwilligung MII_###_1006003_###_HEADER','MII_###_Patienteneinwilligung MII_###_1006003','2023-06-29 08:03:30.444','2023-06-29 08:03:38.099','Patienteninformation und -einwilligung','AG Consent',1,'5f7af761-e3e0-42a6-a7de-d800ee11b85c',NULL);

--
-- Dumping data for table `consent_template_scan`
--

INSERT INTO `consent_template_scan` VALUES ('MII_###_Patienteneinwilligung MII_###_1006003','','');

--
-- Dumping data for table `domain`
--

INSERT INTO `domain` VALUES ('MII','Medizininformatik Initiative','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorCharVersionConverter','SAFE_SIGNERID_TYPE = Pseudonym','','MII','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorVersionConverter','org.emau.icmvc.ganimed.ttp.cm2.version.MajorMinorVersionConverter','REVOKE_IS_PERMANENT=false;TAKE_HIGHEST_VERSION_INSTEAD_OF_NEWEST=false;SCANS_ARE_NOT_MANDATORY_FOR_ACCEPTED_CONSENTS=false;SCANS_SIZE_LIMIT=10485760;TAKE_MOST_SPECIFIC_PERIOD_OF_VALIDITY_INSTEAD_OF_SHORTEST=false;VALID_QC_TYPES=not_checked,checked_minor_faults,checked_no_faults,###_auto_generated_###;INVALID_QC_TYPES=checked_major_faults,invalidated;DEFAULT_QC_TYPE=not_checked',NULL,1,'2023-06-29 08:03:30.154','2023-06-29 08:03:30.219','8ceeb823-cbb8-4113-97df-f73c3d8a37f4');

--
-- Dumping data for table `free_text_def`
--


--
-- Dumping data for table `free_text_val`
--


--
-- Dumping data for table `mapped_consent_template`
--


--
-- Dumping data for table `module`
--

INSERT INTO `module` VALUES ('','','MII_###_Geltungsdauer_###_1006_###_MODULE_TITLE','Geltungsdauer',1006,'MII','MII_###_Geltungsdauer_###_1006_###_MODUL','','2023-06-29 08:03:30.328','2023-06-29 08:03:38.099','Geltungsdauer',1,'ae44d58-4b73b-49d1-bc9b-b1d951f500f6'),('','','MII_###_KKDAT_Intro_###_1006_###_MODULE_TITLE','KKDAT_Intro',1006,'MII','MII_###_KKDAT_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.347','2023-06-29 08:03:38.097','KKDAT_Intro',1,'b99516e-9ede6-4b8f-ad9e-399392cc5901'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1594','MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE','KKDAT_prospektiv_uebertragen_speichern_nutzen',1008,'MII','MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.353','2023-06-29 08:03:38.098','KKDAT_prospektiv_uebertragen_speichern_nutzen',1,'0401356-ee5a7-46cb-93ce-602f267b2a8d'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1593','MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1008,'MII','MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.370','2023-06-29 08:03:38.098','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1,'b424ece-975f0-4c17-9ffd-a05edab4f7d7'),('','','MII_###_PATDAT_Intro_###_1006_###_MODULE_TITLE','PATDAT_Intro',1006,'MII','MII_###_PATDAT_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.381','2023-06-29 08:03:38.096','PATDAT_Intro',1,'52bb88e-ce3d6-4c03-abc1-41977e6ef924'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1567','MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODULE_TITLE','PATDAT_erheben_speichern_nutzen',1008,'MII','MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODUL','','2023-06-29 08:03:30.387','2023-06-29 08:03:38.097','PATDAT_erheben_speichern_nutzen',1,'b24f571-dc68f-4401-9862-923f7ab35165'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1597','MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODULE_TITLE','Rekontaktierung_Ergaenzungen',1007,'MII','MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODUL','','2023-06-29 08:03:30.403','2023-06-29 08:03:38.099','Rekontaktierung_Ergaenzungen',1,'49d5779-8ccba-469e-90d1-3df7f4b9ded9'),('','','MII_###_Rekontaktierung_Intro_###_1006_###_MODULE_TITLE','Rekontaktierung_Intro',1006,'MII','MII_###_Rekontaktierung_Intro_###_1006_###_MODUL','','2023-06-29 08:03:30.413','2023-06-29 08:03:38.098','Rekontaktierung_Intro',1,'88353e1-0a453-4fb4-a883-5911b5d1bc54'),('','fhirQuestionCode=2.16.840.1.113883.3.1937.777.24.2.1598','MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODULE_TITLE','Rekontaktierung_Zusatzbefund',1008,'MII','MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODUL','','2023-06-29 08:03:30.420','2023-06-29 08:03:38.099','Rekontaktierung_Zusatzbefund',1,'e05820e-33a2b-4352-bc4c-3067a835a2d2'),('','','MII_###_Widerrufsrecht_###_1006_###_MODULE_TITLE','Widerrufsrecht',1006,'MII','MII_###_Widerrufsrecht_###_1006_###_MODUL','','2023-06-29 08:03:30.428','2023-06-29 08:03:38.099','Widerrufsrecht',1,'ed1fa1b-f372e-4902-9e9f-78b9a2ffc0d5');

--
-- Dumping data for table `module_consent_template`
--

INSERT INTO `module_consent_template` VALUES (NULL,NULL,0,NULL,_binary '\0',11,'MII','Patienteneinwilligung MII',1006003,'MII','Geltungsdauer',1006,NULL,NULL,NULL,'','2fed9e5d-e9af-481a-a00f-6e1f13aa25d2'),(NULL,NULL,0,NULL,_binary '\0',2,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_Intro',1006,NULL,NULL,NULL,'','3d396549-c717-4e01-8afa-672dfefe4d6e'),(NULL,NULL,3,NULL,_binary '\0',4,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_prospektiv_uebertragen_speichern_nutzen',1008,NULL,NULL,NULL,'','30420045-b334-42b2-bf7e-88946473c0fd'),(NULL,NULL,3,NULL,_binary '\0',3,'MII','Patienteneinwilligung MII',1006003,'MII','KKDAT_retrospektiv_uebertragen_speichern_nutzen',1008,NULL,NULL,NULL,'','7fd43530-877d-451b-8949-1efbd64820dc'),(NULL,NULL,0,NULL,_binary '\0',0,'MII','Patienteneinwilligung MII',1006003,'MII','PATDAT_Intro',1006,NULL,NULL,NULL,'','afd4e142-a202-46b1-b2da-af0af4bf0512'),(NULL,NULL,3,NULL,_binary '',1,'MII','Patienteneinwilligung MII',1006003,'MII','PATDAT_erheben_speichern_nutzen',1008,NULL,NULL,NULL,'','3f7c5c6e-48b5-42cc-bf3b-762f49f00079'),(NULL,NULL,3,NULL,_binary '\0',9,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Ergaenzungen',1007,NULL,NULL,NULL,'','3742c8ac-016f-4234-b226-b0364691b805'),(NULL,NULL,0,NULL,_binary '\0',8,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Intro',1006,NULL,NULL,NULL,'','f4089717-5317-4522-a978-a918f21765b2'),(NULL,NULL,3,NULL,_binary '\0',10,'MII','Patienteneinwilligung MII',1006003,'MII','Rekontaktierung_Zusatzbefund',1008,NULL,NULL,NULL,'','3f1dec8b-9afc-4ce3-b9a6-ecb23975ca20'),(NULL,NULL,0,NULL,_binary '\0',12,'MII','Patienteneinwilligung MII',1006003,'MII','Widerrufsrecht',1006,NULL,NULL,NULL,'','e87d39d3-0736-4976-be9c-39fcbd952dcd');

--
-- Dumping data for table `module_policy`
--

INSERT INTO `module_policy` VALUES ('IDAT_bereitstellen_EU_DSGVO_konform','MII',1001,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','79013dcc-569e-4dfd-b53c-e161bbc2a4ee'),('IDAT_erheben','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','fac2e451-a66d-4a62-9f33-aef79c99c9a6'),('IDAT_speichern_verarbeiten','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','84df98ba-3115-4c35-b945-43d4428c08b2'),('IDAT_zusammenfuehren_Dritte','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','462f4dcf-492f-47ed-9c3e-b9a13feb0d82'),('KKDAT_5J_pro_speichern_verarbeiten','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','10edaa59-9f10-4d7f-8d56-ea853d0f41a8'),('KKDAT_5J_pro_uebertragen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','934e30b2-6f72-4c9e-810e-765062d854fa'),('KKDAT_5J_pro_wissenschaftlich_nutzen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','6d560695-0ada-424d-9a9d-94fef5d69b2f'),('KKDAT_5J_retro_speichern_verarbeiten','MII',1001,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','7ec163ac-244c-4b4f-ad3b-95396c82d384'),('KKDAT_5J_retro_uebertragen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,'singleUseOnly=true','','8e5e163e-2845-48bf-888b-30091ac253a4'),('KKDAT_5J_retro_wissenschaftlich_nutzen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','acdb25cc-c257-4981-9657-82a5e1b3449c'),('KKDAT_KVNR_5J_pro_uebertragen','MII',1000,'KKDAT_prospektiv_uebertragen_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','a8e43166-4171-4a84-8c8f-30c5e777da43'),('KKDAT_KVNR_5J_retro_uebertragen','MII',1000,'KKDAT_retrospektiv_uebertragen_speichern_nutzen','MII',1008,NULL,NULL,'','306fae23-c6c4-41f6-b155-203a03559cb7'),('MDAT_erheben','MII',1001,'PATDAT_erheben_speichern_nutzen','MII',1008,'Automatischer Ablauf nach 5 Jahren',NULL,'VALIDITY_PERIOD=P5Y;','e24f12be-9adc-44f1-b3f8-357cac463bd8'),('MDAT_speichern_verarbeiten','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','a40e8901-c643-4558-bccb-4c937e4ec1a1'),('MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','1d59e1dd-3782-436f-8834-d98f07e94832'),('MDAT_zusammenfuehren_Dritte','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','d0d49784-3d9c-4967-9e79-86cbfab1a439'),('Rekontaktierung_Ergebnisse_erheblicher_Bedeutung','MII',1000,'PATDAT_erheben_speichern_nutzen','MII',1008,NULL,NULL,'','80c262ad-981f-4bb2-8ec6-8a212ed354a5'),('Rekontaktierung_Verknuepfung_Datenbanken','MII',1000,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','70d18001-467d-42ab-aefb-961c44c826e5'),('Rekontaktierung_Zusatzbefund','MII',1000,'Rekontaktierung_Zusatzbefund','MII',1008,NULL,NULL,'','7a0b2b08-b9b5-42b0-8bde-ee4446c52b99'),('Rekontaktierung_weitere_Erhebung','MII',1001,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','21f0c8e4-8446-41ce-8d69-35d823d5bda2'),('Rekontaktierung_weitere_Studien','MII',1001,'Rekontaktierung_Ergaenzungen','MII',1007,NULL,NULL,'','79a20c2f-cab5-4b9f-b214-7047a09ddeb5');

--
-- Dumping data for table `policy`
--

INSERT INTO `policy` VALUES ('Herausgabe identifizierender Daten (IDAT) an verantwortliche Stelle zur weiteren Verarbeitung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.5','IDAT_bereitstellen_EU_DSGVO_konform',1001,'MII','2023-06-29 08:03:30.230','2023-06-29 08:03:38.097','IDAT_bereitstellen_EU_DSGVO_konform',1,'15299f7b-b878-410c-8dc0-8c0dec3c485c'),('Erfassung neuer identifizierender Daten (IDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.2','IDAT_erheben',1000,'MII','2023-06-29 08:03:30.240','2023-06-29 08:03:38.097','IDAT_erheben',1,'977a635c-759b-45ec-bde7-3b0285d764ae'),('Speicherung und Verarbeitung identifizierender Daten (IDAT)  in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.3','IDAT_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.245','2023-06-29 08:03:38.096','IDAT_speichern_verarbeiten',1,'54fa6eca-72f5-433c-a512-9c65aea61ed4'),('Zusammenführung identifizierender Daten (IDAT) mit Dritten Forschungspartnern','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.4','IDAT_zusammenfuehren_Dritte',1000,'MII','2023-06-29 08:03:30.250','2023-06-29 08:03:38.097','IDAT_zusammenfuehren_Dritte',1,'df35530b-2654-4c26-8de6-50bdaccdfcf0'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv speichern und verarbeiten in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.16','KKDAT_5J_pro_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.254','2023-06-29 08:03:38.098','KKDAT_5J_pro_speichern_verarbeiten',1,'754f5ef7-0f3a-4ec5-a834-1017023a32ca'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv übertragen','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.15','KKDAT_5J_pro_uebertragen',1000,'MII','2023-06-29 08:03:30.258','2023-06-29 08:03:38.098','KKDAT_5J_pro_uebertragen',1,'ebf71aa9-886d-4b6d-ad02-6fa525a75135'),('Krankenkassendaten (KKDAT) für 5 Jahre prospektiv wissenschaftlich nutzen ','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.17','KKDAT_5J_pro_wissenschaftlich_nutzen',1000,'MII','2023-06-29 08:03:30.263','2023-06-29 08:03:38.098','KKDAT_5J_pro_wissenschaftlich_nutzen',1,'e1c2e4fe-9ce6-479f-ac61-c1e2a5adc0c6'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv speichern und verarbeiten in der verantwortlichen Stelle','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.12','KKDAT_5J_retro_speichern_verarbeiten',1001,'MII','2023-06-29 08:03:30.267','2023-06-29 08:03:38.098','KKDAT_5J_retro_speichern_verarbeiten',1,'e975dd55-c63b-430c-9518-e5d66cf7bfc1'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv übertragen','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.11','KKDAT_5J_retro_uebertragen',1000,'MII','2023-06-29 08:03:30.271','2023-06-29 08:03:38.098','KKDAT_5J_retro_uebertragen',1,'f89d3fac-bd9f-4ff7-a063-038937be51c8'),('Krankenkassendaten (KKDAT) für 5 Jahre retrospektiv wissenschaftlich nutzen ','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.13','KKDAT_5J_retro_wissenschaftlich_nutzen',1000,'MII','2023-06-29 08:03:30.275','2023-06-29 08:03:38.098','KKDAT_5J_retro_wissenschaftlich_nutzen',1,'57f95ff2-c244-4095-a96f-73542de68ac1'),('Erlaubnis zur prospektiven Übermittlung der KVNr. an zuständige Stelle\n','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.39','KKDAT_KVNR_5J_pro_uebertragen',1000,'MII','2023-06-29 08:03:30.280','2023-06-29 08:03:38.098','KKDAT_KVNR_5J_pro_uebertragen',1,'80341c97-752b-4ab2-abac-2b580bc0f3d1'),('Erlaubnis zur retrospektiven Übermittlung der KVNr. an zuständige Stelle\n','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.38','KKDAT_KVNR_5J_retro_uebertragen',1000,'MII','2023-06-29 08:03:30.284','2023-06-29 08:03:38.098','KKDAT_KVNR_5J_retro_uebertragen',1,'5fcf72fe-77ce-4cb8-8ad9-3710a7f2385d'),('Erfassung medizinischer Daten (MDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.6','MDAT_erheben',1001,'MII','2023-06-29 08:03:30.288','2023-06-29 08:03:38.097','MDAT_erheben',1,'d113ec2c-47a3-4dcd-90dd-4d41ddf03d4c'),('Speicherung und Verarbeitung von medizinischen Daten innerhalb der verantwortlichen Stelle (MDAT)','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.7','MDAT_speichern_verarbeiten',1000,'MII','2023-06-29 08:03:30.292','2023-06-29 08:03:38.097','MDAT_speichern_verarbeiten',1,'4a394fed-ccd1-4ca5-af86-e69406b822ed'),('Bereitstellung medizinischer Daten (MDAT) für wissenschaftliche Nutzung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.8','MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform',1000,'MII','2023-06-29 08:03:30.297','2023-06-29 08:03:38.096','MDAT_wissenschaftlich_nutzen_EU_DSGVO_konform',1,'41c51dc8-0753-4cd5-8b6f-6db3ce65466a'),('Zusammenführung medizinischer Daten (MDAT) mit Dritten Forschungspartnern','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.9','MDAT_zusammenfuehren_Dritte',1000,'MII','2023-06-29 08:03:30.301','2023-06-29 08:03:38.097','MDAT_zusammenfuehren_Dritte',1,'740cd82b-2047-4942-8c8b-9deaee58ffd6'),('Rekontaktierung des Betroffenen bei Ergebnissen von erheblicher Bedeutung','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.37','Rekontaktierung_Ergebnisse_erheblicher_Bedeutung',1000,'MII','2023-06-29 08:03:30.306','2023-06-29 08:03:38.097','Rekontaktierung_Ergebnisse_erheblicher_Bedeutung',1,'eb55a9ac-a518-4805-b2d0-7f7183470617'),('Rekontaktierung zur Verknüpfung von PatDat mit Info anderer Dbs','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.27','Rekontaktierung_Verknuepfung_Datenbanken',1000,'MII','2023-06-29 08:03:30.310','2023-06-29 08:03:38.099','Rekontaktierung_Verknuepfung_Datenbanken',1,'c4864b47-652a-456d-9876-3af7443d311f'),('Rekontaktierung bezüglich Zusatzbefund','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.31','Rekontaktierung_Zusatzbefund',1000,'MII','2023-06-29 08:03:30.315','2023-06-29 08:03:38.099','Rekontaktierung_Zusatzbefund',1,'94c5589e-c8aa-47cc-a2a8-33d884b9e94d'),('Rekontaktierung bezüglich Erhebung zusätzlicher Daten','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.28','Rekontaktierung_weitere_Erhebung',1001,'MII','2023-06-29 08:03:30.319','2023-06-29 08:03:38.099','Rekontaktierung_weitere_Erhebung',1,'31b8aeec-eb41-48af-b74d-a4e1a8338185'),('Rekontaktierung bezüglich Information zu neuen Forschungsvorhaben oder Studien','fhirPolicyCode=2.16.840.1.113883.3.1937.777.24.5.3.29','Rekontaktierung_weitere_Studien',1001,'MII','2023-06-29 08:03:30.323','2023-06-29 08:03:38.099','Rekontaktierung_weitere_Studien',1,'958af868-bdb7-42a2-9bd0-7313bacd676f');

--
-- Dumping data for table `qc`
--


--
-- Dumping data for table `qc_hist`
--


--
-- Dumping data for table `qc_problem`
--


--
-- Dumping data for table `qc_problem_hist`
--


--
-- Dumping data for table `sequence`
--

INSERT INTO `sequence` VALUES ('statistic_index',0),('virtual_person_index',50);

--
-- Dumping data for table `signature`
--


--
-- Dumping data for table `signed_policy`
--


--
-- Dumping data for table `signer_id`
--


--
-- Dumping data for table `signer_id_type`
--

INSERT INTO `signer_id_type` VALUES ('Pseudonym','MII','2023-06-29 08:03:30.154','2023-06-29 08:03:30.154',NULL,NULL,1,'4068957a-b7ac-4aaa-a839-950a995a0c94');

--
-- Dumping data for table `stat_entry`
--


--
-- Dumping data for table `stat_value`
--


--
-- Dumping data for table `text`
--

INSERT INTO `text` VALUES ('MII_###_Geltungsdauer_###_1006_###_MODUL','<div style=\"text-align: justify;\">Meine Einwilligung in die Erhebung von Patientendaten bei Aufenthalten in der Universitätsmedizin Essen gilt für einen <strong>Zeitraum von fünf Jahren</strong> ab meiner Einwilligungserklärung. Sollte ich nach Ablauf von fünf Jahren wieder in der Universitätsmedizin Essen vorstellig werden, kann ich erneut meine Einwilligung erteilen. Die Nutzung der von mir erhobenen Daten bleibt über diesen Zeitraum hinaus zulässig (Punkt 5 der Patienteninformation). </div>'),('MII_###_Geltungsdauer_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">5. Geltungsdauer meiner Einwilligung</h3>'),('MII_###_KKDAT_Intro_###_1006_###_MODUL','<div style=\"text-align: justify;\">Hiermit ermächtige ich meine Krankenkasse auf Anforderung durch die Universitätsmedizin Essen, Daten über von mir in Anspruch genommene ärztliche Leistungen in der ambulanten Versorgung und bei stationären Aufenthalten, über verordnete Heil- und Hilfsmittel sowie Arzneimittel und Angaben zum Bereich Pflege an die Universitätsmedizin Essen sowie in der Patienteninformation beschrieben, zu übermitteln, und zwar:</div><div><br></div>'),('MII_###_KKDAT_Intro_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">2. Übertragung und wissenschaftliche Nutzung meiner Krankenkassendaten</h3>'),('MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>2.2 </strong>Für Daten <strong>ab dem Datum meiner Unterschrift über einen Zeitraum von 5 Jahren</strong>. Mit der dafür nötigen Übermittlung meiner Krankenversicherungsnummer. an die Universitätsmedizin Essen bin ich einverstanden.</div>'),('MII_###_KKDAT_prospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>2.1 </strong>Einmalig <strong>rückwirkend für die Daten der vergangenen 5 Kalenderjahre</strong>. Mit der dafür nötigen Übermittlung meiner Krankenversicherungsnummer an die Universitätsmedizin Essen bin ich einverstanden.</div>'),('MII_###_KKDAT_retrospektiv_uebertragen_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_PATDAT_Intro_###_1006_###_MODUL','<div style=\"text-align: justify;\"><strong>1.1 </strong>die Verarbeitung und Nutzung meiner Patientendaten für die medizinische Forschung ausschließlich wie in der Patienteninformation beschrieben und mit getrennter Verwaltung des Namens und anderer direkt identifizierender Daten (Codierung). Unter der Adresse <strong>www.medizininformatik-initiative.de/datennutzung</strong> kann ich mich für einen E-Mail-Verteiler registrieren, der per E-Mail über alle neuen Studien, die mit den Patientendaten durchgeführt werden, vor deren Durchführung informiert (siehe Punkte 1.1, 1.2 und 1.3 der Patienteninformation).</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>1.2 </strong>die wissenschaftliche Analyse und Nutzung meiner codierten Patientendaten durch Dritte wie z.B. durch andere Universitäten/Institute/forschende Unternehmen; dies kann auch eine Weitergabe für Forschungsprojekte im Ausland umfassen, wenn in diesen europäisches Datenschutzrecht gilt oder die Europäische Kommission ein angemessenes Datenschutzniveau bestätigt hat. An einem etwaigen kommerziellen Nutzen aus der Forschung werde ich nicht beteiligt. Vor einer Weitergabe an Forscher außerhalb meiner behandelnden Einrichtung erfolgt zudem eine weitere Ersetzung des internen Kennzeichens durch eine neue Zeichenkombination.</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>1.3 </strong>die Möglichkeit einer Zusammenführung meiner Patientendaten mit Daten in Datenbanken anderer Forschungspartner. <strong>Voraussetzung ist, dass ich dieser Nutzung bei den entsprechenden Forschungspartnern auch zugestimmt habe.</strong></div>'),('MII_###_PATDAT_Intro_###_1006_###_MODULE_TITLE','<div><br></div><h3 style=\"text-align: justify;\">1. Erhebung, Verarbeitung und wissenschaftliche Nutzung meiner Patientendaten, wie in der Patienteninformation beschrieben; dies umfasst</h3>'),('MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODUL','<div style=\"text-align: justify;\">Ich willige ein in die Erhebung, Verarbeitung, Speicherung und wissenschaftliche Nutzung meiner <strong>Patientendaten </strong>wie in Punkt 1.1 bis 1.3 der Einwilligungserklärung und Punkt 1 der Patienteninformation beschrieben.</div>'),('MII_###_PATDAT_erheben_speichern_nutzen_###_1008_###_MODULE_TITLE',NULL),('MII_###_Patienteneinwilligung MII_###_1006003_###_FOOTER',''),('MII_###_Patienteneinwilligung MII_###_1006003_###_HEADER','<h2 style=\"text-align: justify;\">Einwilligung in die Nutzung von Patientendaten und Krankenkassendaten für medizinische Forschungszwecke</h2>'),('MII_###_Patienteneinwilligung MII_###_1006003_###_TEMPLATE_TITLE','<h1>Patienteninformation und - einwilligung</h1>'),('MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODUL','<div style=\"text-align: justify;\"><strong>3.1. </strong>Ich willige ein, dass ich von der Universitätsmedizin Essen erneut kontaktiert werden darf, um gegebenenfalls zusätzliche für wissenschaftliche Fragen relevante Informationen zur Verfügung zu stellen, um über neue Forschungsvorhaben/Studien informiert zu werden, und/oder um meine Einwilligung in die Verknüpfung meiner Patientendaten mit medizinischen Informationen aus anderen Datenbanken einzuholen (siehe Punkt 3.1 der Patienteninformation).</div>'),('MII_###_Rekontaktierung_Ergaenzungen_###_1007_###_MODULE_TITLE',NULL),('MII_###_Rekontaktierung_Intro_###_1006_###_MODUL',NULL),('MII_###_Rekontaktierung_Intro_###_1006_###_MODULE_TITLE','<h3 style=\"text-align: justify;\">3. Möglichkeit einer erneuten Kontaktaufnahme</h3>'),('MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODUL','<div style=\"text-align: justify;\"><strong>3.2 </strong>Ich willige ein, dass ich von die Universitätsmedizin Essen wieder kontaktiert werden darf, um über medizinische Zusatzbefunde informiert zu werden (siehe Punkt 3.2 der Patienteninformation).</div>'),('MII_###_Rekontaktierung_Zusatzbefund_###_1008_###_MODULE_TITLE',NULL),('MII_###_Widerrufsrecht_###_1006_###_MODUL','<div style=\"text-align: justify;\">Meine Einwilligung ist <strong>freiwillig</strong>!</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\">Ich kann meine Einwilligung jederzeit ohne Angabe von Gründen bei der Universitätsmedizin Essen</div><div style=\"text-align: justify;\">vollständig oder in Teilen widerrufen, ohne dass mir irgendwelche Nachteile entstehen.&nbsp;</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\">Beim Widerruf werden die auf Grundlage dieser Einwilligung gespeicherten Daten gelöscht oder anonymisiert, sofern dies gesetzlich zulässig ist. Daten aus bereits durchgeführten Analysen können nicht mehr entfernt werden (Punkt 6 der Patienteninformation).</div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>Ich wurde über die Nutzung meiner Patientendaten und Krankenkassendaten sowie die damit verbundenen Risiken informiert und erteile im vorgenannten Rahmen meine Einwilligung. Ich hatte ausreichend Bedenkzeit und alle meine Fragen wurden zufriedenstellend beantwortet.</strong></div><div style=\"text-align: justify;\"><br></div><div style=\"text-align: justify;\"><strong>Ich wurde darüber informiert, dass ich ein Exemplar der Patienteninformation und eine Kopie der unterschriebenen Einwilligungserklärung erhalten werde.</strong></div>'),('MII_###_Widerrufsrecht_###_1006_###_MODULE_TITLE','<div style=\"text-align: justify;\"><br></div><h3 style=\"text-align: justify;\">6. Widerrufsrecht</h3>');

--
-- Dumping data for table `virtual_person`
--


--
-- Dumping data for table `virtual_person_signer_id`
--

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
