## migrate

```shell
DB_TYPE=postgresql ./gradlew flywayClean flywayMigrate
```

```shell
DB_TYPE=postgresql DB_URL=jdbc:postgresql://localhost:54321/idpserver_reader ./gradlew flywayClean flywayMigrate
```

```shell
DB_TYPE=mysql ./gradlew flywayClean flywayMigrate --info
```


## Note

### 🛠 PostgreSQL → MySQL DDL Conversion Rules

This table summarizes key syntax and data type differences when converting DDL from PostgreSQL to MySQL (>= 5.7).

| PostgreSQL Syntax / Type             | MySQL Equivalent                         | Notes / Remarks                                                                 |
|-------------------------------------|------------------------------------------|----------------------------------------------------------------------------------|
| `CHAR(36)`                          | `CHAR(36)`                               | Commonly used for UUIDs (stored as strings)                                     |
| `VARCHAR(255)`                      | `VARCHAR(255)`                           | No change needed                                                                |
| `TEXT`                              | `TEXT`                                   | Direct equivalent for long text                                                 |
| `BOOLEAN`                           | `TINYINT(1)`                             | MySQL does not support native boolean types; `1 = TRUE`, `0 = FALSE`            |
| `TIMESTAMP DEFAULT now()`           | `DATETIME DEFAULT CURRENT_TIMESTAMP`     | Replace PostgreSQL's `now()` with MySQL's built-in timestamp default            |
| `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `DATETIME DEFAULT CURRENT_TIMESTAMP`   | Same as above                                                                   |
| `TIMESTAMP`                         | `DATETIME`                               | Use `DATETIME` for cross-databaseType compatibility                                 |
| `JSONB`                             | `JSON`                                   | MySQL 5.7+ supports native JSON type                                            |
| `INET`                              | `VARCHAR(45)`                            | IPv6-compatible IP address storage                                              |
| `SERIAL`                            | `INT AUTO_INCREMENT`                     | PostgreSQL's auto-increment shortcut                                            |
| `gen_random_uuid()`                 | `UUID()`                                 | Use MySQL's `UUID()` function if UUID generation is required at the DB level    |
| `UUID` type (extension)             | `CHAR(36)` + `UUID()`                    | PostgreSQL has `uuid` type, MySQL stores as string                              |
| `ON DELETE CASCADE`                 | `ON DELETE CASCADE`                      | Behavior is the same                                                            |
| `UNIQUE (...) WHERE ...`            | Not supported                            | Needs to be rewritten using triggers or application-level checks                |
| `CREATE VIEW`                       | `CREATE VIEW`                            | Syntax mostly compatible (but some expressions may differ)                      |
| `JSONB` Indexing (e.g. GIN)         | `JSON` + `Generated Columns + Index`     | MySQL has no GIN index; use generated columns for JSON fields                   |
