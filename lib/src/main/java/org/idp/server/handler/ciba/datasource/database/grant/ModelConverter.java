package org.idp.server.handler.ciba.datasource.database.grant;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.grant.CibaGrantStatus;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetail;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static CibaGrant convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestIdentifier id =
        new BackchannelAuthenticationRequestIdentifier(
            stringMap.get("backchannel_authentication_request_id"));
    AuthReqId authReqId = new AuthReqId(stringMap.get("auth_req_id"));
    ExpiredAt expiredAt = new ExpiredAt(stringMap.get("expired_at"));
    Interval interval = new Interval(stringMap.get("interval"));
    CibaGrantStatus status = CibaGrantStatus.valueOf(stringMap.get("status"));
    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    ClaimsPayload claimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            user,
            authentication,
            clientId,
            scopes,
            claimsPayload,
            customProperties,
            authorizationDetails);
    return new CibaGrant(id, authorizationGrant, authReqId, expiredAt, interval, status);
  }

  private static ClaimsPayload convertClaimsPayload(String value) {
    if (value.isEmpty()) {
      return new ClaimsPayload();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return jsonConverter.read(value, ClaimsPayload.class);
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }

  // TODO
  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value.isEmpty()) {
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
