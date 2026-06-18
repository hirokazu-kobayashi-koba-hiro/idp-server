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
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * #1628: the UserInfo verified_claims creator reads the request from the grant (the consent record
 * persisted at authorization time), not a live request, then defers assembly to {@link
 * VerifiedClaimsAssembler} — the same engine as the ID Token path. These tests pin the grant-read
 * wiring and the shouldCreate gating; the §5.7 / value-values rules are covered by {@code
 * VerifiedClaimsCreatorTest}.
 */
class UserinfoVerifiedClaimsCreatorTest {

  private final UserinfoVerifiedClaimsCreator creator = new UserinfoVerifiedClaimsCreator();

  /** Builds a GrantUserinfoClaims carrying the userinfo.verified_claims request (via sentinel). */
  private static GrantUserinfoClaims grantUserinfoClaimsRequesting(String verifiedClaimsJson) {
    String json = "{\"userinfo\":{\"verified_claims\":" + verifiedClaimsJson + "}}";
    RequestedClaimsPayload payload =
        JsonConverter.snakeCaseInstance().read(json, RequestedClaimsPayload.class);
    VerifiedClaimsObject requested = payload.userinfo().verifiedClaims();
    return new GrantUserinfoClaims(Set.of(), RequestedVerifiedClaims.of(requested));
  }

  private static AuthorizationGrant grantWith(GrantUserinfoClaims userinfoClaims, User user) {
    return new AuthorizationGrantBuilder(
            new TenantIdentifier("11111111-1111-1111-1111-111111111111"),
            new RequestedClientId("client"),
            GrantType.authorization_code,
            new Scopes(Set.of("openid")))
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> claimsOf(Map<String, Object> result) {
    Map<String, Object> structure = (Map<String, Object>) result.get("verified_claims");
    return (Map<String, Object>) structure.get("claims");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> verificationOf(Map<String, Object> result) {
    Map<String, Object> structure = (Map<String, Object>) result.get("verified_claims");
    return (Map<String, Object>) structure.get("verification");
  }

  @Test
  void emitsVerifiedClaimsReadFromTheGrant() {
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    GrantUserinfoClaims userinfoClaims =
        grantUserinfoClaimsRequesting(
            "{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");
    AuthorizationGrant grant = grantWith(userinfoClaims, user);

    assertTrue(creator.shouldCreate(user, grant, null, null));
    Map<String, Object> result = creator.create(user, grant, null, null);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("Taro", claimsOf(result).get("given_name"));
  }

  @Test
  void enforcesValueConstraintThroughTheSharedAssembler() {
    // Proves the assembler is wired: a value constraint not satisfied by the user drops the claim
    // (§5.7.4 claims branch). The IDA schema permits an empty claims object ([IDA-verified-claims]
    // §5.3), so verification + an empty claims object is returned — not a whole omission.
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    GrantUserinfoClaims userinfoClaims =
        grantUserinfoClaimsRequesting(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"given_name\":{\"value\":\"Hanako\"}}}");
    AuthorizationGrant grant = grantWith(userinfoClaims, user);

    Map<String, Object> result = creator.create(user, grant, null, null);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertTrue(
        claimsOf(result).isEmpty(), "claim failing its value constraint dropped; claims empty");
  }

  @Test
  void doesNotCreateWhenGrantHasNoVerifiedClaimsRequest() {
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    AuthorizationGrant grant = grantWith(new GrantUserinfoClaims(Set.of("email")), user);

    assertFalse(creator.shouldCreate(user, grant, null, null));
  }

  @Test
  void doesNotCreateWhenUserHasNoVerifiedClaims() {
    User user = new User();
    GrantUserinfoClaims userinfoClaims =
        grantUserinfoClaimsRequesting(
            "{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");
    AuthorizationGrant grant = grantWith(userinfoClaims, user);

    assertFalse(creator.shouldCreate(user, grant, null, null));
  }
}
