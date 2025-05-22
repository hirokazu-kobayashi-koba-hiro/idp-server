/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oidc.authentication.exception.AuthenticationInteractorUnSupportedException;

public class AuthenticationInteractors {

  Map<AuthenticationInteractionType, AuthenticationInteractor> values;

  public AuthenticationInteractors(
      Map<AuthenticationInteractionType, AuthenticationInteractor> values) {
    this.values = values;
  }

  public AuthenticationInteractor get(AuthenticationInteractionType type) {
    AuthenticationInteractor interactor = values.get(type);

    if (Objects.isNull(interactor)) {
      throw new AuthenticationInteractorUnSupportedException(
          "No OAuthInteractor found for type " + type.name());
    }

    return interactor;
  }
}
