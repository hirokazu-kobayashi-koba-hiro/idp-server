package org.idp.server.type.rar;

import java.util.Objects;

/**
 * @see <a
 *     href="https://www.rfc-editor.org/rfc/rfc9396.html#name-authorization-request">Authorization
 *     Details</a>
 */
public class AuthorizationDetailsValue {
  Object value;

  public AuthorizationDetailsValue() {}

  public AuthorizationDetailsValue(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
