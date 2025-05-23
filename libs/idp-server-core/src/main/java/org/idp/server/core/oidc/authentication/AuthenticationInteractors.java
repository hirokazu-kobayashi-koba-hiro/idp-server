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
