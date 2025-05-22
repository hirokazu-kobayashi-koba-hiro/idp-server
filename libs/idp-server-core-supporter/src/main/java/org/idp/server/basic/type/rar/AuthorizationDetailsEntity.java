/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.rar;

import java.util.Objects;

/**
 * @see <a
 *     href="https://www.rfc-editor.org/rfc/rfc9396.html#name-authorization-request">Authorization
 *     Details</a>
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
