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

import java.util.Map;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public interface AuthenticationInteractor {

  AuthenticationInteractionType type();

  default OperationType operationType() {
    return OperationType.AUTHENTICATION;
  }

  /**
   * Returns whether this interactor is browser-based.
   *
   * <p>Browser-based interactors require AUTH_SESSION cookie validation to prevent session fixation
   * attacks. Device-based interactors (e.g., push notification confirmation) should return false as
   * the request comes from a different device without the cookie.
   *
   * @return true if browser-based (default), false if device-based
   */
  default boolean isBrowserBased() {
    return true;
  }

  String method();

  /**
   * What the authorization view needs, beyond the policy, to render this step — the inputs to ask
   * for, say. Returned in view-data under {@code authentication_step_hints.<method>}.
   *
   * <p>Only settings the end-user is about to be asked for anyway belong here; never a registered
   * value or anything secret. Empty by default: most steps are fully described by the policy.
   */
  default Map<String, Object> viewHints(Tenant tenant) {
    return Map.of();
  }

  AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository);
}
