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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.session.repository.ClientSessionRepository;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OIDCSessionService {

  private final OPSessionRepository opSessionRepository;
  private final ClientSessionRepository clientSessionRepository;

  public OIDCSessionService(
      OPSessionRepository opSessionRepository, ClientSessionRepository clientSessionRepository) {
    this.opSessionRepository = opSessionRepository;
    this.clientSessionRepository = clientSessionRepository;
  }

  public OPSession createOPSession(
      Tenant tenant,
      User user,
      Instant authTime,
      String acr,
      List<String> amr,
      Map<String, Map<String, Object>> interactionResults,
      long sessionTimeoutSeconds,
      String ipAddress,
      String userAgent) {
    OPSession session =
        OPSession.create(
            tenant.identifier(),
            user,
            authTime,
            acr,
            amr,
            interactionResults,
            sessionTimeoutSeconds,
            ipAddress,
            userAgent);
    opSessionRepository.register(tenant, session);
    return session;
  }

  public Optional<OPSession> getOPSession(Tenant tenant, OPSessionIdentifier sessionId) {
    return opSessionRepository.findById(tenant, sessionId);
  }

  public void touchOPSession(Tenant tenant, OPSession session) {
    OPSession touched = session.touch();
    opSessionRepository.updateLastAccessedAt(tenant, touched);
  }

  public ClientSessions terminateOPSession(
      Tenant tenant, OPSessionIdentifier sessionId, TerminationReason reason) {
    Optional<OPSession> sessionOpt = opSessionRepository.findById(tenant, sessionId);
    if (sessionOpt.isEmpty()) {
      return ClientSessions.empty();
    }

    ClientSessions clientSessions = clientSessionRepository.findByOpSessionId(tenant, sessionId);

    // Terminate all client sessions
    for (ClientSession clientSession : clientSessions) {
      clientSession.terminate(TerminationReason.PARENT_TERMINATED);
    }

    // Delete sessions from Redis
    clientSessionRepository.deleteByOpSessionId(tenant, sessionId);
    opSessionRepository.delete(tenant, sessionId);

    return clientSessions;
  }

  public ClientSession createClientSession(
      Tenant tenant,
      OPSession opSession,
      String clientId,
      Set<String> scope,
      Map<String, Object> claims,
      String nonce,
      long sessionTimeoutSeconds) {
    ClientSession session =
        ClientSession.create(opSession, clientId, scope, claims, nonce, sessionTimeoutSeconds);
    clientSessionRepository.register(tenant, session);
    return session;
  }

  /**
   * Gets existing ClientSession or creates a new one.
   *
   * <p>If a ClientSession already exists for the same OPSession and clientId, it will be refreshed
   * with the new authorization context. Otherwise, a new ClientSession will be created.
   *
   * @param tenant the tenant
   * @param opSession the OP session
   * @param clientId the client ID
   * @param scope the authorized scopes
   * @param claims the claims
   * @param nonce the nonce from authorization request
   * @param sessionTimeoutSeconds the session timeout in seconds
   * @return the ClientSession (existing refreshed or newly created)
   */
  public ClientSession getOrCreateClientSession(
      Tenant tenant,
      OPSession opSession,
      String clientId,
      Set<String> scope,
      Map<String, Object> claims,
      String nonce,
      long sessionTimeoutSeconds) {
    Optional<ClientSession> existingSession =
        clientSessionRepository.findByOpSessionIdAndClientId(tenant, opSession.id(), clientId);

    if (existingSession.isPresent() && existingSession.get().isActive()) {
      ClientSession session = existingSession.get();
      session.refresh(scope, claims, nonce, sessionTimeoutSeconds);
      clientSessionRepository.update(tenant, session);
      return session;
    }

    return createClientSession(
        tenant, opSession, clientId, scope, claims, nonce, sessionTimeoutSeconds);
  }

  public Optional<ClientSession> getClientSession(Tenant tenant, ClientSessionIdentifier sid) {
    return clientSessionRepository.findBySid(tenant, sid);
  }

  public ClientSessions getClientSessionsByOPSession(
      Tenant tenant, OPSessionIdentifier opSessionId) {
    return clientSessionRepository.findByOpSessionId(tenant, opSessionId);
  }

  public ClientSessions getClientSessionsBySub(TenantIdentifier tenantId, String sub) {
    return clientSessionRepository.findByTenantAndSub(tenantId, sub);
  }

  public ClientSessions getClientSessionsByClientAndSub(
      TenantIdentifier tenantId, String clientId, String sub) {
    return clientSessionRepository.findByTenantClientAndSub(tenantId, clientId, sub);
  }

  public void terminateClientSession(
      Tenant tenant, ClientSessionIdentifier sid, TerminationReason reason) {
    Optional<ClientSession> sessionOpt = clientSessionRepository.findBySid(tenant, sid);
    if (sessionOpt.isPresent()) {
      sessionOpt.get().terminate(reason);
      clientSessionRepository.deleteBySid(tenant, sid);
    }
  }

  public Optional<String> getBrowserState(Tenant tenant, OPSessionIdentifier opSessionId) {
    return opSessionRepository.findById(tenant, opSessionId).map(s -> s.browserState().value());
  }
}
