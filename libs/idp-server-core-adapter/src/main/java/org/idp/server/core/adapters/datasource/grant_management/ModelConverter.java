/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.grant_management;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaim;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthorizationGranted convert(Map<String, String> stringMap) {
    AuthorizationGrantedIdentifier identifier =
        new AuthorizationGrantedIdentifier(stringMap.get("id"));
    TenantIdentifier tenantIdentifier = new TenantIdentifier(stringMap.get("tenant_id"));

    User user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);

    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    ClientAttributes clientAttributes =
        jsonConverter.read(stringMap.get("client_payload"), ClientAttributes.class);

    GrantType grantType = GrantType.of(stringMap.get("grant_type"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = convertCustomProperties(stringMap.get("custom_properties"));
    GrantIdTokenClaims idTokenClaims = new GrantIdTokenClaims(stringMap.get("id_token_claims"));
    GrantUserinfoClaims userinfoClaims = new GrantUserinfoClaims(stringMap.get("userinfo_claims"));
    AuthorizationDetails authorizationDetails =
        AuthorizationDetails.fromString(stringMap.get("authorization_details"));
    ConsentClaims consentClaims = convertConsentClaims(stringMap.get("consent_claims"));

    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            tenantIdentifier,
            user,
            authentication,
            requestedClientId,
            clientAttributes,
            grantType,
            scopes,
            idTokenClaims,
            userinfoClaims,
            customProperties,
            authorizationDetails,
            consentClaims);

    LocalDateTime createdAt = parseLocalDateTime(stringMap.get("created_at"));
    LocalDateTime updatedAt = parseLocalDateTime(stringMap.get("updated_at"));

    return new AuthorizationGranted(identifier, authorizationGrant, createdAt, updatedAt);
  }

  private static LocalDateTime parseLocalDateTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return LocalDateTimeParser.parse(value);
    } catch (Exception e) {
      return null;
    }
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
