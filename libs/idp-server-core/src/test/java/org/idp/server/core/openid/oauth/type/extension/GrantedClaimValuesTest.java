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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
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

  @Nested
  class AppliedToUser {

    private static User userOwning(Map<String, Object> properties) {
      return new User().setSub("user-1").setCustomProperties(new HashMap<>(properties));
    }

    @Test
    void narrowsTheCopyThatBecomesTheGrant() {
      User user = userOwning(owned());

      User narrowed = user.narrowCustomProperties(selecting("accounts", "acc-2"));

      assertEquals(List.of("acc-2"), narrowed.customProperties().getValue("accounts"));
    }

    @Test
    void leavesTheOriginalUserUntouched() {
      // OAuthFlowEntryService hands the same user to UserRegistrator#registerOrUpdate after a
      // successful authorization, while OAuthAuthorizeContext narrows only the copy it puts in the
      // grant. If narrowing were in place, consenting to one account would DELETE the others from
      // the stored user. Consent decides what a token carries, never what the user owns.
      User user = userOwning(owned());

      User narrowed = user.narrowCustomProperties(selecting("accounts", "acc-2"));

      assertEquals(OWNED, user.customProperties().getValue("accounts"));
      assertNotSame(user, narrowed);
    }

    @Test
    void returnsTheSameInstanceWhenNothingWasSelected() {
      User user = userOwning(owned());

      assertSame(user, user.narrowCustomProperties(new GrantedClaimValues()));
      assertSame(user, user.narrowCustomProperties(null));
    }

    @Test
    void keepsTheRestOfTheUser() {
      User user = userOwning(owned()).setEmail("user@example.com");

      User narrowed = user.narrowCustomProperties(selecting("accounts", "acc-2"));

      assertEquals("user-1", narrowed.sub());
      assertEquals("user@example.com", narrowed.email());
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

    private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

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
