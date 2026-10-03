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

package org.idp.server.core.openid.authentication;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.authentication.exception.AuthenticationInteractorNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationInteractors {

  Map<AuthenticationInteractionType, AuthenticationInteractor> values;

  public AuthenticationInteractors(
      Map<AuthenticationInteractionType, AuthenticationInteractor> values) {
    this.values = values;
  }

  public boolean contains(AuthenticationInteractionType type) {
    return values.containsKey(type);
  }

  /**
   * The view hints of the interactors behind {@code methods}, keyed by method. Methods with no
   * hints are left out, so the view only finds a key where there is something to read.
   */
  public Map<String, Map<String, Object>> viewHints(Tenant tenant, Collection<String> methods) {
    Map<String, Map<String, Object>> hints = new HashMap<>();
    for (AuthenticationInteractor interactor : values.values()) {
      if (!methods.contains(interactor.method()) || hints.containsKey(interactor.method())) {
        continue;
      }
      Map<String, Object> interactorHints = interactor.viewHints(tenant);
      if (interactorHints != null && !interactorHints.isEmpty()) {
        hints.put(interactor.method(), interactorHints);
      }
    }
    return hints;
  }

  public AuthenticationInteractor get(AuthenticationInteractionType type) {
    AuthenticationInteractor interactor = values.get(type);

    if (Objects.isNull(interactor)) {
      throw new AuthenticationInteractorNotFoundException(
          "Unsupported interaction type: " + type.name());
    }

    return interactor;
  }
}
