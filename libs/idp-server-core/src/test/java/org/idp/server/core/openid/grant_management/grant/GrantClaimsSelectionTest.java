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

package org.idp.server.core.openid.grant_management.grant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.oauth.type.extension.DeniedClaims;
import org.idp.server.core.openid.oauth.type.extension.GrantedClaimValues;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * How the End-User's per-element selection rides on a grant's claim sets (#1816).
 *
 * <p>The selection is carried inside the existing claim-name TEXT column as a sentinel token, the
 * same way the OIDC4IDA verified_claims request is (#1628), so it survives to issuance without a
 * schema change.
 */
class GrantClaimsSelectionTest {

  private static GrantedClaimValues selecting(String claim, String... allowed) {
    return GrantedClaimValues.fromObject(Map.of(claim, List.of(allowed)));
  }

  @Nested
  class Persistence {

    @Test
    void survivesTheIdTokenClaimsRoundTrip() {
      GrantIdTokenClaims claims =
          new GrantIdTokenClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"));

      GrantIdTokenClaims restored = new GrantIdTokenClaims(claims.toStringValues());

      assertEquals(List.of("acc-2"), restored.grantedClaimValues().values().get("accounts"));
      assertTrue(restored.toStringSet().contains("email"));
    }

    @Test
    void survivesTheUserinfoClaimsRoundTrip() {
      GrantUserinfoClaims claims =
          new GrantUserinfoClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"));

      GrantUserinfoClaims restored = new GrantUserinfoClaims(claims.toStringValues());

      assertEquals(List.of("acc-2"), restored.grantedClaimValues().values().get("accounts"));
    }

    @Test
    void isPersistedEvenWhenNoStandardClaimIsReleased() {
      // A request for claims:accounts alone releases no standard claim. Without exists() covering
      // the sentinel, the write path would skip the column and the selection would be lost.
      GrantIdTokenClaims idTokenClaims =
          new GrantIdTokenClaims(Set.of()).withGrantedClaimValues(selecting("accounts", "acc-2"));
      GrantUserinfoClaims userinfoClaims =
          new GrantUserinfoClaims(Set.of()).withGrantedClaimValues(selecting("accounts", "acc-2"));

      assertTrue(idTokenClaims.exists());
      assertTrue(userinfoClaims.exists());
    }

    @Test
    void isIgnoredByAReaderThatDoesNotKnowTheSentinel() {
      // Claim emission matches known claim names rather than enumerating the token set, so an
      // unrecognized token is inert for code that predates this change. Keeps rolling deploys safe.
      GrantIdTokenClaims claims =
          new GrantIdTokenClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"));

      assertTrue(claims.hasEmail());
      assertFalse(claims.toStringSet().stream().anyMatch(name -> name.startsWith("gcv:")));
    }
  }

  @Nested
  class BothClaimSetsCarryTheSameSelection {

    @Test
    void soEitherAnswersForTheGrant() {
      GrantedClaimValues selection = selecting("accounts", "acc-2");

      GrantIdTokenClaims idTokenClaims =
          new GrantIdTokenClaims(Set.of("email")).withGrantedClaimValues(selection);
      GrantUserinfoClaims userinfoClaims =
          new GrantUserinfoClaims(Set.of("email")).withGrantedClaimValues(selection);

      assertEquals(
          idTokenClaims.grantedClaimValues().values(),
          userinfoClaims.grantedClaimValues().values());
    }

    @Test
    void andADeniedClaimNameLeavesTheSelectionInPlace() {
      // Custom claims are released by the grant's claims:* scopes, not by these claim names, so a
      // denied name does not stop them. Dropping the selection here would hand the client every
      // element of a claim the End-User narrowed to one — the opposite of what denying asks for.
      DeniedClaims denied = new DeniedClaims(List.of("accounts"));

      GrantIdTokenClaims idTokenClaims =
          new GrantIdTokenClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"))
              .removeClaims(denied);
      GrantUserinfoClaims userinfoClaims =
          new GrantUserinfoClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"))
              .removeClaims(denied);

      assertEquals(List.of("acc-2"), idTokenClaims.grantedClaimValues().values().get("accounts"));
      assertEquals(List.of("acc-2"), userinfoClaims.grantedClaimValues().values().get("accounts"));
    }
  }

  @Nested
  class Merging {

    @Test
    void theLatestConsentSupersedesTheEarlierOne() {
      GrantIdTokenClaims first =
          new GrantIdTokenClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-1"));
      GrantIdTokenClaims second =
          new GrantIdTokenClaims(Set.of("name"))
              .withGrantedClaimValues(selecting("accounts", "acc-2"));

      GrantIdTokenClaims merged = first.merge(second);

      assertEquals(List.of("acc-2"), merged.grantedClaimValues().values().get("accounts"));
    }

    @Test
    void anEarlierSelectionSurvivesAConsentThatMadeNoSelection() {
      GrantIdTokenClaims first =
          new GrantIdTokenClaims(Set.of("email"))
              .withGrantedClaimValues(selecting("accounts", "acc-1"));
      GrantIdTokenClaims second = new GrantIdTokenClaims(Set.of("name"));

      GrantIdTokenClaims merged = first.merge(second);

      assertEquals(List.of("acc-1"), merged.grantedClaimValues().values().get("accounts"));
    }
  }
}
