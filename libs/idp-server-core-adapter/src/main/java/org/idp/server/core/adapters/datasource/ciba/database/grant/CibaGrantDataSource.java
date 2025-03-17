package org.idp.server.core.adapters.datasource.ciba.database.grant;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.type.ciba.AuthReqId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CibaGrantDataSource implements CibaGrantRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
            """
                    INSERT INTO public.ciba_grant
                    (backchannel_authentication_request_id, auth_req_id, expired_at, interval, status, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb);
                    """;
    List<Object> params = new ArrayList<>();
    AuthorizationGrant authorizationGrant = cibaGrant.authorizationGrant();
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());
    params.add(cibaGrant.authReqId().value());
    params.add(cibaGrant.expiredAt().toStringValue());
    params.add(cibaGrant.interval().toStringValue());
    params.add(cibaGrant.status().name());
    params.add(authorizationGrant.user().sub());
    params.add(toJson(authorizationGrant.user()));
    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.clientId().value());
    params.add(authorizationGrant.scopes().toStringValues());
    if (authorizationGrant.hasClaim()) {
      params.add(toJson(authorizationGrant.claimsPayload()));
    } else {
      params.add(null);
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

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
            """
                UPDATE ciba_grant
                SET authentication = ?::jsonb,
                status = ?
                WHERE backchannel_authentication_request_id = ?;
                """;
    List<Object> params = new ArrayList<>();
    params.add(toJson(cibaGrant.authorizationGrant().authentication()));
    params.add(cibaGrant.status().name());
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);

  }

  @Override
  public CibaGrant find(AuthReqId authReqId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            SELECT backchannel_authentication_request_id, auth_req_id, expired_at, interval, status, user_id, user_payload, authentication, client_id, scopes, claims, custom_properties, authorization_details
            FROM ciba_grant
            WHERE auth_req_id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(authReqId.value());

    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new CibaGrant();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(CibaGrant cibaGrant) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            DELETE FROM ciba_grant WHERE backchannel_authentication_request_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(cibaGrant.backchannelAuthenticationRequestIdentifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
