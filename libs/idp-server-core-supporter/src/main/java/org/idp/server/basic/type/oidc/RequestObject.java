package org.idp.server.basic.type.oidc;

import java.util.Objects;

/**
 * Passing Request Parameters as JWTs
 *
 * <p>
 * OpenID Connect defines the following Authorization Request parameters to enable Authentication
 * Requests to be signed and optionally encrypted:
 *
 * <p>
 * request OPTIONAL. This parameter enables OpenID Connect requests to be passed in a single,
 * self-contained parameter and to be optionally signed and/or encrypted. The parameter value is a
 * Request Object value, as specified in Section 6.1. It represents the request as a JWT whose
 * Claims are the request parameters.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#JWTRequests">reqyest
 *      object</a>
 *      <p>
 *      * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *      Authentication Request</a>
 */
public class RequestObject {
  String value;

  public RequestObject() {}

  public RequestObject(String value) {
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
    RequestObject that = (RequestObject) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
