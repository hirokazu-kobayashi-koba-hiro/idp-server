package org.idp.server.core.adapters.datasource.grantmanagment;

import java.util.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class AuthorizationGrantedDataSource implements AuthorizationGrantedRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(AuthorizationGranted authorizationGranted) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                        INSERT INTO public.authorization_granted
                        (id, tenant_id, user_id, user_payload, authentication, client_id, client_payload, scopes, id_token_claims, userinfo_claims, custom_properties, authorization_details, consent_claims)
                        VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb);
                        """;
    List<Object> params = new ArrayList<>();

    AuthorizationGrant authorizationGrant = authorizationGranted.authorizationGrant();
    params.add(authorizationGranted.identifier().value());
    params.add(authorizationGrant.tenantIdentifier().value());
    params.add(authorizationGrant.user().sub());
    params.add(toJson(authorizationGrant.user()));
    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.requestedClientId().value());
    params.add(toJson(authorizationGrant.client()));
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      params.add(authorizationGrant.idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      params.add(authorizationGrant.userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    if (authorizationGrant.hasConsentClaims()) {
      params.add(toJson(authorizationGrant.consentClaims().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public AuthorizationGranted get(AuthorizationGrantedIdentifier identifier) {

    AuthorizationGranted authorizationGranted = new AuthorizationGranted();
    if (Objects.isNull(authorizationGranted)) {
      throw new RuntimeException(
          String.format("not found authorization granted (%s)", identifier.value()));
    }
    return authorizationGranted;
  }

  @Override
  public AuthorizationGranted find(AuthorizationGrantedIdentifier identifier) {

    return null;
  }

  @Override
  public AuthorizationGranted find(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
              SELECT id, tenant_id, user_id, user_payload, authentication, client_id, client_payload, scopes, id_token_claims, userinfo_claims, custom_properties, authorization_details, consent_claims
                FROM authorization_granted
              WHERE tenant_id = ?
              AND client_id = ?
              AND user_id = ?
              limit 1;
              """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());
    params.add(requestedClientId.value());
    params.add(user.sub());

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (result == null || result.isEmpty()) {
      return new AuthorizationGranted();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void update(AuthorizationGranted authorizationGranted) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        """
                UPDATE public.authorization_granted
                SET user_payload = ?::jsonb,
                authentication = ?::jsonb,
                client_payload = ?::jsonb,
                scopes = ?,
                id_token_claims = ?,
                userinfo_claims = ?,
                custom_properties = ?::jsonb,
                authorization_details = ?::jsonb,
                consent_claims = ?::jsonb,
                updated_at = now()
                WHERE id = ?;
                """;

    List<Object> params = new ArrayList<>();
    AuthorizationGrant authorizationGrant = authorizationGranted.authorizationGrant();
    params.add(toJson(authorizationGrant.user()));
    params.add(toJson(authorizationGrant.authentication()));
    params.add(toJson(authorizationGrant.client()));
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      params.add(authorizationGrant.idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      params.add(authorizationGrant.userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    if (authorizationGrant.hasConsentClaims()) {
      params.add(toJson(authorizationGrant.consentClaims().toMap()));
    } else {
      params.add(null);
    }

    params.add(authorizationGranted.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
