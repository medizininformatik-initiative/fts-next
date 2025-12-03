# gPAS Database Dumps

* 01_ddl.sql is copied from official gPAS
  deployment https://www.ths-greifswald.de/gpas/communitydownload
* 02_data.sql is generated using mysqldump:

  ```
  mysqldump --no-create-info --skip-add-locks --skip-create-options --skip-lock-tables \
      --skip-add-drop-table --skip-disable-keys --no-create-db --databases gpas
  ```

* 03_migrations.sql contains migration scripts from the official deployment (starting with 2024.3.x)
