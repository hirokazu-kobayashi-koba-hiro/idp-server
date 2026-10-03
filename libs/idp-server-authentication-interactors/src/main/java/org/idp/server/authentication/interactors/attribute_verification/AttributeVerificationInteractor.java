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
import org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Checks the identified user part-way through the flow (Issue #1907), in two ways that can be used
 * together:
 *
 * <ul>
 *   <li>conditions on what the account already is — identity-verified, a role, a custom property —
 *       written as authentication policy conditions. The policy's own {@code success_conditions}
 *       are only evaluated at the end, so an end-user who will never pass them otherwise finds out
 *       only after every step; placed right after the step that identifies them, this tells the
 *       authorization view at once, with an error the tenant chose, so it can send them on (to
 *       identity verification, for instance).
 *   <li>values the end-user enters — a birthdate, the last digits of a phone number — against the
 *       attributes the account holds.
 * </ul>
 *
 * <p>An additional check on a user who is already identified, not a way to identify one: it runs
 * only once an earlier step has established the user, and is recorded as {@link
 * OperationType#VERIFICATION}, so it never counts as an authentication factor on its own.
 *
 * <p>For entered values, the answer is only ever "matched" or "did not match". Which item was
 * wrong, and whether the account has the attribute at all, are not told: either would help someone
 * guessing.
 *
 * <p>Guessing is bounded twice. Per user, across authorization requests, by a counter in the cache
 * that starting a new request does not reset. Per transaction, by the failures already recorded on
 * it, which holds even when the cache is unavailable. A tenant that wants a failure to lock the
 * account adds {@code lock_conditions} to its authentication policy, as for any other step.
 */
public class AttributeVerificationInteractor implements AuthenticationInteractor {

  static final String CONFIG_KEY = "attribute-verification";

  /**
   * The names the two checks are recorded under, as {@code
   * $.attribute-verification.interactions.<name>.*}. Kept apart so a policy can lock the account on
   * failed guesses ({@code fields}) without locking every user who is simply not verified yet
   * ({@code conditions}).
   */
  static final String CONDITIONS = "conditions";

  static final String FIELDS = "fields";

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

    if (config.hasConditions()
        && !MfaConditionEvaluator.isSatisfied(
            config.conditions(), transaction.interactionResults(), user)) {
      log.info("Attribute conditions did not hold. sub={}", user.sub());
      return named(conditionNotSatisfied(type, user, config.conditionError()), CONDITIONS);
    }

    if (config.hasFields()) {
      AuthenticationInteractionRequestResult rejected =
          verifyFields(tenant, transaction, type, request, user, config);
      if (rejected != null) {
        return named(rejected, FIELDS);
      }
    }

    AuthenticationInteractionRequestResult success =
        new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.SUCCESS,
            type,
            operationType(),
            method(),
            user,
            Map.of(),
            DefaultSecurityEventType.attribute_verification_success);
    return named(success, config.hasFields() ? FIELDS : CONDITIONS);
  }

  private static AuthenticationInteractionRequestResult named(
      AuthenticationInteractionRequestResult result, String name) {
    result.setInteractionName(name);
    return result;
  }

  /**
   * Compares the entered values, within the attempt limits.
   *
   * @return the rejection, or null when every field matched
   */
  private AuthenticationInteractionRequestResult verifyFields(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      User user,
      AttributeVerificationConfig config) {
    String attemptKey = attemptKey(tenant, user);
    if (exhaustedInTransaction(transaction, type, config) || exhaustedForUser(attemptKey, config)) {
      return tooManyAttempts(type, user);
    }
    if (!allMatch(config, request, new VerifiableUserAttributes(user))) {
      log.info("Attribute verification did not match. sub={}", user.sub());
      return mismatch(type, user);
    }
    cacheStore.delete(attemptKey);
    return null;
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

  /**
   * Failed guesses already recorded on this transaction. Read from the database, not the cache.
   * Only the {@code fields} breakdown counts: an account that does not meet the conditions has not
   * guessed anything.
   */
  private boolean exhaustedInTransaction(
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AttributeVerificationConfig config) {
    AuthenticationInteractionResults results = transaction.interactionResults();
    if (results == null || !results.contains(type.name())) {
      return false;
    }
    AuthenticationInteractionResult recorded = results.get(type.name());
    if (!recorded.hasInteractions()) {
      return false;
    }
    AuthenticationInteractionResult fields = recorded.interactions().get(FIELDS);
    return fields != null && fields.failureCount() >= config.maxAttempts();
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

  /**
   * The account is not what this step requires — not identity-verified, say. The error is the
   * tenant's, so the authorization view can tell this apart and send the end-user where they can do
   * something about it.
   */
  private AuthenticationInteractionRequestResult conditionNotSatisfied(
      AuthenticationInteractionType type, User user, String error) {
    return AuthenticationInteractionRequestResult.clientError(
        Map.of(
            "error",
            error,
            "error_description",
            "the account does not meet the conditions for this step."),
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
