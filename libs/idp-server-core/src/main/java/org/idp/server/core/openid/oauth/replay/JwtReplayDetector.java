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

package org.idp.server.core.openid.oauth.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Lets a short-lived JWT through once (Issue #1893): the DPoP proof (RFC 9449 Section 11.1) and the
 * Client Attestation PoP JWT (draft-ietf-oauth-attestation-based-client-auth Section 12.1).
 *
 * <p>Each accepted {@code jti} is recorded in the cache store (Redis {@code SET NX EX}) for as long
 * as the JWT could still be accepted by its {@code iat} window. The cache is written outside the
 * database transaction, so the check also works on read-only paths such as userinfo and
 * introspection.
 *
 * <p>The key holds what the JWT is bound to — the key thumbprint of a DPoP proof, the client of a
 * PoP — so that one client cannot use up the {@code jti} values of another.
 *
 * <p>Where no cache store is configured, or it fails, nothing is recorded and the {@code iat}
 * window alone limits reuse; the cache store logs the failure.
 */
public class JwtReplayDetector {

  static final LoggerWrapper log = LoggerWrapper.getLogger(JwtReplayDetector.class);
  static final AtomicBoolean warnedNoStore = new AtomicBoolean(false);
  static final String KEY_PREFIX = "jti_replay";

  CacheStore cacheStore;

  public JwtReplayDetector(CacheStore cacheStore) {
    this.cacheStore = cacheStore;
  }

  /**
   * Records {@code jti} unless it was seen before.
   *
   * @param kind what the JWT is, e.g. {@code dpop}
   * @param binding what the JWT is bound to: the key thumbprint, or the client
   * @param issuedAt the JWT's {@code iat}
   * @param window how far {@code iat} may be from now for the JWT to be accepted
   * @return {@code true} the first time, {@code false} when the same JWT comes again
   */
  public boolean firstUse(
      Tenant tenant,
      JwtReplayKind kind,
      String binding,
      String jti,
      Instant issuedAt,
      Duration window) {
    if (cacheStore instanceof NoOperationCacheStore) {
      if (warnedNoStore.compareAndSet(false, true)) {
        log.warn(
            "No cache store is configured: JWT replay detection (jti) is off, and only the iat window limits reuse.");
      }
      return true;
    }
    return cacheStore.putIfAbsent(
        key(tenant, kind, binding, jti), timeToLiveSeconds(issuedAt, window));
  }

  /**
   * As long as the JWT could still be accepted: until {@code iat + window}. At least one second, so
   * that a JWT accepted at the very edge of the window is still recorded.
   */
  static int timeToLiveSeconds(Instant issuedAt, Duration window) {
    long seconds = Duration.between(Instant.now(), issuedAt.plus(window)).toSeconds();
    return (int) Math.max(1, Math.min(seconds, Integer.MAX_VALUE));
  }

  static String key(Tenant tenant, JwtReplayKind kind, String binding, String jti) {
    return String.join(
        ":", KEY_PREFIX, tenant.identifierValue(), kind.value(), sha256(binding + "\n" + jti));
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
