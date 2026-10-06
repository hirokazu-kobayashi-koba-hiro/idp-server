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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.session.repository.ClientSessionRepository;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * Issue #1912: re-authenticating in a browser that already has the same user's OP session reuses
 * that session, and the session has to record the new authentication — SSO decides on it and issues
 * ID Tokens from it afterwards.
 */
class OIDCSessionHandlerReauthenticationTest {

  private static final String SUB = "user-1912";
  private static final Instant FIRST_AUTH_TIME = Instant.parse("2026-10-06T00:00:00Z");
  private static final Instant SECOND_AUTH_TIME = Instant.parse("2026-10-06T01:00:00Z");

  private final InMemoryOPSessionRepository repository = new InMemoryOPSessionRepository();
  private final OIDCSessionHandler handler =
      new OIDCSessionHandler(
          new OIDCSessionService(repository, new UnusedClientSessionRepository()));

  @Test
  void reusedSessionRecordsTheNewAuthentication() {
    OPSession existing =
        storedSession(
            "urn:idp:acr:password",
            List.of("pwd"),
            Map.of("password-authentication", Map.of("success_count", 1)));

    Map<String, Map<String, Object>> newResults =
        Map.of(
            "password-authentication", Map.of("success_count", 1),
            "fido-uaf-authentication", Map.of("success_count", 1));
    OPSession reused =
        handler.onAuthenticationSuccess(
            new Tenant(),
            user(),
            authentication(SECOND_AUTH_TIME, "urn:idp:acr:mfa", List.of("pwd", "fido-uaf")),
            newResults,
            existing,
            null);

    assertEquals(existing.id().value(), reused.id().value());
    OPSession stored = repository.findById(new Tenant(), existing.id()).orElseThrow();
    assertEquals(SECOND_AUTH_TIME, stored.authTime());
    assertEquals("urn:idp:acr:mfa", stored.acr());
    assertEquals(List.of("pwd", "fido-uaf"), stored.amr());
    assertEquals(newResults.keySet(), stored.interactionResults().keySet());
  }

  /**
   * A weaker authentication replaces a stronger one rather than being merged with it: auth_time,
   * acr and amr must describe the same authentication.
   */
  @Test
  void weakerAuthenticationReplacesStrongerOne() {
    OPSession existing =
        storedSession(
            "urn:idp:acr:mfa",
            List.of("pwd", "fido-uaf"),
            Map.of(
                "password-authentication", Map.of("success_count", 1),
                "fido-uaf-authentication", Map.of("success_count", 1)));

    handler.onAuthenticationSuccess(
        new Tenant(),
        user(),
        authentication(SECOND_AUTH_TIME, "urn:idp:acr:password", List.of("pwd")),
        Map.of("password-authentication", Map.of("success_count", 1)),
        existing,
        null);

    OPSession stored = repository.findById(new Tenant(), existing.id()).orElseThrow();
    assertEquals(SECOND_AUTH_TIME, stored.authTime());
    assertEquals("urn:idp:acr:password", stored.acr());
    assertEquals(List.of("pwd"), stored.amr());
    assertFalse(stored.interactionResults().containsKey("fido-uaf-authentication"));
  }

  private OPSession storedSession(
      String acr, List<String> amr, Map<String, Map<String, Object>> interactionResults) {
    OPSession session =
        OPSession.create(
            new TenantIdentifier("tenant-1912"),
            user(),
            FIRST_AUTH_TIME,
            acr,
            amr,
            interactionResults,
            3600,
            "192.168.1.1",
            "Mozilla/5.0");
    repository.register(new Tenant(), session);
    return session;
  }

  private static User user() {
    return new User().setSub(SUB);
  }

  private static Authentication authentication(Instant time, String acr, List<String> methods) {
    return new Authentication()
        .setTime(LocalDateTime.ofInstant(time, ZoneOffset.UTC))
        .addAcr(acr)
        .addMethods(methods);
  }

  /** Keeps sessions as JSON, as the session store does, so what is asserted is what was stored. */
  static class InMemoryOPSessionRepository implements OPSessionRepository {

    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    Map<String, String> sessions = new HashMap<>();

    @Override
    public void register(Tenant tenant, OPSession session) {
      sessions.put(session.id().value(), jsonConverter.write(session));
    }

    @Override
    public Optional<OPSession> findById(Tenant tenant, OPSessionIdentifier id) {
      return Optional.ofNullable(sessions.get(id.value()))
          .map(json -> jsonConverter.read(json, OPSession.class));
    }

    @Override
    public OPSessions findByUser(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Tenant tenant, OPSessionIdentifier id) {
      sessions.remove(id.value());
    }

    @Override
    public void deleteByUser(Tenant tenant, UserIdentifier userIdentifier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(Tenant tenant, OPSession session) {
      sessions.put(session.id().value(), jsonConverter.write(session));
    }
  }

  static class UnusedClientSessionRepository implements ClientSessionRepository {

    @Override
    public void register(Tenant tenant, ClientSession session) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(Tenant tenant, ClientSession session) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ClientSession> findBySid(Tenant tenant, ClientSessionIdentifier sid) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ClientSessions findByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ClientSession> findByOpSessionIdAndClientId(
        Tenant tenant, OPSessionIdentifier opSessionId, String clientId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ClientSessions findByTenantAndSub(TenantIdentifier tenantId, String sub) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ClientSessions findByTenantClientAndSub(
        TenantIdentifier tenantId, String clientId, String sub) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteBySid(Tenant tenant, ClientSessionIdentifier sid) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int deleteByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
      throw new UnsupportedOperationException();
    }
  }
}
