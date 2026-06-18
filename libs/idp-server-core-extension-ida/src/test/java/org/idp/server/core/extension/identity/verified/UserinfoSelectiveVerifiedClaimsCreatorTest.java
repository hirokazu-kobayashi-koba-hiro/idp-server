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

package org.idp.server.core.extension.identity.verified;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.grant_management.grant.RequestedVerifiedClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * #1628: the scope path ({@code verified_claims:*}) and the claims-parameter path ({@link
 * UserinfoVerifiedClaimsCreator}) both emit the same top-level {@code verified_claims} key and the
 * invoker composes creators via {@code putAll}. These tests pin the precedence rule that keeps the
 * result deterministic: the claims-parameter path is authoritative, so this scope-based creator
 * stands down whenever a claims-parameter verified_claims request is present on the grant.
 */
class UserinfoSelectiveVerifiedClaimsCreatorTest {

  private final UserinfoSelectiveVerifiedClaimsCreator creator =
      new UserinfoSelectiveVerifiedClaimsCreator();

  private static AuthorizationServerConfiguration serverConfigWithSelectiveEnabled() {
    Map<String, Object> extension = new HashMap<>();
    extension.put("accessTokenSelectiveVerifiedClaims", true);
    Map<String, Object> config = new HashMap<>();
    config.put("extension", extension);
    return JsonConverter.defaultInstance().read(config, AuthorizationServerConfiguration.class);
  }

  /** Builds a GrantUserinfoClaims carrying the userinfo.verified_claims request (via sentinel). */
  private static GrantUserinfoClaims grantUserinfoClaimsRequesting(String verifiedClaimsJson) {
    String json = "{\"userinfo\":{\"verified_claims\":" + verifiedClaimsJson + "}}";
    RequestedClaimsPayload payload =
        JsonConverter.snakeCaseInstance().read(json, RequestedClaimsPayload.class);
    VerifiedClaimsObject requested = payload.userinfo().verifiedClaims();
    return new GrantUserinfoClaims(Set.of(), RequestedVerifiedClaims.of(requested));
  }

  private static AuthorizationGrant grantWith(
      Scopes scopes, GrantUserinfoClaims userinfoClaims, User user) {
    return new AuthorizationGrantBuilder(
            new TenantIdentifier("11111111-1111-1111-1111-111111111111"),
            new RequestedClientId("client"),
            GrantType.authorization_code,
            scopes)
        .add(user)
        .add(userinfoClaims)
        .build();
  }

  private static User userWith(Map<String, Object> verification, Map<String, Object> claims) {
    Map<String, Object> verifiedClaims = new HashMap<>();
    verifiedClaims.put("verification", verification);
    verifiedClaims.put("claims", claims);
    return new User().setVerifiedClaims(verifiedClaims);
  }

  @Test
  void createsForScopeOnlyRequest() {
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    Scopes scopes = new Scopes(Set.of("openid", "verified_claims:given_name"));
    AuthorizationGrant grant = grantWith(scopes, new GrantUserinfoClaims(Set.of()), user);

    assertTrue(creator.shouldCreate(user, grant, serverConfigWithSelectiveEnabled(), null));
  }

  @Test
  void standsDownWhenClaimsParameterRequestIsPresent() {
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    Scopes scopes = new Scopes(Set.of("openid", "verified_claims:given_name"));
    GrantUserinfoClaims userinfoClaims =
        grantUserinfoClaimsRequesting(
            "{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");
    AuthorizationGrant grant = grantWith(scopes, userinfoClaims, user);

    assertFalse(creator.shouldCreate(user, grant, serverConfigWithSelectiveEnabled(), null));
  }
}
