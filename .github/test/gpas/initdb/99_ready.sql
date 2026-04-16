-- Sentinel for docker health checks: if this table exists, all init scripts completed.
CREATE TABLE _ready (ready TINYINT NOT NULL DEFAULT 1);
INSERT INTO _ready VALUES (1);
