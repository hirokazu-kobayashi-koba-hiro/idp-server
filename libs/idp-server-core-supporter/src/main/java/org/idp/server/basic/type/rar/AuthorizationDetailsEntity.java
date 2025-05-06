package org.idp.server.basic.type.rar;

import java.util.Objects;

/**
 * @see <a href=
 *      "https://www.rfc-editor.org/rfc/rfc9396.html#name-authorization-request">Authorization
 *      Details</a>
 */
public class AuthorizationDetailsEntity {
  Object value;

  public AuthorizationDetailsEntity() {}

  public AuthorizationDetailsEntity(Object value) {
    this.value = value;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }
}
