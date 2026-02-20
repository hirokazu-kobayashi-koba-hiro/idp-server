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

package org.idp.server.authentication.interactors.password.executor;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.PasswordPolicyConfig;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Password authentication executor that validates username and password credentials.
 *
 * <p>This executor implements the AuthenticationExecutor pattern for password authentication,
 * providing configuration-based and testable password verification.
 *
 * <p><b>Issue #898:</b> Refactored from direct implementation in PasswordAuthenticationInteractor
 * to follow EmailAuthenticationInteractor pattern.
 *
 * <p><b>Issue #897:</b> Uses preferred_username for user lookup instead of email.
 *
 * <p><b>Execution Logic:</b>
 *
 * <ol>
 *   <li>Extract username, password, provider_id from request
 *   <li>Search user by preferred_username (Issue #897)
 *   <li>Verify password using PasswordVerificationDelegation
 *   <li>Return success with user_id and username, or client error
 * </ol>
 *
 * @see org.idp.server.authentication.interactors.password.PasswordAuthenticationInteractor
 * @see org.idp.server.authentication.interactors.email.EmailAuthenticationInteractor
 */
public class PasswordAuthenticationExecutor implements AuthenticationExecutor {

  UserQueryRepository userQueryRepository;
  PasswordVerificationDelegation passwordVerificationDelegation;
  CacheStore cacheStore;
  LoggerWrapper log = LoggerWrapper.getLogger(PasswordAuthenticationExecutor.class);

  public PasswordAuthenticationExecutor(
      UserQueryRepository userQueryRepository,
      PasswordVerificationDelegation passwordVerificationDelegation,
      CacheStore cacheStore) {
    this.userQueryRepository = userQueryRepository;
    this.passwordVerificationDelegation = passwordVerificationDelegation;
    this.cacheStore = cacheStore;
  }

  @Override
  public String function() {
    return "password_verification";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    String username = request.optValueAsString("username", "");
    String password = request.optValueAsString("password", "");
    String providerId = request.optValueAsString("provider_id", "idp-server");

    log.debug(
        "Password authentication executor called. username={}, providerId={}",
        username,
        providerId);

    PasswordPolicyConfig passwordPolicyConfig =
        tenant.identityPolicyConfig().passwordPolicyConfig();
    String attemptKey = String.format("password_attempt:%s:%s", tenant.identifierValue(), username);

    if (passwordPolicyConfig.hasBruteForceProtection()) {
      long attemptCount =
          cacheStore.increment(attemptKey, passwordPolicyConfig.lockoutDurationSeconds());
      if (attemptCount > passwordPolicyConfig.maxAttempts()) {
        log.warn(
            "Password authentication locked out due to too many attempts. username={}, attemptCount={}",
            username,
            attemptCount);

        Map<String, Object> errorContents = new HashMap<>();
        errorContents.put("error", "too_many_attempts");
        errorContents.put("error_description", "Too many failed attempts. Please try again later.");

        return AuthenticationExecutionResult.clientError(errorContents);
      }
    }

    // Issue #897: Search by preferred_username instead of email
    User user = userQueryRepository.findByPreferredUsername(tenant, providerId, username);

    if (!user.exists()) {
      log.warn("User not found. username={}, providerId={}", username, providerId);

      Map<String, Object> errorContents = new HashMap<>();
      errorContents.put("error", "invalid_credentials");
      errorContents.put("error_description", "Invalid username or password");

      return AuthenticationExecutionResult.clientError(errorContents);
    }

    if (!passwordVerificationDelegation.verify(password, user.hashedPassword())) {
      log.warn(
          "Password verification failed. username={}, providerId={}, sub={}",
          username,
          providerId,
          user.sub());

      Map<String, Object> errorContents = new HashMap<>();
      errorContents.put("error", "invalid_credentials");
      errorContents.put("error_description", "Invalid username or password");

      return AuthenticationExecutionResult.clientError(errorContents);
    }

    cacheStore.delete(attemptKey);

    log.debug("Password authentication succeeded. username={}, sub={}", username, user.sub());

    Map<String, Object> successContents = new HashMap<>();
    successContents.put("user_id", user.sub());
    successContents.put("username", username);

    return AuthenticationExecutionResult.success(successContents);
  }
}
