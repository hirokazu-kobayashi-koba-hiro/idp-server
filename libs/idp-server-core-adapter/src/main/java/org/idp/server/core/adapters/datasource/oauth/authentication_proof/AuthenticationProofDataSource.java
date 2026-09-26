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

package org.idp.server.core.adapters.datasource.oauth.authentication_proof;

import org.idp.server.core.openid.oauth.AuthenticationProof;
import org.idp.server.core.openid.oauth.repository.AuthenticationProofRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * Keeps proofs in the cache.
 *
 * <p>Nothing here outlives the few seconds between authenticating and arriving back at the client,
 * so there is no table. What the cache does have to provide is an atomic operation, and {@code
 * increment} is the one: a proof is claimed by being the caller that saw the count go to one.
 * Reading the value and then deleting it would let two requests arriving together both read it
 * before either removed it.
 */
public class AuthenticationProofDataSource implements AuthenticationProofRepository {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationProofDataSource.class);

  /** How long the browser has to make the next call. Seconds, not minutes. */
  static final int TTL_SECONDS = 120;

  CacheStore cacheStore;

  public AuthenticationProofDataSource(CacheStore cacheStore) {
    this.cacheStore = cacheStore;
  }

  @Override
  public String issue(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier, String sub) {
    return put(tenant, new AuthenticationProof(authorizationRequestIdentifier.value(), sub, null));
  }

  @Override
  public String issueForCompletion(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      String sub,
      String redirectUri) {
    return put(
        tenant, new AuthenticationProof(authorizationRequestIdentifier.value(), sub, redirectUri));
  }

  @Override
  public AuthenticationProof claim(Tenant tenant, String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    String key = key(tenant, value);

    long claim = cacheStore.increment(claimedKey(key), TTL_SECONDS);
    if (claim != 1) {
      // Do not remove the proof here. The caller that did take the claim has not read it yet, and
      // removing it now would fail them too — a double-clicked button would send the person back
      // to the start. Reuse is already prevented by the counter itself.
      log.warn("auth_proof was already claimed or could not be claimed. claim:{}", claim);
      return null;
    }

    AuthenticationProof found = cacheStore.find(key, AuthenticationProof.class).orElse(null);
    cacheStore.delete(key);
    return found;
  }

  private String put(Tenant tenant, AuthenticationProof proof) {
    String value = new RandomStringGenerator(32).generate();
    cacheStore.put(key(tenant, value), proof, TTL_SECONDS);
    return value;
  }

  private String key(Tenant tenant, String value) {
    return String.format("authentication_proof:%s:%s", tenant.identifier().value(), value);
  }

  private String claimedKey(String key) {
    return key + ":claimed";
  }
}
