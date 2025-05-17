package org.idp.server.core.adapters.datasource.oidc.code;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oauth.AuthorizationCode;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;

public class MysqlExecutor implements AuthorizationCodeGrantExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  MysqlExecutor() {}

  public void insert(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO authorization_code_grant
                    (
                    authorization_request_id,
                    tenant_id,
                    authorization_code,
                    user_id,
                    user_payload,
                    authentication,
                    client_id,
                    client_payload,
                    grant_type,
                    scopes,
                    id_token_claims,
                    userinfo_claims,
                    custom_properties,
                    authorization_details,
                    expired_at,
                    consent_claims
                    )
                    VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                    );
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
    params.add(authorizationCodeGrant.authorizationGrant().grantType().name());
    params.add(authorizationCodeGrant.scopes().toStringValues());

    if (authorizationCodeGrant.authorizationGrant().hasIdTokenClaims()) {
      params.add(authorizationCodeGrant.authorizationGrant().idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationCodeGrant.authorizationGrant().hasUserinfoClaim()) {
      params.add(authorizationCodeGrant.authorizationGrant().userinfoClaims().toStringValues());
    } else {
      params.add("");
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

    if (authorizationCodeGrant.authorizationGrant().hasConsentClaims()) {
      params.add(toJson(authorizationCodeGrant.authorizationGrant().consentClaims().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  public Map<String, String> selectOne(Tenant tenant, AuthorizationCode authorizationCode) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT
                authorization_request_id,
                tenant_id,
                authorization_code,
                user_id,
                user_payload,
                authentication,
                client_id,
                client_payload,
                grant_type,
                scopes,
                id_token_claims,
                userinfo_claims,
                custom_properties,
                authorization_details,
                expired_at,
                consent_claims
                FROM authorization_code_grant
                WHERE authorization_code = ?
                AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(authorizationCode.value());
    params.add(tenant.identifier().value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE FROM authorization_code_grant
                WHERE authorization_request_id = ?
                AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(authorizationCodeGrant.authorizationRequestIdentifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
