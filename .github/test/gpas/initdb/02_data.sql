-- MySQL dump 10.13  Distrib 9.0.1, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: gpas
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
-- Current Database: `gpas`
--

USE `gpas`;

--
-- Dumping data for table `domain`
--

INSERT INTO `domain` VALUES ('MII','MII','org.emau.icmvc.ganimed.ttp.psn.alphabets.Numbers','','org.emau.icmvc.ganimed.ttp.psn.generator.Verhoeff','FORCE_CACHE=DEFAULT;INCLUDE_PREFIX_IN_CHECK_DIGIT_CALCULATION=false;INCLUDE_SUFFIX_IN_CHECK_DIGIT_CALCULATION=false;MAX_DETECTED_ERRORS=2;PSN_LENGTH=8;PSN_PREFIX=;PSN_SUFFIX=;PSNS_DELETABLE=false;USE_LAST_CHAR_AS_DELIMITER_AFTER_X_CHARS=0;VALIDATE_VALUES_VIA_PARENTS=OFF;','2024-06-28 09:22:57.871','2024-06-28 09:22:57.871');

--
-- Dumping data for table `domain_parents`
--


--
-- Dumping data for table `psn`
--


--
-- Dumping data for table `sequence`
--


--
-- Dumping data for table `stat_entry`
--

INSERT INTO `stat_entry` VALUES (53,'2024-06-28 09:22:26.885'),(54,'2024-06-28 09:22:29.623'),(55,'2024-06-28 09:22:30.567'),(56,'2024-06-28 09:22:31.455');

--
-- Dumping data for table `stat_value`
--

INSERT INTO `stat_value` VALUES (53,0,'anonyms'),(53,0,'calculation_time'),(53,1,'domains'),(53,4528,'pseudonyms_per_domain.MII'),(53,4528,'pseudonyms'),(53,0,'anonyms_per_domain.MII'),(54,0,'anonyms'),(54,0,'calculation_time'),(54,1,'domains'),(54,4528,'pseudonyms_per_domain.MII'),(54,4528,'pseudonyms'),(54,0,'anonyms_per_domain.MII'),(55,0,'anonyms'),(55,0,'calculation_time'),(55,1,'domains'),(55,4528,'pseudonyms_per_domain.MII'),(55,4528,'pseudonyms'),(55,0,'anonyms_per_domain.MII'),(56,0,'anonyms'),(56,0,'calculation_time'),(56,1,'domains'),(56,4528,'pseudonyms_per_domain.MII'),(56,4528,'pseudonyms'),(56,0,'anonyms_per_domain.MII');
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
