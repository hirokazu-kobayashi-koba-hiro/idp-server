package org.idp.server.core.adapters.datasource.oauth.database.code;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  @Override
  public void register(AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    INSERT INTO public.authorization_code_grant
                    (authorization_request_id, authorization_code, user_id, user_payload, authentication, client_id, client_payload, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition)
                    VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb);;
                    """;
    List<Object> params = InsertSqlParamsCreator.create(authorizationCodeGrant);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public AuthorizationCodeGrant find(AuthorizationCode authorizationCode) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                SELECT authorization_request_id, authorization_code, user_id, user_payload, authentication, client_id, client_payload, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition
                FROM authorization_code_grant
                WHERE authorization_code = ?;
                """;

    List<Object> params = List.of(authorizationCode.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

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
                WHERE authorization_request_id = ?;
            """;
    List<Object> params = List.of(authorizationCodeGrant.authorizationRequestIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
