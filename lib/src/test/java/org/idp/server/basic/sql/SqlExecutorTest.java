package org.idp.server.basic.sql;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class SqlExecutorTest {
  SqlConnection sqlConnection =
      new SqlConnection("jdbc:postgresql://localhost:5432/idpserver", "idpserver", "idpserver");
  Logger log = Logger.getLogger(SqlExecutorTest.class.getName());

  @Test
  void execute() {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    for (int i = 0; i < 100; i++) {
      List<String> strings = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
      strings.parallelStream()
          .forEach(
              value -> {
                log.info(value);
                String sql =
                    String.format(
                        "INSERT INTO authorization_request(id, payload) VALUES ('%s', '%s')",
                        UUID.randomUUID().toString(), "{}");
                sqlExecutor.execute(sql);
              });
    }
    sqlConnection.rollback();
  }
}
