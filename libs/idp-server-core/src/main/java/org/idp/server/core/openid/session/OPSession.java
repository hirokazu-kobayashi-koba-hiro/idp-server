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
import java.util.List;
import java.util.Objects;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OPSession implements Serializable {

  private OPSessionIdentifier id;
  private TenantIdentifier tenantId;
  private String sub;
  private Instant authTime;
  private String acr;
  private List<String> amr;
  private BrowserState browserState;
  private Instant createdAt;
  private Instant expiresAt;
  private Instant lastAccessedAt;
  private SessionStatus status;
  private Instant terminatedAt;
  private TerminationReason terminationReason;

  public OPSession() {
    this.id = new OPSessionIdentifier();
    this.status = SessionStatus.ACTIVE;
  }

  public OPSession(
      OPSessionIdentifier id,
      TenantIdentifier tenantId,
      String sub,
      Instant authTime,
      String acr,
      List<String> amr,
      BrowserState browserState,
      Instant createdAt,
      Instant expiresAt,
      Instant lastAccessedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.sub = sub;
    this.authTime = authTime;
    this.acr = acr;
    this.amr = amr;
    this.browserState = browserState;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.lastAccessedAt = lastAccessedAt;
    this.status = SessionStatus.ACTIVE;
  }

  public static OPSession create(
      TenantIdentifier tenantId,
      String sub,
      Instant authTime,
      String acr,
      List<String> amr,
      long sessionTimeoutSeconds) {
    Instant now = Instant.now();
    return new OPSession(
        OPSessionIdentifier.generate(),
        tenantId,
        sub,
        authTime,
        acr,
        amr,
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
    return sub;
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
    if (status == SessionStatus.EXPIRED || status == SessionStatus.TERMINATED) {
      return true;
    }
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isActive() {
    return status == SessionStatus.ACTIVE && !isExpired();
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
