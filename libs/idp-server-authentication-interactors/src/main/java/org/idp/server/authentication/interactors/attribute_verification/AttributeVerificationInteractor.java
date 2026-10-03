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
 * Checks the identified user part-way through the flow (Issue #1907).
 *
 * <p>A tenant configures named interactions, each of which is one kind of check:
 *
 * <ul>
 *   <li>{@code conditions} on what the account already is — identity-verified, a role, a custom
 *       property — written as authentication policy conditions. The policy's own {@code
 *       success_conditions} are only evaluated at the end, so an end-user who will never pass them
 *       otherwise finds out only after every step; placed right after the step that identifies
 *       them, this tells the authorization view at once, with an error the tenant chose, so it can
 *       send them on (to identity verification, for instance).
 *   <li>{@code fields}: values the end-user enters — a birthdate, the last digits of a phone number
 *       — against the attributes the account holds.
 * </ul>
 *
 * <p>The request names the interaction ({@code "interaction": "<name>"}), and a policy can place
 * several of them as separate steps ({@code step_definitions[].interaction}). Each is recorded
 * under its own name, {@code $.attribute-verification.interactions.<name>.*}, so a policy can lock
 * the account on failed guesses without locking every user who simply is not verified yet.
 *
 * <p>An additional check on a user who is already identified, not a way to identify one: it runs
 * only once an earlier step has established the user, and is recorded as {@link
 * OperationType#VERIFICATION}, so it never counts as an authentication factor on its own.
 *
 * <p>For entered values, the answer is only ever "matched" or "did not match". Which item was
 * wrong, and whether the account has the attribute at all, are not told: either would help someone
 * guessing.
 *
 * <p>Guessing is bounded twice, per interaction. Per user, across authorization requests, by a
 * counter in the cache that starting a new request does not reset. Per transaction, by the failures
 * already recorded on it, which holds even when the cache is unavailable. A tenant that wants a
 * failure to lock the account adds {@code lock_conditions} to its authentication policy, as for any
 * other step.
 */
public class AttributeVerificationInteractor implements AuthenticationInteractor {

  static final String CONFIG_KEY = "attribute-verification";

  /** The request field naming which configured interaction to run. */
  static final String INTERACTION_FIELD = "interaction";

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

    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, CONFIG_KEY);
    if (!configuration.exists()) {
      log.error("Attribute verification is not configured for this tenant.");
      return notConfigured(type, user);
    }

    String interaction = request.optValueAsString(INTERACTION_FIELD, "");
    AuthenticationInteractionConfig interactionConfig =
        interaction.isEmpty() ? null : configuration.getAuthenticationConfig(interaction);
    if (interactionConfig == null) {
      return unknownInteraction(type, user);
    }

    AttributeVerificationConfig config =
        AttributeVerificationConfig.from(interactionConfig.execution().details());
    if (!config.isValid()) {
      log.error(
          "Attribute verification interaction {} is not configured correctly: {}",
          interaction,
          config.invalidReason());
      return named(notConfigured(type, user), interaction);
    }

    AuthenticationInteractionRequestResult rejected =
        config.hasConditions()
            ? checkConditions(transaction, type, user, config)
            : verifyFields(tenant, transaction, type, request, user, interaction, config);
    if (rejected != null) {
      return named(rejected, interaction);
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
    return named(success, interaction);
  }

  private static AuthenticationInteractionRequestResult named(
      AuthenticationInteractionRequestResult result, String name) {
    result.setInteractionName(name);
    return result;
  }

  /**
   * Checks the account against the conditions. Nothing is entered, so nothing is guessed, and a
   * failure is not counted against any attempt limit.
   *
   * @return the rejection, or null when the conditions hold
   */
  private AuthenticationInteractionRequestResult checkConditions(
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      User user,
      AttributeVerificationConfig config) {
    if (MfaConditionEvaluator.isSatisfied(
        config.conditions(), transaction.interactionResults(), user)) {
      return null;
    }
    log.info("Attribute conditions did not hold. sub={}", user.sub());
    return conditionNotSatisfied(type, user, config.conditionError());
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
      String interaction,
      AttributeVerificationConfig config) {
    String attemptKey = attemptKey(tenant, user, interaction);
    if (exhaustedInTransaction(transaction, type, interaction, config)
        || exhaustedForUser(attemptKey, config)) {
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

  /**
   * Failed guesses already recorded on this transaction for this interaction. Read from the
   * database, not the cache.
   */
  private boolean exhaustedInTransaction(
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      String interaction,
      AttributeVerificationConfig config) {
    AuthenticationInteractionResults results = transaction.interactionResults();
    if (results == null || !results.contains(type.name())) {
      return false;
    }
    AuthenticationInteractionResult recorded = results.get(type.name());
    if (!recorded.hasInteractions()) {
      return false;
    }
    AuthenticationInteractionResult byInteraction = recorded.interactions().get(interaction);
    return byInteraction != null && byInteraction.failureCount() >= config.maxAttempts();
  }

  /**
   * Counts this attempt against the user, across authorization requests. Counted before the
   * comparison, so concurrent attempts cannot all slip under the limit; a match clears it.
   */
  private boolean exhaustedForUser(String attemptKey, AttributeVerificationConfig config) {
    long attempts = cacheStore.increment(attemptKey, config.lockoutSeconds());
    return attempts > config.maxAttempts();
  }

  private static String attemptKey(Tenant tenant, User user, String interaction) {
    return String.format(
        "attribute_verification_attempt:%s:%s:%s",
        tenant.identifierValue(), user.sub(), interaction);
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

  private AuthenticationInteractionRequestResult unknownInteraction(
      AuthenticationInteractionType type, User user) {
    return AuthenticationInteractionRequestResult.clientError(
        Map.of(
            "error", "invalid_request",
            "error_description", "interaction is missing or not configured."),
        type,
        operationType(),
        method(),
        user,
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
