package org.idp.server.type.oidc;

import java.util.Arrays;
import java.util.HashSet;
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
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class UiLocales {

  Set<String> values;

  public UiLocales() {
    this.values = new HashSet<>();
  }

  public UiLocales(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public UiLocales(Set<String> values) {
    this.values = values;
  }

  public Set<String> values() {
    return values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }
}
