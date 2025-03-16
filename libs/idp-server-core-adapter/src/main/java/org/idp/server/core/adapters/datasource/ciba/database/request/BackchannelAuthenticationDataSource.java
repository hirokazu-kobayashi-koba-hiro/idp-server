package org.idp.server.core.adapters.datasource.ciba.database.request;

import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;

import java.util.Map;
import java.util.Objects;

public class BackchannelAuthenticationDataSource
    implements BackchannelAuthenticationRequestRepository {

  @Override
  public void register(BackchannelAuthenticationRequest request) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sql = InsertSqlCreator.createInsert(request);
    sqlExecutor.execute(sql);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                        SELECT id, token_issuer, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details
                        FROM backchannel_authentication_request
                        WHERE id = '%s';
                        """;
    String sql = String.format(sqlTemplate, identifier.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new BackchannelAuthenticationRequestBuilder().build();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            DELETE FROM backchannel_authentication_request WHERE id = '%s';
            """;
    String sql = String.format(sqlTemplate, identifier.value());
    sqlExecutor.execute(sql);
  }
}
