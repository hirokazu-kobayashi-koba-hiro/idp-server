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


package org.idp.server.authentication.interactors.email;

import java.util.*;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class EmailAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationInteractionCommandRepository commandRepository;
  AuthenticationInteractionQueryRepository queryRepository;

  public EmailAuthenticationInteractor(
      AuthenticationInteractionCommandRepository commandRepository,
      AuthenticationInteractionQueryRepository queryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    EmailVerificationChallenge emailVerificationChallenge =
        queryRepository.get(
            tenant, authorizationIdentifier, "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult =
        emailVerificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpEmailVerificationChallenge =
          emailVerificationChallenge.countUp();
      commandRepository.update(
          tenant, authorizationIdentifier, "email", countUpEmailVerificationChallenge);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          transaction.user(),
          new Authentication(),
          verificationResult.response(),
          DefaultSecurityEventType.email_verification_failure);
    }

    User user = transaction.user();

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("otp")))
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
        DefaultSecurityEventType.email_verification_success);
  }
}
