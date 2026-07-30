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

package org.idp.server.core.openid.grant_management;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/** #1729: the grant management response must expose the fields the model actually carries. */
class AuthorizationGrantedTest {

  private AuthorizationGrantBuilder baseBuilder(GrantType grantType, Scopes scopes) {
    return new AuthorizationGrantBuilder(
            new TenantIdentifier("11111111-1111-1111-1111-111111111111"),
            new RequestedClientId("client-x"),
            grantType,
            scopes)
        .add(new ClientAttributes("client-x", null, "My App", null, null, null, null, null));
  }

  @Test
  void toMap_exposesAuthorizationGrantModelFields() {
    AuthorizationGrant grant =
        baseBuilder(GrantType.authorization_code, new Scopes("openid profile email"))
            .add(new GrantIdTokenClaims(Set.of("sub", "email")))
            .add(new GrantUserinfoClaims(Set.of("name")))
            .add(new CustomProperties(Map.of("department", "sales")))
            .add(AuthorizationDetails.fromString("[{\"type\":\"payment_initiation\"}]"))
            .build();

    Map<String, Object> map =
        new AuthorizationGranted(new AuthorizationGrantedIdentifier("grant-1"), grant).toMap();

    assertEquals("authorization_code", map.get("grant_type"));

    assertEquals(
        Set.of("openid", "profile", "email"), new HashSet<>(asStringList(map.get("scopes"))));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> details =
        (List<Map<String, Object>>) map.get("authorization_details");
    assertEquals(1, details.size());
    assertEquals("payment_initiation", details.get(0).get("type"));

    assertEquals(Set.of("sub", "email"), new HashSet<>(asStringList(map.get("id_token_claims"))));
    assertEquals(Set.of("name"), new HashSet<>(asStringList(map.get("userinfo_claims"))));

    @SuppressWarnings("unchecked")
    Map<String, Object> customProperties = (Map<String, Object>) map.get("custom_properties");
    assertEquals("sales", customProperties.get("department"));
  }

  @Test
  void toMap_omitsEmptyOptionalFields() {
    AuthorizationGrant grant =
        baseBuilder(GrantType.client_credentials, new Scopes("openid")).build();

    Map<String, Object> map =
        new AuthorizationGranted(new AuthorizationGrantedIdentifier("grant-2"), grant).toMap();

    // grant_type is always present; it distinguishes client_credentials from authorization_code.
    assertEquals("client_credentials", map.get("grant_type"));
    // Optional fields are only emitted when present, matching the existing consent_claims behavior.
    assertFalse(map.containsKey("authorization_details"));
    assertFalse(map.containsKey("id_token_claims"));
    assertFalse(map.containsKey("userinfo_claims"));
    assertFalse(map.containsKey("custom_properties"));
  }

  @SuppressWarnings("unchecked")
  private List<String> asStringList(Object value) {
    return (List<String>) value;
  }
}
