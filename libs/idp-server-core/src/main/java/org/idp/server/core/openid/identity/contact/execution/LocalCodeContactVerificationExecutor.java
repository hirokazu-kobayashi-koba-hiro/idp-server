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

package org.idp.server.core.openid.identity.contact.execution;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Compares a submitted code against the one on the challenge row (Issue #1416).
 *
 * <p>Registered under the {@code email_authentication} / {@code sms_authentication} functions that
 * a locally generating tenant already writes in {@code interactions.{channel}-authentication}. The
 * function name is injected rather than hard-coded so one implementation serves both: the
 * comparison has nothing channel-specific in it, and two classes differing only in a string is how
 * the two drift apart later.
 *
 * <p>Constant-time comparison is unnecessary: the code is single-use and attempt-capped.
 */
public class LocalCodeContactVerificationExecutor implements ContactVerificationExecutor {

  String function;

  public LocalCodeContactVerificationExecutor(String function) {
    this.function = function;
  }

  @Override
  public String function() {
    return function;
  }

  @Override
  public ContactExecutionResult execute(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    String submitted = request.optValueAsString("verification_code", "");

    if (!challenge.matches(submitted)) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_request");
      contents.put("error_description", "verification code is unmatched.");
      return ContactExecutionResult.clientError(contents);
    }

    return ContactExecutionResult.success(new HashMap<>());
  }
}
