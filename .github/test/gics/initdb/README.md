# gICS Database Dumps

* 01_ddl.sql and 02_data.sql are copied from official gICS
  deployment https://www.ths-greifswald.de/gics/communitydownload
  * consent instances have been removed
* 03_consent.sql.gz is dumped from a running gICS instance with 100 consents uploaded  
  using the mysqldump:

  ```
  mysqldump --no-create-info --skip-add-locks --skip-create-options --skip-lock-tables \
      --skip-add-drop-table --skip-disable-keys --no-create-db --databases gics | gzip
  ```

* 04_migrations.sql contains migration scripts from the official deployment (starting with 2024.2.x)
