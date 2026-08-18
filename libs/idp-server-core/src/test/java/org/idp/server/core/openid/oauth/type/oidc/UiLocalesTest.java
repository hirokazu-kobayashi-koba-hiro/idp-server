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
 * ui_locales is "ordered by preference" (OIDC Core 3.1.2.1), and the sign-in view resolves it by
 * walking the list until it finds a bundle it has. The values used to be collected into a {@code
 * HashSet}, so "fr-CA fr en" could reach the view as "en fr-CA fr" and the wrong language would win
 * (#1801).
 */
class UiLocalesTest {

  @Test
  void keepsTheRequestedOrderOfPreference() {
    UiLocales uiLocales = new UiLocales("fr-CA fr en");

    assertEquals(List.of("fr-CA", "fr", "en"), uiLocales.toStringList());
    assertEquals(List.of("fr-CA", "fr", "en"), List.copyOf(uiLocales.values()));
    assertEquals("fr-CA fr en", uiLocales.toStringValues());
  }

  @Test
  void keepsTheOrderForEnoughTagsThatHashOrderWouldDiffer() {
    // Three tags can come back in request order from a HashSet by luck. This many will not.
    String requested = "ja-JP ja en-US en-GB en zh-Hant zh-Hans ko fr-CA fr de it es pt-BR";

    assertEquals(requested, new UiLocales(requested).toStringValues());
  }

  @Test
  void deduplicatesKeepingTheFirstOccurrence() {
    assertEquals(List.of("ja", "en"), new UiLocales("ja en ja").toStringList());
  }

  @Test
  void dropsBlankTagsFromADoubledSeparator() {
    // An empty tag would reach the view as a locale to resolve.
    assertEquals(List.of("ja", "en"), new UiLocales("ja  en").toStringList());
    assertEquals("ja en", new UiLocales("ja  en").toStringValues());
    assertEquals(List.of("ja"), new UiLocales(" ja ").toStringList());
  }

  @Test
  void isAbsentForNullEmptyAndWhitespaceOnly() {
    assertFalse(new UiLocales().exists());
    assertFalse(new UiLocales((String) null).exists());
    assertFalse(new UiLocales("").exists());
    assertFalse(new UiLocales("   ").exists());
  }

  @Test
  void isPresentForASingleTag() {
    UiLocales uiLocales = new UiLocales("ja-JP");

    assertTrue(uiLocales.exists());
    assertEquals(List.of("ja-JP"), uiLocales.toStringList());
  }

  @Test
  void toStringListIsImmutable() {
    List<String> values = new UiLocales("ja en").toStringList();

    assertThrows(UnsupportedOperationException.class, () -> values.add("de"));
  }
}
