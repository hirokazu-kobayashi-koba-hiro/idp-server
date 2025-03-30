package org.idp.server.core.adapters.datasource.oauth.database.code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.grant.GrantIdTokenClaims;
import org.idp.server.core.oauth.grant.GrantUserinfoClaims;
import org.idp.server.core.oauth.grant.consent.ConsentClaim;
import org.idp.server.core.oauth.grant.consent.ConsentClaims;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.AuthorizationCode;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static AuthorizationCodeGrant convert(Map<String, String> stringMap) {
    AuthorizationRequestIdentifier id =
        new AuthorizationRequestIdentifier(stringMap.get("authorization_request_id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));
    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Client client = jsonConverter.read(stringMap.get("client_payload"), Client.class);
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = convertCustomProperties(stringMap.get("custom_properties"));
    GrantIdTokenClaims idTokenClaims = new GrantIdTokenClaims(stringMap.get("id_token_claims"));
    GrantUserinfoClaims userinfoClaims = new GrantUserinfoClaims(stringMap.get("userinfo_claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    ConsentClaims consentClaims = convertConsentClaims(stringMap.get("consent_claims"));

    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            tenantIdentifier,
            user,
            authentication,
            requestedClientId,
            client,
            scopes,
            idTokenClaims,
            userinfoClaims,
            customProperties,
            authorizationDetails,
            consentClaims);

    AuthorizationCode authorizationCode =
        new AuthorizationCode(stringMap.get("authorization_code"));
    ExpiredAt expiredAt = new ExpiredAt(stringMap.get("expired_at"));
    return new AuthorizationCodeGrant(id, authorizationGrant, authorizationCode, expiredAt);
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

  private static ConsentClaims convertConsentClaims(String value) {
    if (value == null || value.isEmpty()) {
      return new ConsentClaims();
    }
    try {
      JsonNodeWrapper jsonNode = jsonConverter.readTree(value);
      Map<String, List<ConsentClaim>> claimMap = new HashMap<>();

      jsonNode.fieldNames().forEachRemaining(fileName -> {
        List<JsonNodeWrapper> jsonNodeWrappers = jsonNode.getValueAsJsonNodeList(fileName);
        List<ConsentClaim> consentClaimList = new ArrayList<>();
        jsonNodeWrappers.forEach(jsonNodeWrapper -> {
          ConsentClaim consentClaim = jsonConverter.read(jsonNodeWrapper.node(), ConsentClaim.class);
          consentClaimList.add(consentClaim);
        });
        claimMap.put(fileName, consentClaimList);
      });

      return new ConsentClaims(claimMap);
    } catch (Exception exception) {
      return new ConsentClaims();
    }
  }
}
