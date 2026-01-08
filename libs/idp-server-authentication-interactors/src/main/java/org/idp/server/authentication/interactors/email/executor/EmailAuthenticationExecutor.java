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

package org.idp.server.authentication.interactors.email.executor;

import java.util.Map;
import org.idp.server.authentication.interactors.email.EmailVerificationChallenge;
import org.idp.server.authentication.interactors.email.EmailVerificationResult;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class EmailAuthenticationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  JsonConverter jsonConverter;

  public EmailAuthenticationExecutor(
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationInteractionQueryRepository interactionQueryRepository) {
    this.interactionCommandRepository = interactionCommandRepository;
    this.interactionQueryRepository = interactionQueryRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String function() {
    return "email_authentication";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    EmailVerificationChallenge verificationChallenge =
        interactionQueryRepository.get(
            tenant, identifier, "email-authentication-challenge", EmailVerificationChallenge.class);

    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult = verificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpVerificationChallenge = verificationChallenge.countUp();
      interactionCommandRepository.update(
          tenant, identifier, "email-authentication-challenge", countUpVerificationChallenge);

      return AuthenticationExecutionResult.clientError(verificationResult.response());
    }

    // Delete challenge after successful verification to prevent replay attacks
    interactionCommandRepository.delete(tenant, identifier, "email-authentication-challenge");

    return AuthenticationExecutionResult.success(Map.of());
  }
}
