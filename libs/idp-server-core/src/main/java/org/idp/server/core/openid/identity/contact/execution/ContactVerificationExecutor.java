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

import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Runs one step of a self-service contact exchange, selected by {@code execution.function} (Issue
 * #1416).
 *
 * <p>Deliberately the same shape as {@code AuthenticationExecutor}: a tenant describes the step in
 * the same {@code interactions.{key}.execution} block it already writes for login, and the
 * implementation is chosen by the same {@code function} value. Nothing infers the mode from the
 * presence or absence of a key — {@code email_authentication_challenge} names a locally generated
 * code, {@code http_request} names a delegated exchange, and a configuration naming neither simply
 * has no executor rather than falling into whichever branch was written first.
 *
 * <p>The one difference from the authentication SPI is the second argument. {@code
 * AuthenticationExecutor} keys its state on {@code AuthenticationTransactionIdentifier}; this
 * feature deliberately has no authentication transaction — that absence is what keeps it off the
 * unauthenticated interaction endpoints — so the challenge itself is passed instead. At challenge
 * time it does not exist yet ({@link ContactVerificationChallenge#exists()} is false); at
 * verification time it carries the code or the external reference the earlier step produced.
 */
public interface ContactVerificationExecutor {

  /** The {@code execution.function} value this executor is registered under. */
  String function();

  ContactExecutionResult execute(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration);
}
