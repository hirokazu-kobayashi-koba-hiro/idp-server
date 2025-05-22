/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.request;

import java.util.Objects;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;

/** AuthorizationRequestIdentifier */
public class AuthorizationRequestIdentifier {
  String value;

  public AuthorizationRequestIdentifier() {}

  public AuthorizationRequestIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AuthorizationRequestIdentifier that = (AuthorizationRequestIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public AuthorizationIdentifier toAuthorizationIdentifier() {
    return new AuthorizationIdentifier(value);
  }
}
