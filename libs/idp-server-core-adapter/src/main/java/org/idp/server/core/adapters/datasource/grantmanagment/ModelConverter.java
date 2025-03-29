package org.idp.server.core.adapters.datasource.grantmanagment;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthorizationGranted convert(Map<String, String> stringMap) {
    AuthorizationGrantedIdentifier identifier =
        new AuthorizationGrantedIdentifier(stringMap.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));

    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);

    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Client client = jsonConverter.read(stringMap.get("client_payload"), Client.class);

    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = convertCustomProperties(stringMap.get("custom_properties"));
    RequestedClaimsPayload requestedClaimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));

    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            tenantIdentifier,
            user,
            authentication,
            requestedClientId,
            client,
            scopes,
            requestedClaimsPayload,
            customProperties,
            authorizationDetails);

    return new AuthorizationGranted(identifier, authorizationGrant);
  }

  private static RequestedClaimsPayload convertClaimsPayload(String value) {
    if (value == null || value.isEmpty()) {
      return new RequestedClaimsPayload();
    }
    try {

      return jsonConverter.read(value, RequestedClaimsPayload.class);
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
    }
  }

  private static CustomProperties convertCustomProperties(String value) {
    if (value == null || value.isEmpty()) {
      return new CustomProperties();
    }
    try {

      Map map = jsonConverter.read(value, Map.class);

      return new CustomProperties(map);
    } catch (Exception exception) {
      return new CustomProperties();
    }
  }

  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value == null || value.isEmpty()) {
      return new AuthorizationDetails();
    }
    try {

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
