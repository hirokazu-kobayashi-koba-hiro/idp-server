package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantStatus;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.GrantIdTokenClaims;
import org.idp.server.core.oidc.grant.GrantUserinfoClaims;
import org.idp.server.core.oidc.grant.consent.ConsentClaim;
import org.idp.server.core.oidc.grant.consent.ConsentClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetail;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static CibaGrant convert(Map<String, String> stringMap) {
    BackchannelAuthenticationRequestIdentifier id =
        new BackchannelAuthenticationRequestIdentifier(
            stringMap.get("backchannel_authentication_request_id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));
    AuthReqId authReqId = new AuthReqId(stringMap.get("auth_req_id"));
    ExpiredAt expiredAt = new ExpiredAt(stringMap.get("expired_at"));
    Interval interval = new Interval(stringMap.get("polling_interval"));
    CibaGrantStatus status = CibaGrantStatus.valueOf(stringMap.get("status"));
    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Client client = jsonConverter.read(stringMap.get("client_payload"), Client.class);
    GrantType grantType = GrantType.of(stringMap.get("grant_type"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
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
            grantType,
            scopes,
            idTokenClaims,
            userinfoClaims,
            customProperties,
            authorizationDetails,
            consentClaims);

    return new CibaGrant(id, authorizationGrant, authReqId, expiredAt, interval, status);
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

      jsonNode
          .fieldNames()
          .forEachRemaining(
              fileName -> {
                List<JsonNodeWrapper> jsonNodeWrappers = jsonNode.getValueAsJsonNodeList(fileName);
                List<ConsentClaim> consentClaimList = new ArrayList<>();
                jsonNodeWrappers.forEach(
                    jsonNodeWrapper -> {
                      ConsentClaim consentClaim =
                          jsonConverter.read(jsonNodeWrapper.node(), ConsentClaim.class);
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
