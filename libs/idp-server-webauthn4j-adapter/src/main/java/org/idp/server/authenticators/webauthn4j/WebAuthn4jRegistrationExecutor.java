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

package org.idp.server.authenticators.webauthn4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.authentication.interactors.fido2.Fido2Challenge;
import org.idp.server.authentication.interactors.fido2.Fido2ExecutorType;
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

public class WebAuthn4jRegistrationExecutor implements AuthenticationExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jRegistrationExecutor(
      AuthenticationInteractionCommandRepository transactionCommandRepository,
      AuthenticationInteractionQueryRepository transactionQueryRepository,
      WebAuthn4jCredentialRepository credentialRepository) {
    this.transactionCommandRepository = transactionCommandRepository;
    this.transactionQueryRepository = transactionQueryRepository;
    this.credentialRepository = credentialRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public Fido2ExecutorType type() {
    return new Fido2ExecutorType("webauthn4j");
  }

  @Override
  public String function() {
    return "webauthn4j_registration";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    Fido2Challenge fido2Challenge =
        transactionQueryRepository.get(tenant, identifier, type().value(), Fido2Challenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge = new WebAuthn4jChallenge(fido2Challenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

    String userId = UUID.randomUUID().toString();

    WebAuthn4jRegistrationManager manager =
        new WebAuthn4jRegistrationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString, userId);

    WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();
    credentialRepository.register(webAuthn4jCredential);

    Map<String, Object> response = new HashMap<>();
    response.put("execution_webauthn4j", webAuthn4jCredential.toMap());

    return AuthenticationExecutionResult.success(response);
  }
}
