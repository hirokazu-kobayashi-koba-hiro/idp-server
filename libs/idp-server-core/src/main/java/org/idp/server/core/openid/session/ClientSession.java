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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ClientSession implements Serializable {

  private ClientSessionIdentifier sid;
  private OPSessionIdentifier opSessionId;
  private TenantIdentifier tenantId;
  private String clientId;
  private String sub;
  private Set<String> scope;
  private Map<String, Object> claims;
  private String nonce;
  private Instant authTime;
  private Instant createdAt;
  private Instant expiresAt;
  private SessionStatus status;
  private Instant terminatedAt;
  private TerminationReason terminationReason;

  public ClientSession() {
    this.sid = new ClientSessionIdentifier();
    this.status = SessionStatus.ACTIVE;
  }

  public ClientSession(
      ClientSessionIdentifier sid,
      OPSessionIdentifier opSessionId,
      TenantIdentifier tenantId,
      String clientId,
      String sub,
      Set<String> scope,
      Map<String, Object> claims,
      String nonce,
      Instant authTime,
      Instant createdAt,
      Instant expiresAt) {
    this.sid = sid;
    this.opSessionId = opSessionId;
    this.tenantId = tenantId;
    this.clientId = clientId;
    this.sub = sub;
    this.scope = scope;
    this.claims = claims;
    this.nonce = nonce;
    this.authTime = authTime;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.status = SessionStatus.ACTIVE;
  }

  public static ClientSession create(
      OPSession opSession,
      String clientId,
      Set<String> scope,
      Map<String, Object> claims,
      String nonce,
      long sessionTimeoutSeconds) {
    Instant now = Instant.now();
    return new ClientSession(
        ClientSessionIdentifier.generate(),
        opSession.id(),
        opSession.tenantId(),
        clientId,
        opSession.sub(),
        scope,
        claims,
        nonce,
        opSession.authTime(),
        now,
        now.plusSeconds(sessionTimeoutSeconds));
  }

  public ClientSessionIdentifier sid() {
    return sid;
  }

  public OPSessionIdentifier opSessionId() {
    return opSessionId;
  }

  public TenantIdentifier tenantId() {
    return tenantId;
  }

  public String clientId() {
    return clientId;
  }

  public String sub() {
    return sub;
  }

  public Set<String> scope() {
    return scope;
  }

  public Map<String, Object> claims() {
    return claims;
  }

  public String nonce() {
    return nonce;
  }

  public Instant authTime() {
    return authTime;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
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
    return sid != null && sid.exists();
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

  public ClientSession terminate(TerminationReason reason) {
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
    ClientSession that = (ClientSession) o;
    return Objects.equals(sid, that.sid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sid);
  }
}
