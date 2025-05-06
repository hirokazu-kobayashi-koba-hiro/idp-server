package org.idp.server.basic.type.oidc;

import java.util.Objects;

/**
 * Claims
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *      Authentication Request</a>
 */
public class ClaimsValue {
  String value;

  public ClaimsValue() {}

  public ClaimsValue(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ClaimsValue that = (ClaimsValue) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
