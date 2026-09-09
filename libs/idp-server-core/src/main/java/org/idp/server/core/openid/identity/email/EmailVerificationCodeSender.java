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

package org.idp.server.core.openid.identity.email;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Sends a one-time code to an address, using whatever the tenant configured (Issue #1416).
 *
 * <p>A port so the domain service stays free of the sender plumbing: reading the tenant's email
 * configuration and picking an SMTP / HTTP sender is infrastructure, and the configuration type
 * itself lives outside this module.
 */
public interface EmailVerificationCodeSender {

  /**
   * @return how long the issued code stays valid, so the caller can build the challenge
   */
  int expireSeconds(Tenant tenant);

  /** Maximum wrong attempts before a challenge is discarded. */
  int retryCountLimitation(Tenant tenant);

  /**
   * @return true when the code was accepted for delivery
   */
  boolean send(
      Tenant tenant,
      EmailVerificationOperation operation,
      String targetEmail,
      String verificationCode);
}
