package org.idp.server.type.oidc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * acr_values OPTIONAL.
 *
 * <p>Requested Authentication Context Class Reference values. Space-separated string that specifies
 * the acr values that the Authorization Server is being requested to use for processing this
 * Authentication Request, with the values appearing in order of preference. The Authentication
 * Context Class satisfied by the authentication performed is returned as the acr Claim Value, as
 * specified in Section 2. The acr Claim is requested as a Voluntary Claim by this parameter.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class AcrValues {

  Set<String> values;

  public AcrValues() {
    this.values = new HashSet<>();
  }

  public AcrValues(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
    ;
  }

  public AcrValues(Set<String> values) {
    this.values = values;
  }

  public Set<String> values() {
    return values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }
}
