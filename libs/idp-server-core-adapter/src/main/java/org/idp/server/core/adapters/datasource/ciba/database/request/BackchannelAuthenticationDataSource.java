package org.idp.server.core.adapters.datasource.ciba.database.request;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BackchannelAuthenticationDataSource
    implements BackchannelAuthenticationRequestRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(BackchannelAuthenticationRequest request) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate = """
                INSERT INTO public.backchannel_authentication_request
                (id, token_issuer, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb);
                """;

    List<Object> params = new ArrayList<>();
    params.add(request.identifier().value());
    params.add(request.tokenIssuer().value());
    params.add(request.profile().name());
    params.add(request.deliveryMode().name());
    params.add(request.scopes().toStringValues());
    params.add(request.clientId().value());

    if (request.hasIdTokenHint()) {
      params.add(request.idTokenHint().value());
    } else {
      params.add(null);
    }
    if (request.hasLoginHint()) {
      params.add(request.loginHint().value());
    } else {
      params.add(null);
    }
    if (request.hasLoginHintToken()) {
      params.add(request.loginHintToken().value());
    } else {
      params.add(null);
    }
    if (request.hasAcrValues()) {
      params.add(request.acrValues().toStringValues());
    } else {
      params.add(null);
    }
    if (request.hasUserCode()) {
      params.add(request.userCode().value());
    } else {
      params.add(null);
    }
    if (request.hasBindingMessage()) {
      params.add(request.bindingMessage().value());
    } else {
      params.add(null);
    }
    if (request.hasClientNotificationToken()) {
      params.add(
              request.clientNotificationToken().value());
    } else {
      params.add(null);
    }
    if (request.hasRequestedExpiry()) {
      params.add(request.requestedExpiry().value());
    } else {
      params.add(null);
    }
    if (request.hasRequest()) {
      params.add(request.requestObject().value());
    } else {
      params.add(null);
    }
    if (request.hasAuthorizationDetails()) {
      params.add(
              toJson(request.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      BackchannelAuthenticationRequestIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                        SELECT id, token_issuer, profile, delivery_mode, scopes, client_id, id_token_hint, login_hint, login_hint_token, acr_values, user_code, client_notification_token, binding_message, requested_expiry, request_object, authorization_details
                        FROM backchannel_authentication_request
                        WHERE id = ?;
                        """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

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
            DELETE FROM backchannel_authentication_request WHERE id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
