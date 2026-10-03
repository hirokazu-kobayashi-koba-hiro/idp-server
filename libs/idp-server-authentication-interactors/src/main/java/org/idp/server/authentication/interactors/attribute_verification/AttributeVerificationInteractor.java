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

package org.idp.server.authentication.interactors.attribute_verification;

import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Checks values the end-user enters — a birthdate, the last digits of a phone number — against the
 * attributes the account holds (Issue #1907).
 *
 * <p>An additional check on a user who is already identified, not a way to identify one: it runs
 * only once an earlier step has established the user, and is recorded as {@link
 * OperationType#VERIFICATION}, so it never counts as an authentication factor on its own.
 *
 * <p>The answer is only ever "matched" or "did not match". Which item was wrong, and whether the
 * account has the attribute at all, are not told: either would help someone guessing.
 *
 * <p>Guessing is bounded twice. Per user, across authorization requests, by a counter in the cache
 * that starting a new request does not reset. Per transaction, by the failures already recorded on
 * it, which holds even when the cache is unavailable. A tenant that wants a failure to lock the
 * account adds {@code lock_conditions} to its authentication policy, as for any other step.
 */
public class AttributeVerificationInteractor implements AuthenticationInteractor {

  static final String CONFIG_KEY = "attribute-verification";

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  CacheStore cacheStore;
  LoggerWrapper log = LoggerWrapper.getLogger(AttributeVerificationInteractor.class);

  public AttributeVerificationInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      CacheStore cacheStore) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.cacheStore = cacheStore;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.ATTRIBUTE_VERIFICATION.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.VERIFICATION;
  }

  @Override
  public String method() {
    return "attribute-verification";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    if (!transaction.hasTrustedUser()) {
      return userNotIdentified(type);
    }
    User user = transaction.user();

    AttributeVerificationConfig config = loadConfig(tenant);
    if (!config.isValid()) {
      log.error("Attribute verification is not configured correctly: {}", config.invalidReason());
      return notConfigured(type, user);
    }

    String attemptKey = attemptKey(tenant, user);
    if (exhaustedInTransaction(transaction, type, config) || exhaustedForUser(attemptKey, config)) {
      return tooManyAttempts(type, user);
    }

    if (!allMatch(config, request, new VerifiableUserAttributes(user))) {
      log.info("Attribute verification did not match. sub={}", user.sub());
      return mismatch(type, user);
    }

    cacheStore.delete(attemptKey);
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        Map.of(),
        DefaultSecurityEventType.attribute_verification_success);
  }

  /**
   * Every field is compared, even after one has failed, so the time taken does not say how many of
   * them were right.
   */
  private boolean allMatch(
      AttributeVerificationConfig config,
      AuthenticationInteractionRequest request,
      VerifiableUserAttributes attributes) {
    boolean matched = true;
    for (AttributeVerificationField field : config.fields()) {
      String submitted = request.optValueAsString(field.input(), null);
      String registered = attributes.valueOf(field.userAttribute());
      matched &= field.matches(submitted, registered);
    }
    return matched;
  }

  private AttributeVerificationConfig loadConfig(Tenant tenant) {
    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, CONFIG_KEY);
    if (!configuration.exists()) {
      return AttributeVerificationConfig.from(null);
    }
    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig(CONFIG_KEY);
    if (interactionConfig == null) {
      return AttributeVerificationConfig.from(null);
    }
    return AttributeVerificationConfig.from(interactionConfig.execution().details());
  }

  /** Failures already recorded on this transaction. Read from the database, not the cache. */
  private boolean exhaustedInTransaction(
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AttributeVerificationConfig config) {
    AuthenticationInteractionResults results = transaction.interactionResults();
    if (results == null || !results.contains(type.name())) {
      return false;
    }
    return results.get(type.name()).failureCount() >= config.maxAttempts();
  }

  /**
   * Counts this attempt against the user, across authorization requests. Counted before the
   * comparison, so concurrent attempts cannot all slip under the limit; a match clears it.
   */
  private boolean exhaustedForUser(String attemptKey, AttributeVerificationConfig config) {
    long attempts = cacheStore.increment(attemptKey, config.lockoutSeconds());
    return attempts > config.maxAttempts();
  }

  private static String attemptKey(Tenant tenant, User user) {
    return String.format(
        "attribute_verification_attempt:%s:%s", tenant.identifierValue(), user.sub());
  }

  private AuthenticationInteractionRequestResult userNotIdentified(
      AuthenticationInteractionType type) {
    return AuthenticationInteractionRequestResult.clientError(
        Map.of(
            "error", "invalid_request",
            "error_description", "attribute verification requires an identified user."),
        type,
        operationType(),
        method(),
        DefaultSecurityEventType.attribute_verification_failure);
  }

  private AuthenticationInteractionRequestResult notConfigured(
      AuthenticationInteractionType type, User user) {
    return AuthenticationInteractionRequestResult.serverError(
        Map.of(
            "error", "server_error",
            "error_description", "attribute verification is not configured."),
        type,
        operationType(),
        method(),
        user,
        DefaultSecurityEventType.attribute_verification_failure);
  }

  private AuthenticationInteractionRequestResult tooManyAttempts(
      AuthenticationInteractionType type, User user) {
    return AuthenticationInteractionRequestResult.clientError(
        Map.of(
            "error", "too_many_attempts",
            "error_description", "Too many failed attempts. Please try again later."),
        type,
        operationType(),
        method(),
        user,
        DefaultSecurityEventType.attribute_verification_failure);
  }

  private AuthenticationInteractionRequestResult mismatch(
      AuthenticationInteractionType type, User user) {
    return AuthenticationInteractionRequestResult.clientError(
        Map.of(
            "error", "attribute_mismatch",
            "error_description", "the submitted values do not match the registered attributes."),
        type,
        operationType(),
        method(),
        user,
        DefaultSecurityEventType.attribute_verification_failure);
  }
}
