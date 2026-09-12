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

package org.idp.server.core.openid.identity.contact;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Drives the one-time code exchange over the operation's channel (Issue #1416).
 *
 * <p>A port rather than a sender, because who owns the code depends on tenant configuration. Under
 * {@code execution.function: "http_request"} the tenant delegates generation, delivery and
 * verification to an external service, so there is no code to hand over — only an exchange to start
 * and later ask about. Naming this "send a code" would be a lie for half the tenants.
 */
public interface ContactVerificationGateway {

  /** Begins the exchange, delivering a code to {@code targetValue}. */
  ContactChallengeStart start(
      Tenant tenant, ContactVerificationOperation operation, String targetValue);

  /**
   * Decides whether the submitted code is the one that was delivered.
   *
   * <p>Local comparison when idp-server owns the code; a call to the external service when it does
   * not.
   */
  boolean verifyCode(
      Tenant tenant,
      ContactVerificationOperation operation,
      ContactVerificationChallenge challenge,
      String submittedCode);

  /**
   * @return how long the issued code stays valid, so the caller can build the challenge
   */
  int expireSeconds(Tenant tenant, ContactVerificationOperation operation);

  /** Maximum wrong attempts before a challenge is discarded. */
  int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation);

  /** Minimum interval between two sends for the same user and operation. */
  int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation);
}
