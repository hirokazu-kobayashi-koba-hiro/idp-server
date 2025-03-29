package org.idp.server.core.adapters.datasource.oauth.database.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    INSERT INTO public.authorization_code_grant
                    (authorization_request_id, tenant_id, authorization_code, user_id, user_payload, authentication, client_id, client_payload, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb);;
                    """;
    List<Object> params = new ArrayList<>();

    params.add(authorizationCodeGrant.authorizationRequestIdentifier().value());
    params.add(authorizationCodeGrant.tenantIdentifier().value());
    params.add(authorizationCodeGrant.authorizationCode().value());
    params.add(authorizationCodeGrant.user().sub());
    params.add(toJson(authorizationCodeGrant.user()));
    params.add(toJson(authorizationCodeGrant.authentication()));
    params.add(authorizationCodeGrant.clientId().value());
    params.add(toJson(authorizationCodeGrant.client()));
    params.add(authorizationCodeGrant.scopes().toStringValues());

    if (authorizationCodeGrant.authorizationGrant().hasClaim()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().claimsPayload()));
    } else {
      params.add("{}");
    }

    if (authorizationCodeGrant.authorizationGrant().hasCustomProperties()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationCodeGrant.authorizationGrant().hasAuthorizationDetails()) {
      params.add(
          toJson(authorizationCodeGrant.authorizationGrant().authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    params.add(authorizationCodeGrant.expiredAt().toStringValue());

    if (authorizationCodeGrant.authorizationGrant().hasPresentationDefinition()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().presentationDefinition()));
    } else {
      params.add("{}");
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public AuthorizationCodeGrant find(AuthorizationCode authorizationCode) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                SELECT authorization_request_id, tenant_id, authorization_code, user_id, user_payload, authentication, client_id, client_payload, scopes, claims, custom_properties, authorization_details, expired_at, presentation_definition
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

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
