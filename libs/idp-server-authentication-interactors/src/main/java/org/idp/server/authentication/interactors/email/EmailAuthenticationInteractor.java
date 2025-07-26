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
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class EmailAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationInteractionCommandRepository commandRepository;
  AuthenticationInteractionQueryRepository queryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailAuthenticationInteractor.class);

  public EmailAuthenticationInteractor(
      AuthenticationInteractionCommandRepository commandRepository,
      AuthenticationInteractionQueryRepository queryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_AUTHENTICATION.toType();
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.EMAIL.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("EmailAuthenticationInteractor called");

    EmailVerificationChallenge emailVerificationChallenge =
        queryRepository.get(
            tenant, transaction.identifier(), "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult =
        emailVerificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpEmailVerificationChallenge =
          emailVerificationChallenge.countUp();
      commandRepository.update(
          tenant, transaction.identifier(), "email", countUpEmailVerificationChallenge);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          operationType(),
          method(),
          transaction.user(),
          verificationResult.response(),
          DefaultSecurityEventType.email_verification_failure);
    }

    User verifiedUser = transaction.user();
    verifiedUser.setEmailVerified(true);

    Map<String, Object> response = new HashMap<>();
    response.put("user", verifiedUser.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        verifiedUser,
        response,
        DefaultSecurityEventType.email_verification_success);
  }
}
