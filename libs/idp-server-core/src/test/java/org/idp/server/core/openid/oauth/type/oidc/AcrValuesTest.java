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

package org.idp.server.core.openid.oauth.type.oidc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * acr_values is "in order of preference" (OIDC Core 3.1.2.1) and had the same {@code HashSet}
 * problem as {@link UiLocales} (#1801). Nothing decides differently because of the order today —
 * every consumer asks {@link AcrValues#contains} — but the value is persisted with the
 * authorization request through {@code toStringValues()}, so a scrambled order is what a later
 * reader sees.
 */
class AcrValuesTest {

  @Test
  void keepsTheRequestedOrderOfPreference() {
    String requested =
        "urn:mace:incommon:iap:gold urn:mace:incommon:iap:silver urn:mace:incommon:iap:bronze";

    assertEquals(requested, new AcrValues(requested).toStringValues());
  }

  @Test
  void keepsTheOrderForEnoughValuesThatHashOrderWouldDiffer() {
    String requested = "acr1 acr2 acr3 acr4 acr5 acr6 acr7 acr8 acr9 acr10 acr11 acr12";

    assertEquals(requested, new AcrValues(requested).toStringValues());
    assertEquals(List.of("acr1", "acr2"), List.copyOf(new AcrValues("acr1 acr2").values()));
  }

  @Test
  void stillMatchesByMembership() {
    AcrValues acrValues = new AcrValues("acr1 acr2");

    assertTrue(acrValues.contains("acr2"));
    assertFalse(acrValues.contains("acr3"));
  }

  @Test
  void keepsBlankEntriesFromADoubledSeparator() {
    // Unlike UiLocales, blanks are not filtered: only the ordering was broken here. A doubled
    // separator therefore still yields an empty member, which no consumer looks up.
    AcrValues doubledSeparator = new AcrValues("acr1  acr2");

    assertTrue(doubledSeparator.contains(""));
    // The round trip is exact now that the order is kept. Under the previous HashSet the empty
    // member could land anywhere, so " acr1 acr2" and "acr1 acr2 " were equally possible.
    assertEquals("acr1  acr2", doubledSeparator.toStringValues());
  }

  @Test
  void isAbsentForNullAndEmpty() {
    assertFalse(new AcrValues().exists());
    assertFalse(new AcrValues((String) null).exists());
    assertFalse(new AcrValues("").exists());
    // split(" ") drops trailing empty entries, so a whitespace-only value has always been absent.
    assertFalse(new AcrValues(" ").exists());
  }
}
