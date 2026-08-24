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

package org.idp.server.core.openid.oauth.type.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Per-element consent for array claims (#1816).
 *
 * <p>{@code denied_claims} drops a claim whole, so a custom property holding several things the
 * user owns was all-or-nothing. This narrows it to what the End-User picked on the consent screen.
 */
class GrantedClaimValuesTest {

  private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  private static final List<String> OWNED = List.of("acc-1", "acc-2", "acc-3");

  private static Map<String, Object> owned() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("accounts", OWNED);
    properties.put("branch", "tokyo");
    return properties;
  }

  private static GrantedClaimValues selecting(String claim, Object... allowed) {
    return GrantedClaimValues.fromObject(Map.of(claim, List.of(allowed)));
  }

  @Nested
  class Narrowing {

    @Test
    void keepsOnlyTheSelectedElements() {
      Map<String, Object> narrowed = selecting("accounts", "acc-2").narrow(owned());

      assertEquals(List.of("acc-2"), narrowed.get("accounts"));
    }

    @Test
    void keepsTheOwnedOrderRatherThanTheRequestedOrder() {
      // The token should describe what the user has, not how the screen happened to submit it.
      Map<String, Object> narrowed = selecting("accounts", "acc-3", "acc-1").narrow(owned());

      assertEquals(List.of("acc-1", "acc-3"), narrowed.get("accounts"));
    }

    @Test
    void removesTheClaimWhenNothingIsSelected() {
      // Same result as denying the claim whole, and consistent with omitting a claim that has no
      // value (OIDC Core §5.3.2, #1699) — an empty array would be a third, distinct answer.
      Map<String, Object> narrowed =
          GrantedClaimValues.fromObject(Map.of("accounts", List.of())).narrow(owned());

      assertFalse(narrowed.containsKey("accounts"));
    }

    @Test
    void leavesPropertiesThatWereNotSelectedAlone() {
      Map<String, Object> narrowed = selecting("accounts", "acc-2").narrow(owned());

      assertEquals("tokyo", narrowed.get("branch"));
    }

    @Test
    void doesNotModifyTheInput() {
      Map<String, Object> original = owned();

      selecting("accounts", "acc-2").narrow(original);

      assertEquals(List.of("acc-1", "acc-2", "acc-3"), original.get("accounts"));
    }
  }

  @Nested
  class NarrowingOnly {

    @Test
    void cannotIntroduceAValueTheUserDoesNotHave() {
      // The security property. Without the intersection, naming a value here would write it
      // straight into a token claim.
      Map<String, Object> narrowed =
          selecting("accounts", "acc-2", "acc-999-not-owned").narrow(owned());

      assertEquals(List.of("acc-2"), narrowed.get("accounts"));
    }

    @Test
    void cannotIntroduceAClaimTheUserDoesNotHave() {
      Map<String, Object> narrowed = selecting("cards", "card-1").narrow(owned());

      assertFalse(narrowed.containsKey("cards"));
    }

    @Test
    void removesTheClaimWhenNoSelectedValueIsOwned() {
      Map<String, Object> narrowed = selecting("accounts", "acc-999-not-owned").narrow(owned());

      assertFalse(narrowed.containsKey("accounts"));
    }
  }

  /**
   * The selection travels with the grant as a sentinel token inside the existing claim-name TEXT
   * column, the same way the OIDC4IDA verified_claims request does (#1628). What matters is that an
   * element read back from storage still equals the element parsed from the user's properties, so
   * the intersection keeps working after a round trip.
   */
  @Nested
  class Sentinel {

    @Test
    void survivesARoundTrip() {
      GrantedClaimValues selection = selecting("accounts", "acc-2");

      GrantedClaimValues restored = GrantedClaimValues.fromSentinel(selection.toSentinelToken());

      assertEquals(List.of("acc-2"), restored.narrow(owned()).get("accounts"));
    }

    @Test
    void survivesARoundTripWithObjectElements() {
      Map<String, Object> ownedCards =
          jsonConverter.read(
              "{\"cards\":[{\"id\":\"card-1\",\"brand\":\"visa\",\"limit\":100000}]}", Map.class);
      GrantedClaimValues selection =
          GrantedClaimValues.fromObject(
              jsonConverter.read(
                  "{\"cards\":[{\"id\":\"card-1\",\"brand\":\"visa\",\"limit\":100000}]}",
                  Map.class));

      GrantedClaimValues restored = GrantedClaimValues.fromSentinel(selection.toSentinelToken());

      assertEquals(1, ((List<?>) restored.narrow(ownedCards).get("cards")).size());
    }

    @Test
    void isRecognizedByItsPrefix() {
      assertTrue(GrantedClaimValues.isSentinel(selecting("accounts", "acc-2").toSentinelToken()));
      assertFalse(GrantedClaimValues.isSentinel("accounts"));
      assertFalse(GrantedClaimValues.isSentinel(null));
    }

    @Test
    void isEmptyWhenNothingWasSelected() {
      assertEquals("", new GrantedClaimValues().toSentinelToken());
      assertFalse(GrantedClaimValues.fromSentinel("").exists());
      assertFalse(GrantedClaimValues.fromSentinel("accounts").exists());
    }
  }

  @Nested
  class DeniedWholeClaims {

    @Test
    void dropsTheSelectionForADeniedClaim() {
      // Nothing left to select between once the claim itself is denied.
      GrantedClaimValues selection = selecting("accounts", "acc-2");

      GrantedClaimValues remaining = selection.removeClaims(new DeniedClaims(List.of("accounts")));

      assertFalse(remaining.exists());
    }

    @Test
    void keepsSelectionsForClaimsThatWereNotDenied() {
      GrantedClaimValues selection =
          GrantedClaimValues.fromObject(
              Map.of("accounts", List.of("acc-2"), "cards", List.of("card-1")));

      GrantedClaimValues remaining = selection.removeClaims(new DeniedClaims(List.of("accounts")));

      assertFalse(remaining.values().containsKey("accounts"));
      assertEquals(List.of("card-1"), remaining.values().get("cards"));
    }
  }

  @Nested
  class Ignored {

    @Test
    void leavesScalarPropertiesUntouched() {
      // There is nothing to select between; denied_claims already covers all-or-nothing for these.
      Map<String, Object> narrowed = selecting("branch", "osaka").narrow(owned());

      assertEquals("tokyo", narrowed.get("branch"));
    }

    @Test
    void isAbsentForAnythingThatIsNotAnObject() {
      assertFalse(GrantedClaimValues.fromObject(null).exists());
      assertFalse(GrantedClaimValues.fromObject("accounts").exists());
      assertFalse(GrantedClaimValues.fromObject(List.of("acc-1")).exists());
      assertFalse(GrantedClaimValues.fromObject(Map.of()).exists());
    }

    @Test
    void skipsEntriesWhoseValueIsNotAnArray() {
      GrantedClaimValues parsed =
          GrantedClaimValues.fromObject(Map.of("accounts", "acc-2", "cards", List.of("card-1")));

      assertFalse(parsed.values().containsKey("accounts"));
      assertTrue(parsed.values().containsKey("cards"));
    }

    @Test
    void returnsThePropertiesUnchangedWhenNothingIsSelected() {
      Map<String, Object> original = owned();

      assertEquals(original, new GrantedClaimValues().narrow(original));
    }
  }
}
