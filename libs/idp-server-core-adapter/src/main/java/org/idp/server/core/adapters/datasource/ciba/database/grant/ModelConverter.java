package org.idp.server.core.adapters.datasource.ciba.database.grant;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantStatus;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.ciba.Interval;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static CibaGrant convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestIdentifier id =
        new BackchannelAuthenticationRequestIdentifier(
            stringMap.get("backchannel_authentication_request_id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));
    AuthReqId authReqId = new AuthReqId(stringMap.get("auth_req_id"));
    ExpiredAt expiredAt = new ExpiredAt(stringMap.get("expired_at"));
    Interval interval = new Interval(stringMap.get("interval"));
    CibaGrantStatus status = CibaGrantStatus.valueOf(stringMap.get("status"));
    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Client client = jsonConverter.read(stringMap.get("client_payload"), Client.class);
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    RequestedClaimsPayload requestedClaimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));

    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(tenantIdentifier, requestedClientId, scopes)
            .add(user)
            .add(client)
            .add(authentication)
            .add(requestedClaimsPayload)
            .add(customProperties)
            .add(authorizationDetails)
            .build();

    return new CibaGrant(id, authorizationGrant, authReqId, expiredAt, interval, status);
  }

  private static RequestedClaimsPayload convertClaimsPayload(String value) {
    if (value == null || value.isEmpty()) {
      return new RequestedClaimsPayload();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return jsonConverter.read(value, RequestedClaimsPayload.class);
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
    }
  }

  // TODO
  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value == null || value.isEmpty()) {
      return new AuthorizationDetails();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      List list = jsonConverter.read(value, List.class);
      List<Map> details = (List<Map>) list;
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
