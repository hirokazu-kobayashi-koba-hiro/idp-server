package org.idp.server.handler.oauth.datasource.database;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.handler.oauth.datasource.database.model.AuthorizationRequestConverter;
import org.idp.server.handler.oauth.datasource.database.model.AuthorizationRequestSqlCreator;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  SqlConnection sqlConnection;

  public AuthorizationRequestDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
  }

  @Override
  public void register(AuthorizationRequest authorizationRequest) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sql = AuthorizationRequestSqlCreator.createInsert(authorizationRequest);
    sqlExecutor.execute(sql);
  }

  @Override
  public AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
            SELECT id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details FROM authorization_request
            WHERE id = '%s';
            """;
    String sql = String.format(sqlTemplate, authorizationRequestIdentifier.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new RuntimeException(
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }
    return AuthorizationRequestConverter.convert(stringMap);
  }

  @Override
  public AuthorizationRequest find(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
                SELECT id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details FROM authorization_request
                WHERE id = '%s';
                """;
    String sql = String.format(sqlTemplate, authorizationRequestIdentifier.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }
    return AuthorizationRequestConverter.convert(stringMap);
  }
}
