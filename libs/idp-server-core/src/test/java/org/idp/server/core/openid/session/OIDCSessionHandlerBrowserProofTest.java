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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * A session cookie on its own may have been placed in the browser by a host on a sibling subdomain.
 * Steps that act on who is at the browser take the session only when the browser also holds the
 * proof issued with it.
 */
class OIDCSessionHandlerBrowserProofTest {

  private final OIDCSessionHandlerReauthenticationTest.InMemoryOPSessionRepository repository =
      new OIDCSessionHandlerReauthenticationTest.InMemoryOPSessionRepository();
  private final OIDCSessionHandler handler =
      new OIDCSessionHandler(
          new OIDCSessionService(
              repository,
              new OIDCSessionHandlerReauthenticationTest.UnusedClientSessionRepository()));
  private final Tenant tenant = new Tenant();

  @Test
  void signInIssuesAProofTheSessionIsHeldBy() {
    RecordingCookies browser = new RecordingCookies();

    OPSession session = signIn("user-a", null, browser);

    assertNotNull(browser.browserProof);
    assertEquals(session.id().value(), browser.identityToken);
    Optional<OPSession> held = handler.getBrowserHeldOPSessionFromCookie(tenant, browser);
    assertTrue(held.isPresent());
    assertEquals(session.id().value(), held.get().id().value());
  }

  @Test
  void sessionCookieWithoutProofIsNoSession() {
    RecordingCookies browser = new RecordingCookies();
    signIn("user-a", null, browser);

    browser.browserProof = null;

    assertTrue(handler.getBrowserHeldOPSessionFromCookie(tenant, browser).isEmpty());
    assertTrue(handler.getOPSessionFromCookie(tenant, browser).isPresent());
  }

  /** Another user's session cookie, placed beside this browser's own proof. */
  @Test
  void anotherSessionsCookieIsNotHeldByThisBrowsersProof() {
    RecordingCookies attackerBrowser = new RecordingCookies();
    signIn("attacker", null, attackerBrowser);
    RecordingCookies victimBrowser = new RecordingCookies();
    signIn("victim", null, victimBrowser);

    victimBrowser.identityToken = attackerBrowser.identityToken;

    assertTrue(handler.getBrowserHeldOPSessionFromCookie(tenant, victimBrowser).isEmpty());
  }

  /** Sessions stored before proofs were issued carry none. */
  @Test
  void sessionWithoutRecordedProofIsHeldByNoBrowser() {
    OPSession session = sessionRecordedBeforeProofs("user-a");
    repository.register(tenant, session);
    RecordingCookies browser = new RecordingCookies();
    browser.identityToken = session.id().value();
    browser.browserProof = BrowserProof.generate().value();

    assertTrue(handler.getBrowserHeldOPSessionFromCookie(tenant, browser).isEmpty());
  }

  /**
   * Signing in again in the same browser reuses the session and records the new proof in the same
   * write as the new authentication; the earlier proof stops counting.
   */
  @Test
  void signingInAgainReplacesTheProof() {
    RecordingCookies browser = new RecordingCookies();
    OPSession first = signIn("user-a", null, browser);
    String firstProof = browser.browserProof;

    OPSession existing = repository.findById(tenant, first.id()).orElseThrow();
    OPSession reused = signIn("user-a", existing, browser);

    assertEquals(first.id().value(), reused.id().value());
    assertNotEquals(firstProof, browser.browserProof);
    assertTrue(handler.getBrowserHeldOPSessionFromCookie(tenant, browser).isPresent());
    browser.browserProof = firstProof;
    assertTrue(handler.getBrowserHeldOPSessionFromCookie(tenant, browser).isEmpty());
  }

  @Test
  void proofHashSurvivesTheSessionStoreAndOldSessionsStillRead() {
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    BrowserProof proof = BrowserProof.generate();
    OPSession session =
        OPSession.create(
            new TenantIdentifier("tenant-browser-proof"),
            new User().setSub("user-a"),
            Instant.now(),
            "urn:idp:acr:password",
            List.of("pwd"),
            Map.of(),
            3600,
            "192.168.1.1",
            "Mozilla/5.0",
            proof);

    String json = jsonConverter.write(session);
    assertTrue(json.contains("\"browser_proof_hash\""), json);
    assertFalse(json.contains(proof.value()), "the proof itself is not stored");
    assertTrue(jsonConverter.read(json, OPSession.class).isHeldBy(proof.value()));

    String recordedBefore = jsonConverter.write(sessionRecordedBeforeProofs("user-b"));
    OPSession old = jsonConverter.read(recordedBefore, OPSession.class);
    assertFalse(old.isHeldBy(proof.value()));
  }

  /** Signs in the way the authorization flow does: one proof for the session and the cookie. */
  private OPSession signIn(String sub, OPSession existing, RecordingCookies browser) {
    BrowserProof browserProof = BrowserProof.generate();
    OPSession session =
        handler.onAuthenticationSuccess(
            tenant,
            new User().setSub(sub),
            new Authentication()
                .setTime(SystemDateTime.now())
                .addAcr("urn:idp:acr:password")
                .addMethods(List.of("pwd")),
            Map.of(),
            existing,
            null,
            browserProof);
    handler.registerSessionCookies(tenant, session, browserProof, browser);
    return session;
  }

  private static OPSession sessionRecordedBeforeProofs(String sub) {
    Instant now = Instant.now();
    return new OPSession(
        OPSessionIdentifier.generate(),
        new TenantIdentifier("tenant-browser-proof"),
        new User().setSub(sub),
        now,
        "urn:idp:acr:password",
        List.of("pwd"),
        Map.of(),
        BrowserState.generate(),
        now,
        now.plusSeconds(3600),
        now,
        "192.168.1.1",
        "Mozilla/5.0");
  }

  /** The browser's cookie jar as the server sees it: what was last written is what comes back. */
  static class RecordingCookies implements SessionCookieDelegate {

    String identityToken;
    String sessionHash;
    String browserProof;

    @Override
    public void registerSessionCookies(
        Tenant tenant, String identityToken, String sessionHash, long maxAgeSeconds) {
      this.identityToken = identityToken;
      this.sessionHash = sessionHash;
    }

    @Override
    public void registerBrowserProofCookie(Tenant tenant, String browserProof, long maxAgeSeconds) {
      this.browserProof = browserProof;
    }

    @Override
    public Optional<String> getBrowserProof(Tenant tenant) {
      return Optional.ofNullable(browserProof);
    }

    @Override
    public Optional<String> getIdentityToken() {
      return Optional.ofNullable(identityToken);
    }

    @Override
    public Optional<String> getSessionHash() {
      return Optional.ofNullable(sessionHash);
    }

    @Override
    public void clearSessionCookies(Tenant tenant) {
      identityToken = null;
      sessionHash = null;
      browserProof = null;
    }
  }
}
