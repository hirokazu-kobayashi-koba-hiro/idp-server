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

package org.idp.server.core.openid.session;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OPSession implements Serializable {

  private OPSessionIdentifier id;
  private TenantIdentifier tenantId;
  private User user;
  private Instant authTime;
  private String acr;
  private List<String> amr;
  private Map<String, Map<String, Object>> interactionResults;
  private BrowserState browserState;
  private Instant createdAt;
  private Instant expiresAt;
  private Instant lastAccessedAt;
  private SessionStatus status;
  private Instant terminatedAt;
  private TerminationReason terminationReason;

  /** Default constructor for JSON deserialization. */
  public OPSession() {
    this.id = new OPSessionIdentifier();
  }

  public OPSession(
      OPSessionIdentifier id,
      TenantIdentifier tenantId,
      User user,
      Instant authTime,
      String acr,
      List<String> amr,
      Map<String, Map<String, Object>> interactionResults,
      BrowserState browserState,
      Instant createdAt,
      Instant expiresAt,
      Instant lastAccessedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.user = user;
    this.authTime = authTime;
    this.acr = acr;
    this.amr = amr;
    this.interactionResults = interactionResults;
    this.browserState = browserState;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.lastAccessedAt = lastAccessedAt;
    this.status = SessionStatus.ACTIVE;
  }

  public static OPSession create(
      TenantIdentifier tenantId,
      User user,
      Instant authTime,
      String acr,
      List<String> amr,
      Map<String, Map<String, Object>> interactionResults,
      long sessionTimeoutSeconds) {
    Instant now = Instant.now();
    return new OPSession(
        OPSessionIdentifier.generate(),
        tenantId,
        user,
        authTime,
        acr,
        amr,
        interactionResults,
        BrowserState.generate(),
        now,
        now.plusSeconds(sessionTimeoutSeconds),
        now);
  }

  public OPSessionIdentifier id() {
    return id;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public String sub() {
    return user != null ? user.sub() : null;
  }

  public UserIdentifier userIdentifier() {
    return user != null ? new UserIdentifier(user.sub()) : new UserIdentifier();
  }

  public User user() {
    return user;
  }

  public Instant authTime() {
    return authTime;
  }

  public String acr() {
    return acr;
  }

  public List<String> amr() {
    return amr;
  }

  public Map<String, Map<String, Object>> interactionResults() {
    return interactionResults != null ? interactionResults : new HashMap<>();
  }

  public boolean hasInteractionResults() {
    return interactionResults != null && !interactionResults.isEmpty();
  }

  public AuthenticationInteractionResults toAuthenticationInteractionResults() {
    if (interactionResults == null || interactionResults.isEmpty()) {
      return new AuthenticationInteractionResults();
    }
    return AuthenticationInteractionResults.fromMap(interactionResults);
  }

  public Authentication authentication() {
    return new Authentication()
        .setTime(authTime.atZone(ZoneOffset.UTC).toLocalDateTime())
        .addAcr(acr != null ? acr : "")
        .addMethods(amr != null ? amr : List.of());
  }

  public BrowserState browserState() {
    return browserState;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant lastAccessedAt() {
    return lastAccessedAt;
  }

  public SessionStatus status() {
    return status;
  }

  public Instant terminatedAt() {
    return terminatedAt;
  }

  public TerminationReason terminationReason() {
    return terminationReason;
  }

  public boolean exists() {
    return id != null && id.exists();
  }

  public boolean isExpired() {
    if (status == null || expiresAt == null) {
      return true;
    }
    if (status == SessionStatus.EXPIRED || status == SessionStatus.TERMINATED) {
      return true;
    }
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isActive() {
    return status != null && status == SessionStatus.ACTIVE && !isExpired();
  }

  public OPSession touch() {
    this.lastAccessedAt = Instant.now();
    return this;
  }

  public OPSession terminate(TerminationReason reason) {
    this.status = SessionStatus.TERMINATED;
    this.terminatedAt = Instant.now();
    this.terminationReason = reason;
    return this;
  }

  public long ttlSeconds() {
    long ttl = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    return Math.max(ttl, 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OPSession opSession = (OPSession) o;
    return Objects.equals(id, opSession.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
