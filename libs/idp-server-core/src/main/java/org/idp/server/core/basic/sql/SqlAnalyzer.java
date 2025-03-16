package org.idp.server.core.basic.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlAnalyzer {
  public static List<String> columnNames(String sql) {
    String substring = sql.substring("SELECT ".length());
    int lastIndex = substring.lastIndexOf("FROM");
    String selectValues = substring.substring(0, lastIndex);
    String[] splitSelectValues = selectValues.split(",");
    return new ArrayList<>(Arrays.stream(splitSelectValues).map(SqlAnalyzer::normalize).toList());
  }

  static String normalize(String columnName) {
    String normalizedColumnName = columnName.toLowerCase();
    if (columnName.contains(".")) {
      String[] splitted = columnName.split("\\.");
      String name = splitted[1];
      return name.trim();
    }

    return normalizedColumnName.trim();
  }
}
