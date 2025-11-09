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

import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationStepDefinition;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public interface AuthenticationInteractor {

  AuthenticationInteractionType type();

  default OperationType operationType() {
    return OperationType.AUTHENTICATION;
  }

  String method();

  AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository);

  /**
   * Gets the current step definition for this authentication method from the transaction's
   * authentication policy.
   *
   * <p>Thread-safe implementation using for-loop instead of Stream API to avoid concurrency issues
   * in parallel test execution.
   *
   * @param transaction the authentication transaction
   * @param method the authentication method
   * @return the step definition, or null if not found
   */
  default AuthenticationStepDefinition getCurrentStepDefinition(
      AuthenticationTransaction transaction, String method) {

    if (!transaction.hasAuthenticationPolicy()) {
      return null;
    }

    AuthenticationPolicy policy = transaction.authenticationPolicy();
    if (!policy.hasStepDefinitions()) {
      return null;
    }

    // Use for-loop instead of stream for thread safety in parallel test execution
    for (AuthenticationStepDefinition step : policy.stepDefinitions()) {
      if (method.equals(step.authenticationMethod())) {
        return step;
      }
    }
    return null;
  }
}
