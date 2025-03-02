package org.idp.server.handler.oauth.datasource.database.request;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.basic.sql.TransactionManager;
import org.idp.server.oauth.exception.OAuthException;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  @Override
  public void register(AuthorizationRequest authorizationRequest) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                INSERT INTO public.authorization_request
                (id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details, presentation_definition, presentation_definition_uri, custom_params)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

    List<Object> params = InsertSqlCreator.createInsert(authorizationRequest);
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details, presentation_definition, presentation_definition_uri, custom_params FROM authorization_request
            WHERE id = ?;
            """;
    List<Object> params = List.of(authorizationRequestIdentifier.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new OAuthException(
          "invalid_request",
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public AuthorizationRequest find(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                SELECT id, token_issuer, profile, scopes, response_type, client_id, redirect_uri, state, response_mode, nonce, display, prompts, max_age, ui_locales, id_token_hint, login_hint, acr_values, claims_value, request_object, request_uri, code_challenge, code_challenge_method, authorization_details, presentation_definition, presentation_definition_uri, custom_params FROM authorization_request
                WHERE id = ?;
                """;
    List<Object> params = List.of(authorizationRequestIdentifier.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }

    return ModelConverter.convert(stringMap);
  }
}
