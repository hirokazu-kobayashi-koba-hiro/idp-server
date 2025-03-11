package org.idp.server.core.basic.sql;

import java.util.logging.Logger;

public interface SqlBaseBuilder {

  default String replace(String sql, int index, String value) {
    return sql.replace(String.format("'$%d'", index), String.format("'%s'", escapeValue(value)));
  }

  private String escapeValue(String value) {
    return value.replaceAll("'", "''");
  }

  Logger log = Logger.getLogger(SqlBaseBuilder.class.getName());

  default String build(String sql, int columnSize) {
    for (int i = 1; i <= columnSize; i++) {
      sql = sql.replace("'$" + i + "'", "''");
    }
    log.info(sql);
    return sql;
  }
}
