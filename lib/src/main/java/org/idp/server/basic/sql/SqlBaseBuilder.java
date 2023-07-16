package org.idp.server.basic.sql;

public interface SqlBaseBuilder {

  default String replace(String sql, int index, String value) {
    return sql.replace(String.format("'$%d'", index), String.format("'%s'", escapeValue(value)));
  }

  private String escapeValue(String value) {
    return value.replaceAll("'", "''");
  }
}
