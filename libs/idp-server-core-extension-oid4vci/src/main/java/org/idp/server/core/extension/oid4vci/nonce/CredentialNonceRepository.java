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
package org.idp.server.core.extension.oid4vci.nonce;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Stores the {@code c_nonce} values the Nonce Endpoint issued.
 *
 * <p>Backed by the database rather than the cache: the nonce is a security control, and the cache
 * has a no-operation implementation that would accept every value.
 */
public interface CredentialNonceRepository {

  void register(Tenant tenant, CredentialNonce nonce);

  /**
   * Spends the nonce if it was issued for this tenant and has not expired.
   *
   * <p>Check and spend are one statement, so two requests carrying the same nonce cannot both
   * succeed.
   *
   * @return whether the nonce was valid (and is now spent)
   */
  boolean consume(Tenant tenant, String nonce);
}
