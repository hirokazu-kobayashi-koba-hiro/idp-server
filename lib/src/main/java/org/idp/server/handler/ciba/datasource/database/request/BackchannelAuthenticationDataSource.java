package org.idp.server.handler.ciba.datasource.database.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;

public class BackchannelAuthenticationDataSource
    implements BackchannelAuthenticationRequestRepository {

  SqlConnection sqlConnection;

  public BackchannelAuthenticationDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
  }

  @Override
  public void register(BackchannelAuthenticationRequest request) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sql = InsertSqlCreator.createInsert(request);
    sqlExecutor.execute(sql);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
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
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
            DELETE FROM backchannel_authentication_request WHERE id = '%s';
            """;
    String sql = String.format(sqlTemplate, identifier.value());
    sqlExecutor.execute(sql);
  }
}
