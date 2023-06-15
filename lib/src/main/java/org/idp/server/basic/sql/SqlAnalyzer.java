package org.idp.server.basic.sql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlAnalyzer {
  public static List<String> columnNames(String sql) {
    String substring = sql.substring("SELECT ".length());
    int lastIndex = substring.lastIndexOf("FROM");
    String selectValues = substring.substring(0, lastIndex);
    String[] splitSelectValues = selectValues.split(",");
    return new ArrayList<>(Arrays.stream(splitSelectValues).map(String::trim).toList());
  }
}
