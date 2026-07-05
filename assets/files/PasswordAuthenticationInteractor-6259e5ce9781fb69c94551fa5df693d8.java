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

package org.idp.server.authentication.interactors.password;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationStepDefinition;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Password authentication interactor.
 *
 * <p><b>Issue #898:</b> Refactored to use AuthenticationExecutor pattern following
 * EmailAuthenticationInteractor pattern.
 *
 * <p><b>Issue #800:</b> Implements resolveUser() for database-first user resolution.
 *
 * <p><b>Issue #897:</b> Uses preferred_username for user lookup via PasswordAuthenticationExecutor.
 *
 * @see org.idp.server.authentication.interactors.email.EmailAuthenticationInteractor
 * @see org.idp.server.authentication.interactors.password.executor.PasswordAuthenticationExecutor
 */
public class PasswordAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(PasswordAuthenticationInteractor.class);

  /**
   * Standard OIDC claims that a 2nd-factor {@code user_resolve} may merge into the already
   * authenticated session user (plus {@code custom_properties}, handled separately).
   *
   * <p><b>SECURITY (CWE-287, Issue #1497):</b> the identity established by the 1st factor is
   * authoritative on the 2nd factor. Identifiers ({@code sub} / {@code preferred_username} / {@code
   * email} / {@code phone_number} — any of which is a tenant unique key / {@code
   * user_identity_source}), lifecycle ({@code status}) and privileges ({@code roles} / {@code
   * permissions} / {@code authentication_devices}) must NEVER be patchable from the 2nd factor,
   * otherwise the 2nd factor becomes an identity-swap vector. Only descriptive profile claims are
   * allowed here. This mirrors {@code IdentityVerificationUserUpdater}'s allowlist, but is stricter
   * (email / phone_number excluded) because the 2nd-factor source is influenced by the submitted
   * credentials.
   */
  private static final Set<String> SECOND_FACTOR_ENRICHABLE_CLAIMS =
      Set.of(
          "name",
          "given_name",
          "family_name",
          "middle_name",
          "nickname",
          "profile",
          "picture",
          "website",
          "gender",
          "birthdate",
          "zoneinfo",
          "locale",
          "address");

  public PasswordAuthenticationInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationExecutors = authenticationExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.PASSWORD_AUTHENTICATION.toType();
  }

  public String method() {
    return StandardAuthenticationMethod.PASSWORD.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("PasswordAuthenticationInteractor called");

    AuthenticationConfiguration configuration = getConfig(tenant);
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("password-authentication");

    // Issue #1021: Extract username for user resolution on failure
    String username = request.optValueAsString("username", "");
    String providerId = request.optValueAsString("provider_id", "idp-server");

    // JSON Schema validation (Layer 2) - Issue #1008
    JsonSchemaDefinition schemaDefinition =
        authenticationInteractionConfig.request().requestSchemaAsDefinition();

    if (schemaDefinition.exists()) {
      JsonNodeWrapper requestNode = JsonNodeWrapper.fromMap(request.toMap());
      JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
      JsonSchemaValidationResult validationResult = validator.validate(requestNode);

      if (!validationResult.isValid()) {
        log.warn(
            "Password authentication request validation failed: error_count={}, errors={}",
            validationResult.errors().size(),
            validationResult.errors());

        // Issue #1021: Try to resolve user for security event logging
        // Issue #1396: prefer the authenticated session user (2nd factor) for accurate attribution
        User attemptedUser =
            transaction.hasUser()
                ? transaction.user()
                : tryResolveUserForLogging(tenant, username, providerId, userQueryRepository);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_request");
        errorResponse.put(
            "error_description", "The authentication request is invalid. Please check your input.");
        errorResponse.put("error_messages", validationResult.errors());

        return AuthenticationInteractionRequestResult.clientError(
            errorResponse,
            type,
            operationType(),
            method(),
            attemptedUser,
            DefaultSecurityEventType.password_failure);
      }
    }

    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    // Issue #1396: For 2nd factor (requires_user), the password must be verified against the
    // authenticated session user, not the request-supplied username (prevents identifier
    // switching). Mirrors EmailAuthenticationChallengeInteractor#resolveEmail, which ignores
    // request input on 2nd factor (cf. Issue #801).
    AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());
    boolean secondFactor = stepDefinition != null && stepDefinition.requiresUser();

    // Reject 2nd factor without an authenticated user before running verification, so the executor
    // does not needlessly verify a request-supplied account (cf. resolveEmail empty-email path).
    if (secondFactor && !transaction.hasUser()) {
      log.warn(
          "2nd factor requires authenticated user but transaction has no user. method={}",
          method());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "user_not_found");
      errorResponse.put("error_description", "User not found.");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.password_failure);
    }

    Map<String, Object> executionRequestValues = new HashMap<>(request.toMap());
    if (secondFactor) {
      // hasUser() is guaranteed true here (rejected above otherwise).
      User authenticatedUser = transaction.user();
      executionRequestValues.put("username", authenticatedUser.preferredUsername());
      executionRequestValues.put("provider_id", authenticatedUser.providerId());
      log.debug(
          "2nd factor: binding password verification to authenticated user. sub={}",
          authenticatedUser.sub());
    }

    AuthenticationExecutionRequest executionRequest =
        new AuthenticationExecutionRequest(executionRequestValues);
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant, transaction.identifier(), executionRequest, requestAttributes, execution);

    AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
    Map<String, Object> contents;
    if (responseConfig.bodyMappingRules().isEmpty()) {
      contents = executionResult.contents();
    } else {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);
    }

    if (!executionResult.isSuccess()) {
      log.warn(
          "Password authentication failed. status={}, contents={}",
          executionResult.statusCode(),
          executionResult.contents());
      // Issue #1021: Try to resolve user for security event logging
      // Issue #1396: prefer the authenticated session user (2nd factor) for accurate attribution
      User attemptedUser =
          transaction.hasUser()
              ? transaction.user()
              : tryResolveUserForLogging(tenant, username, providerId, userQueryRepository);
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          attemptedUser,
          DefaultSecurityEventType.password_failure);
    }

    User verifiedUser =
        resolveUser(
            tenant, transaction, request, executionResult, configuration, userQueryRepository);

    if (!verifiedUser.exists()) {
      log.warn(
          "User resolution failed. username={}, method={}",
          request.optValueAsString("username", ""),
          method());

      Map<String, Object> response = new HashMap<>();
      response.put("error", "user_not_found");
      response.put("error_description", "User not found.");

      return AuthenticationInteractionRequestResult.clientError(
          response, type, operationType(), method(), DefaultSecurityEventType.password_failure);
    }

    log.debug("Password authentication succeeded for user: {}", verifiedUser.sub());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("user", verifiedUser.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        verifiedUser,
        responseContents,
        DefaultSecurityEventType.password_success);
  }

  private AuthenticationConfiguration getConfig(Tenant tenant) {

    AuthenticationConfiguration configuration =
        configurationQueryRepository.find(tenant, "password");

    if (configuration.exists()) {
      log.info("Using configuration-based password authentication");
      return configuration;
    }

    log.info("Using default config password authentication");
    return createDefaultConfiguration();
  }

  /**
   * Create default password authentication configuration with JSON Schema validation.
   *
   * <p><b>Issue #1008:</b> Added JSON Schema for request validation (Layer 2).
   *
   * <p><b>Default Schema:</b>
   *
   * <ul>
   *   <li>username: required, string, minLength=1, maxLength=256
   *   <li>password: required, string, minLength=1, maxLength=128
   *   <li>provider_id: optional, string, maxLength=100
   * </ul>
   *
   * @return default password authentication configuration
   */
  private AuthenticationConfiguration createDefaultConfiguration() {
    String config =
        """
              {
                "id": "",
                "type": "password",
                "attributes": {
                  "description": "default password configuration"
                },
                "metadata": {},
                "interactions": {
                  "password-authentication": {
                    "request": {
                      "schema": {
                        "type": "object",
                        "required": ["username", "password"],
                        "properties": {
                          "username": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 256
                          },
                          "password": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 128
                          },
                          "provider_id": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 100
                          }
                        }
                      }
                    },
                    "execution": {
                      "function": "password_verification"
                    },
                    "user_resolve": {},
                    "response": {
                      "body_mapping_rules": []
                    }
                  }
                }
              }
              """;

    return jsonConverter.read(config, AuthenticationConfiguration.class);
  }

  /**
   * Resolve user for password authentication (1st or 2nd factor).
   *
   * <p><b>Issue #800 Fix:</b> Search database by input identifier FIRST.
   *
   * <p><b>Issue #897:</b> Uses preferred_username for user lookup.
   *
   * <p><b>Issue #898:</b> Support external authentication service with user mapping.
   *
   * <p><b>Resolution Logic:</b>
   *
   * <ol>
   *   <li>2nd factor: Return authenticated user from transaction (no DB search)
   *   <li>1st factor with userResolve config: Map external response to User, search by
   *       externalUserId
   *   <li>1st factor without userResolve: Search database by preferred_username (Issue #800 fix)
   *   <li>Return User.notFound() if not found (password authentication does not create new users)
   * </ol>
   *
   * @param tenant tenant
   * @param transaction authentication transaction
   * @param request authentication interaction request
   * @param executionResult authentication execution result
   * @param configuration authentication configuration (may not exist)
   * @param userQueryRepository user query repository
   * @return resolved user
   */
  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionRequest request,
      AuthenticationExecutionResult executionResult,
      AuthenticationConfiguration configuration,
      UserQueryRepository userQueryRepository) {

    String username = request.optValueAsString("username", "");
    String providerId = request.optValueAsString("provider_id", "idp-server");

    AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());
    // New user creation decision (Issue #1538: align with Email/SMS allowRegistration)
    boolean allowRegistration =
        stepDefinition != null && stepDefinition.allowRegistration(); // default: disabled

    // SECURITY: 2nd factor requires authenticated user (prevent authentication bypass)
    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (!transaction.hasUser()) {
        log.warn(
            "2nd factor requires authenticated user but transaction has no user. method={}, username={}",
            method(),
            username);
        return User.notFound();
      }

      // 2nd factor with user_resolve: enrich the authenticated user with non-identifying
      // attributes only. SECURITY (CWE-287): we do NOT re-resolve identity from the submitted
      // credentials here; the session user established by the 1st factor is authoritative.
      if (configuration.exists() && executionResult.isSuccess()) {
        AuthenticationInteractionConfig interactionConfig =
            configuration.getAuthenticationConfig("password-authentication");
        List<MappingRule> userMappingRules = interactionConfig.userResolve().userMappingRules();

        if (!userMappingRules.isEmpty()) {
          log.debug(
              "2nd factor: enriching authenticated user with user_resolve attributes. method={}, sub={}",
              method(),
              transaction.user().sub());
          User enrichmentPatch =
              buildSecondFactorEnrichmentPatch(request, executionResult, userMappingRules);
          return transaction.user().updateWith(enrichmentPatch);
        }
      }

      log.debug(
          "2nd factor: returning authenticated user. method={}, sub={}",
          method(),
          transaction.user().sub());
      return transaction.user();
    }

    // === 1st factor user identification ===

    // External authentication with userResolve mapping
    if (configuration.exists() && executionResult.isSuccess()) {
      AuthenticationInteractionConfig interactionConfig =
          configuration.getAuthenticationConfig("password-authentication");

      if (!interactionConfig.userResolve().userMappingRules().isEmpty()) {
        return resolveUserFromExternalAuth(
            tenant,
            providerId,
            request,
            executionResult,
            interactionConfig.userResolve().userMappingRules(),
            allowRegistration,
            userQueryRepository);
      }
    }

    // Database search (Issue #800 fix, Issue #897: use preferred_username)
    User existingUser = userQueryRepository.findByPreferredUsername(tenant, providerId, username);
    if (existingUser.exists()) {
      log.debug("User found in database. username={}, sub={}", username, existingUser.sub());
      return existingUser;
    }

    log.warn("User not found in database. username={}", username);
    return User.notFound();
  }

  /**
   * Builds an enrichment patch for a 2nd-factor authenticated user.
   *
   * <p>The configured {@code user_mapping_rules} are applied to the mapping source (request body +
   * execution results), then filtered so that only {@link #SECOND_FACTOR_ENRICHABLE_CLAIMS} and
   * {@code custom_properties} survive. Any rule output targeting an identifier, lifecycle or
   * privilege field is dropped and logged at WARN — on the 2nd factor these are inviolable and the
   * identity-resolution rules (e.g. {@code external_user_id} / {@code provider_id} / {@code email})
   * are intentionally not honored (SECURITY, CWE-287). The returned patch never carries {@code
   * sub}, so {@link User#updateWith(User)} keeps the 1st-factor identity intact.
   *
   * <p>Package-private for unit testing.
   */
  User buildSecondFactorEnrichmentPatch(
      AuthenticationInteractionRequest request,
      AuthenticationExecutionResult executionResult,
      List<MappingRule> userMappingRules) {

    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", request.toMap());
    mappingSource.putAll(executionResult.contents());

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(mappingSource);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mapped = MappingRuleObjectMapper.execute(userMappingRules, jsonPath);

    Map<String, Object> enrichable = new HashMap<>();
    List<String> ignored = new ArrayList<>();
    for (Map.Entry<String, Object> entry : mapped.entrySet()) {
      String key = entry.getKey();
      if (SECOND_FACTOR_ENRICHABLE_CLAIMS.contains(key) || key.equals("custom_properties")) {
        enrichable.put(key, entry.getValue());
      } else {
        ignored.add(key);
      }
    }

    if (!ignored.isEmpty()) {
      log.warn(
          "2nd factor user_resolve ignored non-enrichable mapping targets (identifiers, lifecycle "
              + "and privileges are inviolable on the 2nd factor): {}. Use the 1st factor to resolve "
              + "identity, or custom_properties for auxiliary data.",
          ignored);
    }

    return jsonConverter.read(enrichable, User.class);
  }

  /**
   * Resolve user from external authentication service response.
   *
   * <p>This method follows the same pattern as ExternalTokenAuthenticationInteractor:
   *
   * <ol>
   *   <li>Map external response to User object using userMappingRules
   *   <li>Search existing user by provider + externalUserId
   *   <li>If found: reuse sub and status
   *   <li>If not found and allowRegistration: assign new sub (UUID)
   *   <li>If not found and !allowRegistration (Issue #1538): return User.notFound() so the caller
   *       rejects authentication instead of JIT-creating an orphan user
   * </ol>
   *
   * @param tenant tenant
   * @param providerId provider ID
   * @param request authentication interaction request
   * @param executionResult authentication execution result
   * @param userMappingRules user mapping rules from configuration
   * @param allowRegistration whether the current policy step permits new user creation
   * @param userQueryRepository user query repository
   * @return resolved user
   * @see
   *     org.idp.server.authentication.interactors.external_token.ExternalTokenAuthenticationInteractor#interact
   */
  private User resolveUserFromExternalAuth(
      Tenant tenant,
      String providerId,
      AuthenticationInteractionRequest request,
      AuthenticationExecutionResult executionResult,
      List<MappingRule> userMappingRules,
      boolean allowRegistration,
      UserQueryRepository userQueryRepository) {

    // Prepare mapping source (same as ExternalTokenAuthenticationInteractor)
    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", request.toMap());
    mappingSource.putAll(executionResult.contents());

    // Map to User object
    User user = toUser(userMappingRules, mappingSource);

    // Search existing user by provider + externalUserId
    User existingUser =
        userQueryRepository.findByProvider(tenant, user.providerId(), user.externalUserId());

    if (existingUser.exists()) {
      log.debug(
          "Existing user found by externalUserId. providerId={}, externalUserId={}, sub={}",
          user.providerId(),
          user.externalUserId(),
          existingUser.sub());
      user.setSub(existingUser.sub());
      user.setStatus(existingUser.status());
    } else {
      // Issue #1538: enforce allow_registration. Do not JIT-create a user when the policy step
      // disallows registration, even if the external auth API returned success.
      if (!allowRegistration) {
        log.warn(
            "User not found and registration disabled. providerId={}, externalUserId={}, allowRegistration=false",
            user.providerId(),
            user.externalUserId());
        return User.notFound();
      }
      log.debug(
          "New user from external auth. providerId={}, externalUserId={}",
          user.providerId(),
          user.externalUserId());
      user.setSub(UUID.randomUUID().toString());
      if (!user.hasStatus()) {
        user.setStatus(UserStatus.INITIALIZED);
      }
    }

    return user;
  }

  /**
   * Convert execution result to User object using mapping rules.
   *
   * <p>This method maps external authentication service response to User object following the same
   * pattern as ExternalTokenAuthenticationInteractor.
   *
   * @param mappingRules user mapping rules from configuration
   * @param results combined request and execution results
   * @return mapped User object
   */
  private User toUser(List<MappingRule> mappingRules, Map<String, Object> results) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(results);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPath);

    return jsonConverter.read(executed, User.class);
  }

  /**
   * Attempts to resolve user from username for security event logging.
   *
   * <p><b>Issue #1021:</b> This method is used to attach user information to authentication failure
   * security events. It returns null if the user cannot be resolved (e.g., user doesn't exist),
   * which is acceptable for logging purposes.
   *
   * @param tenant the tenant
   * @param username the username from the authentication request
   * @param providerId the provider ID
   * @param userQueryRepository the user query repository
   * @return the resolved user, or null if not found
   */
  private User tryResolveUserForLogging(
      Tenant tenant, String username, String providerId, UserQueryRepository userQueryRepository) {
    if (username == null || username.isEmpty()) {
      return null;
    }

    try {
      User user = userQueryRepository.findByPreferredUsername(tenant, providerId, username);
      if (user.exists()) {
        log.debug(
            "User resolved for security event logging. username={}, sub={}", username, user.sub());
        return user;
      }
    } catch (Exception e) {
      log.debug("Failed to resolve user for security event logging. username={}", username);
    }

    return null;
  }
}
