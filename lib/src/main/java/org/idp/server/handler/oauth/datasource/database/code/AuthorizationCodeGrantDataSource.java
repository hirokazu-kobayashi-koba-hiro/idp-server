package org.idp.server.handler.oauth.datasource.database.code;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.basic.sql.TransactionManager;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  @Override
  public void register(AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sql = InsertSqlCreator.createInsert(authorizationCodeGrant);
    sqlExecutor.execute(sql);
  }

  @Override
  public AuthorizationCodeGrant find(AuthorizationCode authorizationCode) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                SELECT authorization_request_id, authorization_code, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition
                FROM authorization_code_grant
                WHERE authorization_code = '%s';
                """;
    String sql = String.format(sqlTemplate, authorizationCode.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationCodeGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                DELETE FROM authorization_code_grant
                WHERE authorization_request_id = '%s';
            """;
    String sql =
        String.format(sqlTemplate, authorizationCodeGrant.authorizationRequestIdentifier().value());
    sqlExecutor.execute(sql);
  }
}
