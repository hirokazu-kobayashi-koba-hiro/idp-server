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

package org.idp.server.core.extension.identity.verification.application.pre_hook.verification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.AssertionPreHook;
import org.idp.server.core.extension.identity.verification.application.pre_hook.AssertionRule;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Config-driven verifier that denies the application when one or more declared assertions do not
 * hold. Each assertion is a {@link org.idp.server.platform.mapper.ConditionSpec} evaluated against
 * the verifier context (the same {@code $.user} / {@code $.application} / {@code
 * $.previous_applications} / {@code $.request_body} / {@code $.request_attributes} roots used by
 * the gate {@code condition}); an unsatisfied assertion contributes its message to the failure.
 *
 * <p>This generalizes "verify against past applications" — e.g. continuous customer due diligence —
 * into a declarative rule over {@code $.previous_applications}, superseding the dedicated CDD
 * verifier (#1268). Reuses the existing condition language, so no new operators are introduced.
 *
 * <p>Example {@code details}:
 *
 * <pre>{@code
 * {
 *   "assertions": [
 *     {
 *       "condition": {"operation": "eq", "path": "$.previous_applications[*].status", "value": "approved"},
 *       "message": "an approved past application is required"
 *     }
 *   ]
 * }
 * }</pre>
 */
public class AssertionVerifier implements IdentityVerificationApplicationRequestVerifier {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AssertionVerifier.class);

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public String type() {
    return "assert";
  }

  @Override
  public IdentityVerificationApplicationRequestVerifiedResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplication currentApplication,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationConfig verificationConfig,
      IdentityVerificationConfiguration verificationConfiguration) {

    AssertionPreHook assertionPreHook =
        jsonConverter.read(verificationConfig.details(), AssertionPreHook.class);
    List<AssertionRule> assertions = assertionPreHook.assertions();

    JsonPathWrapper jsonPath =
        createJsonPathContext(
            user, currentApplication, previousApplications, request, requestAttributes);

    List<String> errors = new ArrayList<>();
    for (AssertionRule assertion : assertions) {
      if (!assertion.hasCondition()) {
        // A misconfigured assertion (no condition) cannot deny anything; skip it loudly rather than
        // silently failing every request.
        log.warn(
            "Skipping assertion without a condition: type={}, process={}",
            type.name(),
            processes.name());
        continue;
      }
      if (!assertion.condition().evaluate(jsonPath)) {
        errors.add(assertion.message());
      }
    }

    if (!errors.isEmpty()) {
      log.info(
          "Assertion verification failed: type={}, process={}, error_count={}",
          type.name(),
          processes.name(),
          errors.size());
      return IdentityVerificationApplicationRequestVerifiedResult.failure(errors);
    }

    return IdentityVerificationApplicationRequestVerifiedResult.success();
  }

  private JsonPathWrapper createJsonPathContext(
      User user,
      IdentityVerificationApplication application,
      IdentityVerificationApplications previousApplications,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Map<String, Object> context = new HashMap<>();
    context.put("user", user.toMap());
    if (application != null && application.exists()) {
      context.put("application", application.toMap());
    }
    if (previousApplications != null) {
      context.put("previous_applications", previousApplications.toList());
    }
    context.put("request_body", request.toMap());
    context.put("request_attributes", requestAttributes.toMap());
    return new JsonPathWrapper(JsonNodeWrapper.fromMap(context).toJson());
  }
}
