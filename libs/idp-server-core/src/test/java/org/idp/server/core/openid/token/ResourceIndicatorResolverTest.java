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

package org.idp.server.core.openid.token;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Resolving which resource the granted scopes are for.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
 */
class ResourceIndicatorResolverTest {

  static final String API = "https://api.example.com";
  static final String ADMIN = "https://admin.example.com";

  static final Map<String, List<String>> MAPPING =
      Map.of(API, List.of("openid", "account"), ADMIN, List.of("management"));

  @Nested
  class Resolution {

    @Test
    void resolvesTheResourceTheScopesBelongTo() {
      assertEquals(List.of(API), ResourceIndicatorResolver.resolve(MAPPING, List.of("account")));
    }

    @Test
    void resolvesNothingWhenNoScopeIsMapped() {
      // The caller falls back to the configured default, which is what keeps aud present.
      assertTrue(ResourceIndicatorResolver.resolve(MAPPING, List.of("unmapped")).isEmpty());
    }

    @Test
    void resolvesEveryResourceTheScopesSpan() {
      // The caller refuses this rather than picking one: an audience listing both would let each
      // resource accept scopes meant for the other.
      List<String> resolved =
          ResourceIndicatorResolver.resolve(MAPPING, List.of("account", "management"));

      assertEquals(2, resolved.size());
      assertTrue(resolved.containsAll(List.of(API, ADMIN)));
    }

    @Test
    void resolvesNothingWithoutConfiguration() {
      assertTrue(ResourceIndicatorResolver.resolve(Map.of(), List.of("account")).isEmpty());
      assertTrue(ResourceIndicatorResolver.resolve(null, List.of("account")).isEmpty());
      assertTrue(ResourceIndicatorResolver.resolve(MAPPING, null).isEmpty());
    }
  }

  @Nested
  class ResourceIndicatorForm {

    @Test
    void ignoresAKeyThatIsNotAnAbsoluteUri() {
      // A client identifier is the value most likely to be put here by mistake, and an audience
      // naming the client instead of the resource is the confusion the claim exists to prevent.
      Map<String, List<String>> mapping =
          Map.of("3c50f6b9-d525-4d1d-ba92-f7eba219e886", List.of("account"));

      assertTrue(ResourceIndicatorResolver.resolve(mapping, List.of("account")).isEmpty());
    }

    @Test
    void ignoresAKeyCarryingAFragment() {
      Map<String, List<String>> mapping = Map.of("https://api.example.com/#v1", List.of("account"));

      assertTrue(ResourceIndicatorResolver.resolve(mapping, List.of("account")).isEmpty());
    }

    @Test
    void keepsTheOtherEntriesWhenOneIsUnusable() {
      // One bad entry must not stop tokens being issued for the resources that are configured
      // correctly.
      Map<String, List<String>> mapping =
          Map.of("not-a-uri", List.of("account"), ADMIN, List.of("management"));

      assertEquals(
          List.of(ADMIN),
          ResourceIndicatorResolver.resolve(mapping, List.of("account", "management")));
    }

    @Test
    void ignoresAnEntryWhoseScopesAreNotAList() {
      // A value written as a string rather than an array reads as null. Dropping the entry keeps
      // the promise the resolver makes: a malformed entry is ignored, and the rest still resolve.
      Map<String, List<String>> mapping = new HashMap<>();
      mapping.put(API, null);
      mapping.put(ADMIN, List.of("management"));

      assertEquals(
          List.of(ADMIN),
          ResourceIndicatorResolver.resolve(mapping, List.of("account", "management")));
    }

    @Test
    void acceptsAnAbsoluteUriWithAQueryComponent() {
      // RFC 8707 permits a query component and forbids only a fragment.
      Map<String, List<String>> mapping =
          Map.of("https://api.example.com/?tenant=a", List.of("account"));

      assertEquals(1, ResourceIndicatorResolver.resolve(mapping, List.of("account")).size());
    }
  }
}
