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

import static org.idp.server.authentication.interactors.attribute_verification.AttributeVerificationRejection.*;

import java.util.HashMap;
import java.util.List;
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
  AttributeVerificationAttempts attempts;
  LoggerWrapper log = LoggerWrapper.getLogger(AttributeVerificationInteractor.class);

  public AttributeVerificationInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      CacheStore cacheStore) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.attempts = new AttributeVerificationAttempts(cacheStore);
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

  /**
   * What the authorization view needs for each configured interaction, keyed by name: whether it
   * checks the account ({@code conditions}, nothing to ask — the view can submit at once) or
   * compares entered values ({@code fields}, with the inputs to ask for). Nothing here is
   * registered data. An interaction whose configuration cannot be used is left out; the step itself
   * then answers {@code server_error}.
   */
  @Override
  public Map<String, Object> viewHints(Tenant tenant) {
    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, CONFIG_KEY);
    if (!configuration.exists()) {
      return Map.of();
    }
    Map<String, Object> interactions = new HashMap<>();
    configuration
        .authentications()
        .forEach(
            (name, interactionConfig) -> {
              AttributeVerificationConfig config =
                  AttributeVerificationConfig.from(interactionConfig.execution().details());
              if (config.isValid()) {
                interactions.put(name, viewHintOf(config));
              }
            });
    return interactions.isEmpty() ? Map.of() : Map.of("interactions", interactions);
  }

  private static Map<String, Object> viewHintOf(AttributeVerificationConfig config) {
    if (config.hasConditions()) {
      return Map.of("kind", "conditions", "inputs", List.of());
    }
    List<Map<String, Object>> inputs =
        config.fields().stream().map(AttributeVerificationField::toViewHint).toList();
    return Map.of("kind", "fields", "inputs", inputs);
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
      return reject(USER_NOT_IDENTIFIED, type, null, null);
    }
    User user = transaction.user();

    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, CONFIG_KEY);
    if (!configuration.exists()) {
      log.error("Attribute verification is not configured for this tenant.");
      return reject(NOT_CONFIGURED, type, user, null);
    }

    String interaction = request.optValueAsString(INTERACTION_FIELD, "");
    AuthenticationInteractionConfig interactionConfig =
        interaction.isEmpty() ? null : configuration.getAuthenticationConfig(interaction);
    if (interactionConfig == null) {
      return reject(UNKNOWN_INTERACTION, type, user, interaction);
    }

    AttributeVerificationConfig config =
        AttributeVerificationConfig.from(interactionConfig.execution().details());
    if (!config.isValid()) {
      log.error(
          "Attribute verification interaction {} is not configured correctly: {}",
          interaction,
          config.invalidReason());
      return reject(NOT_CONFIGURED, type, user, interaction);
    }

    if (config.hasFields() && hasNonStringInput(config, request)) {
      return reject(INVALID_INPUT, type, user, interaction);
    }

    if (config.hasConditions()) {
      if (!MfaConditionEvaluator.isSatisfied(
          config.conditions(),
          transaction.interactionResults(),
          user,
          transaction.requestForPolicy())) {
        log.info("Attribute conditions did not hold. sub={}", user.sub());
        return CONDITION_NOT_SATISFIED.toResult(
            type, method(), user, interaction, config.conditionError());
      }
    } else {
      if (attempts.exhausted(tenant, transaction, type, user, interaction, config)) {
        return reject(TOO_MANY_ATTEMPTS, type, user, interaction);
      }
      if (!allMatch(config, request, new VerifiableUserAttributes(user))) {
        log.info("Attribute verification did not match. sub={}", user.sub());
        return reject(MISMATCH, type, user, interaction);
      }
      attempts.clear(tenant, user, interaction);
    }

    return success(type, user, interaction);
  }

  private AuthenticationInteractionRequestResult success(
      AuthenticationInteractionType type, User user, String interaction) {
    AuthenticationInteractionRequestResult success =
        new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.SUCCESS,
            type,
            operationType(),
            method(),
            user,
            Map.of(),
            DefaultSecurityEventType.attribute_verification_success);
    success.setInteractionName(interaction);
    return success;
  }

  private AuthenticationInteractionRequestResult reject(
      AttributeVerificationRejection rejection,
      AuthenticationInteractionType type,
      User user,
      String interaction) {
    return rejection.toResult(type, method(), user, interaction);
  }

  /** Whether any configured input arrived as something other than a string. */
  private static boolean hasNonStringInput(
      AttributeVerificationConfig config, AuthenticationInteractionRequest request) {
    return config.fields().stream()
        .map(AttributeVerificationField::input)
        .filter(request::containsKey)
        .map(request::getValue)
        .anyMatch(value -> value != null && !(value instanceof String));
  }

  /**
   * Every field is compared, even after one has failed, so the time taken does not say how many of
   * them were right.
   */
  private static boolean allMatch(
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
}
