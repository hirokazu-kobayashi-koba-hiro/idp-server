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
   * A custom property whose elements are objects rather than strings — {@code cards}, {@code
   * accounts} with attributes — which is what a real deployment stores.
   *
   * <p>Selection is by whole element: matching is {@link List#contains}, so an element matches when
   * the submitted object equals the owned one. {@link Map#equals} compares entry sets, so key order
   * does not matter, but every field must be present and equal — a request naming only the id does
   * not select the element.
   */
  @Nested
  class ObjectElements {

    private static final String OWNED_CARDS_JSON =
        """
        {"cards":[
          {"id":"card-1","brand":"visa","limit":100000},
          {"id":"card-2","brand":"master","limit":50000}
        ]}
        """;

    /** Properties as they come back from the JSONB column, through the same converter. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ownedCards() {
      return jsonConverter.read(OWNED_CARDS_JSON, Map.class);
    }

    /** A consent body as it arrives on the wire, through the same converter. */
    @SuppressWarnings("unchecked")
    private static GrantedClaimValues selectingJson(String grantedClaimValuesJson) {
      return GrantedClaimValues.fromObject(jsonConverter.read(grantedClaimValuesJson, Map.class));
    }

    @Test
    void keepsTheSelectedObject() {
      Map<String, Object> narrowed =
          selectingJson("{\"cards\":[{\"id\":\"card-1\",\"brand\":\"visa\",\"limit\":100000}]}")
              .narrow(ownedCards());

      assertEquals(1, ((List<?>) narrowed.get("cards")).size());
      assertEquals(
          Map.of("id", "card-1", "brand", "visa", "limit", 100000),
          ((List<?>) narrowed.get("cards")).get(0));
    }

    @Test
    void matchesRegardlessOfFieldOrder() {
      // Both sides are parsed JSON objects; Map equality is by entry set, not by insertion order,
      // so the consent screen may submit the fields in any order.
      Map<String, Object> narrowed =
          selectingJson("{\"cards\":[{\"limit\":100000,\"brand\":\"visa\",\"id\":\"card-1\"}]}")
              .narrow(ownedCards());

      assertEquals(1, ((List<?>) narrowed.get("cards")).size());
    }

    @Test
    void doesNotSelectByIdAlone() {
      // The limit of whole-element matching: a partial object is not the owned object, so it
      // matches nothing and the claim is dropped. Selecting by a key field would need the
      // selection to name which field identifies an element.
      Map<String, Object> narrowed =
          selectingJson("{\"cards\":[{\"id\":\"card-1\"}]}").narrow(ownedCards());

      assertFalse(narrowed.containsKey("cards"));
    }

    @Test
    void cannotIntroduceAnObjectTheUserDoesNotHave() {
      Map<String, Object> narrowed =
          selectingJson(
                  "{\"cards\":[{\"id\":\"card-1\",\"brand\":\"visa\",\"limit\":100000},"
                      + "{\"id\":\"card-9\",\"brand\":\"amex\",\"limit\":999999}]}")
              .narrow(ownedCards());

      assertEquals(1, ((List<?>) narrowed.get("cards")).size());
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

  /**
   * The selection is matched by equality, and the two sides of that comparison reach it by
   * different routes: the properties always come from the repository through the converter, while
   * the selection arrives on the wire on the authorizing request and from the sentinel afterwards.
   * A mapping that disagreed on a number would silently drop the element the End-User picked.
   */
  @Nested
  class ElementMappingIsNormalized {

    @Test
    void matchesAnOwnedIntegerSubmittedAsALong() {
      Map<String, Object> ownedLimits = new LinkedHashMap<>();
      ownedLimits.put("limits", List.of(100000));

      // A web layer that maps JSON integers to Long rather than Integer.
      GrantedClaimValues selection =
          GrantedClaimValues.fromObject(Map.of("limits", List.of(100000L)));

      assertEquals(List.of(100000), selection.narrow(ownedLimits).get("limits"));
    }

    @Test
    void matchesAnOwnedObjectWhoseNumberWasSubmittedAsALong() {
      Map<String, Object> ownedCards =
          jsonConverter.read("{\"cards\":[{\"id\":\"card-1\",\"limit\":100000}]}", Map.class);
      Map<String, Object> submitted = new LinkedHashMap<>();
      submitted.put("id", "card-1");
      submitted.put("limit", 100000L);

      GrantedClaimValues selection =
          GrantedClaimValues.fromObject(Map.of("cards", List.of(submitted)));

      assertEquals(1, ((List<?>) selection.narrow(ownedCards).get("cards")).size());
    }
  }

  @Nested
  class NullElements {

    @Test
    void areCarriedThroughRatherThanRejected() {
      // A submitted null is not a value the user holds, so it selects nothing. It must not take
      // the request down on the way there.
      GrantedClaimValues selection =
          GrantedClaimValues.fromObject(Map.of("accounts", java.util.Arrays.asList("acc-2", null)));

      assertEquals(List.of("acc-2"), selection.narrow(owned()).get("accounts"));
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
