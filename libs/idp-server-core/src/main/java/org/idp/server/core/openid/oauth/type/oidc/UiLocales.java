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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ui_locales OPTIONAL.
 *
 * <p>End-User's preferred languages and scripts for the user interface, represented as a
 * space-separated list of BCP47 [RFC5646] language tag values, ordered by preference. For instance,
 * the value "fr-CA fr en" represents a preference for French as spoken in Canada, then French
 * (without a region designation), followed by English (without a region designation). An error
 * SHOULD NOT result if some or all of the requested locales are not supported by the OpenID
 * Provider.
 *
 * <p>Preference order is part of the value, so the tags are held in a {@link LinkedHashSet}: the
 * request order survives {@link #values()}, {@link #toStringList()} and {@link #toStringValues()},
 * which is what the sign-in view needs to pick the first bundle it has. A plain {@code HashSet}
 * would hand the view an arbitrary order and "fr-CA fr en" could arrive as "en fr-CA fr".
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class UiLocales {

  Set<String> values;

  public UiLocales() {
    this.values = new LinkedHashSet<>();
  }

  public UiLocales(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new LinkedHashSet<>();
      return;
    }
    // Blanks are dropped so a doubled separator does not put an empty tag in the list handed to the
    // view, which would look like a locale it should try to resolve.
    this.values =
        Arrays.stream(value.split(" "))
            .filter(tag -> !tag.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Copies into a {@link LinkedHashSet} rather than holding the argument. Taking it as-is would let
   * a caller passing a {@code HashSet} silently drop the ordering this class exists to keep, and no
   * test on this class would notice.
   */
  public UiLocales(Set<String> values) {
    this.values = new LinkedHashSet<>(values);
  }

  public Set<String> values() {
    return values;
  }

  public List<String> toStringList() {
    return List.copyOf(values);
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }
}
