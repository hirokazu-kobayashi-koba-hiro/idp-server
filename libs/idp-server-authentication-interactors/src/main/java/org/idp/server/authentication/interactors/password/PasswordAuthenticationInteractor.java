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

package org.idp.server.authentication.interactors.password;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class PasswordAuthenticationInteractor implements AuthenticationInteractor {

  PasswordVerificationDelegation passwordVerificationDelegation;

  public PasswordAuthenticationInteractor(
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {

    String username = request.optValueAsString("username", "");
    String password = request.optValueAsString("password", "");
    String providerId = request.optValueAsString("provider_id", "idp-server");

    User user = userQueryRepository.findByEmail(tenant, username, providerId);
    if (!passwordVerificationDelegation.verify(password, user.hashedPassword())) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "user is not found or invalid password");

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          user,
          new Authentication(),
          response,
          DefaultSecurityEventType.password_failure);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.password_success);
  }
}
