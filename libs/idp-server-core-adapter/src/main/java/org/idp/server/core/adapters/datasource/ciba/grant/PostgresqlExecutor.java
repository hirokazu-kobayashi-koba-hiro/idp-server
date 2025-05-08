package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.grant.AuthorizationGrant;

public class PostgresqlExecutor implements CibaGrantSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO ciba_grant (
                    backchannel_authentication_request_id,
                    tenant_id,
                    auth_req_id,
                    expired_at,
                    polling_interval,
                    status,
                    user_id,
                    user_payload,
                    authentication,
                    client_id,
                    client_payload,
                    scopes,
                    id_token_claims,
                    userinfo_claims,
                    custom_properties,
                    authorization_details,
                    consent_claims
                    )
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::uuid,
                    ?::jsonb,
                    ?::jsonb,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
                    ?::jsonb,
                    ?::jsonb
                    );
                    """;
    List<Object> params = new ArrayList<>();
    AuthorizationGrant authorizationGrant = cibaGrant.authorizationGrant();
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());
    params.add(cibaGrant.tenantIdentifier().value());
    params.add(cibaGrant.authReqId().value());
    params.add(cibaGrant.expiredAt().toStringValue());
    params.add(cibaGrant.interval().toStringValue());
    params.add(cibaGrant.status().name());
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
      params.add(null);
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add(null);
    }

    if (authorizationGrant.hasConsentClaims()) {
      params.add(toJson(authorizationGrant.consentClaims().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE ciba_grant
                SET authentication = ?::jsonb,
                status = ?
                WHERE backchannel_authentication_request_id = ?::uuid;
                """;
    List<Object> params = new ArrayList<>();
    params.add(toJson(cibaGrant.authorizationGrant().authentication()));
    params.add(cibaGrant.status().name());
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(AuthReqId authReqId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql + " " + """
                WHERE auth_req_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(authReqId.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
            WHERE backchannel_authentication_request_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(backchannelAuthenticationRequestIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public void delete(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM ciba_grant
            WHERE backchannel_authentication_request_id = ?::uuid;
            """;
    List<Object> params = new ArrayList<>();
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }

  String selectSql =
      """
                  SELECT
                  backchannel_authentication_request_id,
                  tenant_id,
                  auth_req_id,
                  expired_at,
                  polling_interval,
                  status,
                  user_id,
                  user_payload,
                  authentication,
                  client_id,
                  client_payload,
                  scopes,
                  id_token_claims,
                  userinfo_claims,
                  custom_properties,
                  authorization_details,
                  consent_claims
                  FROM ciba_grant
                  """;
}
