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
import org.idp.server.authentication.interactors.fido2.Fido2ExecutorType;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Executor for WebAuthn4j credential deregistration (deletion).
 *
 * <p>This executor handles the deletion of WebAuthn/FIDO2 credentials from the repository. It
 * requires the credential_id to be provided in the request.
 */
public class WebAuthn4jDeregistrationExecutor implements AuthenticationExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jDeregistrationExecutor.class);

  WebAuthn4jCredentialRepository credentialRepository;

  public WebAuthn4jDeregistrationExecutor(WebAuthn4jCredentialRepository credentialRepository) {
    this.credentialRepository = credentialRepository;
  }

  public Fido2ExecutorType type() {
    return new Fido2ExecutorType("webauthn4j");
  }

  @Override
  public String function() {
    return "webauthn4j_deregistration";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    try {
      String credentialId = request.getValueAsString("credential_id");
      if (credentialId == null || credentialId.isEmpty()) {
        log.warn("webauthn4j deregistration failed: credential_id is required");
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_request");
        errorResponse.put("error_description", "credential_id is required");
        return AuthenticationExecutionResult.clientError(errorResponse);
      }

      log.debug("webauthn4j deregistration, attempting to delete credential: {}", credentialId);

      // Verify credential exists before deletion
      WebAuthn4jCredential credential = credentialRepository.get(tenant, credentialId);
      String username = credential.username();

      // Delete the credential
      credentialRepository.delete(tenant, credentialId);

      Map<String, Object> response = new HashMap<>();
      response.put("credential_id", credentialId);
      response.put("username", username);
      response.put("deleted", true);
      Map<String, Object> result = new HashMap<>();
      result.put("execution_webauthn4j", response);

      log.info(
          "webauthn4j deregistration succeeded. credential_id: {}, username: {}",
          credentialId,
          username);
      return AuthenticationExecutionResult.success(result);

    } catch (NotFoundException e) {
      log.warn("webauthn4j deregistration failed: credential not found - {}", e.getMessage());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "credential_not_found");
      errorResponse.put("error_description", "The specified credential was not found");
      return AuthenticationExecutionResult.clientError(errorResponse);

    } catch (Exception e) {
      log.error("webauthn4j unexpected error during deregistration", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "server_error");
      errorResponse.put("error_description", "An unexpected error occurred during deregistration");
      return AuthenticationExecutionResult.serverError(errorResponse);
    }
  }
}
