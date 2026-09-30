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

package org.idp.server.core.openid.oauth;

import org.idp.server.core.openid.authentication.AuthSessionId;
import org.idp.server.core.openid.authentication.AuthSessionValidator;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractor;
import org.idp.server.core.openid.authentication.AuthenticationInteractors;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * How an authorization binds to the browser, decided once per request.
 *
 * <p>Same-site, the browser is identified by the {@code IDP_AUTH_SESSION} cookie on every call.
 * Where the authorization view is on another site, the view's calls are third-party and carry no
 * cookie, so this server identifies the browser by one-time values instead ({@link
 * AuthenticationProof}) and reads the cookie again only at {@code /complete}, a top level
 * navigation where it does arrive.
 *
 * <p>Everything here is a decision about values: which proof a step earns, whether {@code
 * /authorize} may proceed, what {@code /complete} accepts. Reading and writing the transaction,
 * cookies and sessions is left to the caller, so the rules can be exercised without any of them.
 */
public class CrossSiteAuthorizationBinding {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(CrossSiteAuthorizationBinding.class);

  boolean crossSite;
  AesCipher aesCipher;

  /**
   * @param crossSite whether this request's authorization view is on another site; decided once, at
   *     the start of the request, so that one request never gets two answers
   * @param aesCipher encrypts the redirect carried by the proof for {@code /complete}
   */
  public CrossSiteAuthorizationBinding(boolean crossSite, AesCipher aesCipher) {
    this.crossSite = crossSite;
    this.aesCipher = aesCipher;
  }

  /** Whether the authorization view is on another site. */
  public boolean crossSite() {
    return crossSite;
  }

  /**
   * Whether the {@code IDP_AUTH_SESSION} cookie is checked on the view's own calls.
   *
   * <p>Not where the view is on another site: those calls are third-party and the cookie is not
   * sent, so the check could only ever fail. Those deployments are bound by the proofs instead, and
   * by the cookie again at {@code /complete}.
   */
  public boolean checksBrowserCookieOnViewCalls() {
    return !crossSite;
  }

  /**
   * The proof a browser step earns, or null when it earns none.
   *
   * <p>Issued on the step itself rather than when the whole transaction succeeds, because the two
   * are often not the same caller. A flow that finishes out of band — a push notification, FIDO UAF
   * — is completed by the device, and a value handed back there never reaches the browser that has
   * to call {@code /authorize}.
   *
   * <p>The call has to come from the browser, or the proof goes to the device. And the step has to
   * be one the end-user could only pass by supplying something they hold: sending a code,
   * cancelling, or acknowledging a notification are things anyone with the request id can do, and a
   * proof earned that way would be worth nothing.
   */
  public AuthenticationProof proofForStep(
      AuthenticationInteractor interactor, AuthenticationInteractionRequestResult result) {
    if (!crossSite || !interactor.isBrowserBased()) {
      return null;
    }
    if (!result.isSuccess() || !result.operationType().provesPossession()) {
      return null;
    }
    return proofFor(result.hasUser() ? result.user() : null);
  }

  /**
   * The proof a federated sign-in earns. It ends in the response the browser reads on its way back
   * from the external provider, so it goes to that browser.
   */
  public AuthenticationProof proofForFederation(User user) {
    if (!crossSite) {
      return null;
    }
    return proofFor(user);
  }

  /**
   * Carries what a cross-site flow keeps in place of cookies onto the transaction: the session this
   * step created, and the proof it earned.
   *
   * <p>On the transaction because the caller that created the session is not always the browser:
   * when a device completes the last step, the session is created on its call, and nothing returned
   * there reaches the browser that goes on to {@code /authorize}.
   *
   * @return the transaction to store, or null when there is nothing to change
   */
  public AuthenticationTransaction carry(
      AuthenticationTransaction transaction, String createdSessionId, AuthenticationProof proof) {
    if (!crossSite) {
      return null;
    }
    boolean hasSession = createdSessionId != null && !createdSessionId.isEmpty();
    if (!hasSession && proof == null) {
      return null;
    }
    AuthenticationTransactionAttributes attributes = attributesOf(transaction);
    if (hasSession) {
      attributes = attributes.withOpSessionId(createdSessionId);
    }
    if (proof != null) {
      attributes = proof.storeOn(attributes, aesCipher);
    }
    return transaction.withAttributes(attributes);
  }

  /**
   * Binds the OP session the authorization request found to the transaction.
   *
   * <p>The authorization request is a top level navigation, so the session cookie is readable there
   * even where the view is on another site. Everything the view calls next is third-party and will
   * not see it.
   *
   * @return the transaction to store, or the same one when nothing is bound
   */
  public AuthenticationTransaction bindSession(
      AuthenticationTransaction transaction, String opSessionId) {
    if (!crossSite || opSessionId == null || opSessionId.isEmpty()) {
      return transaction;
    }
    return transaction.withAttributes(attributesOf(transaction).withOpSessionId(opSessionId));
  }

  /**
   * The OP session bound to the transaction, where the view is on another site. Null same-site,
   * where the session comes from the cookie instead, and null when none was bound.
   */
  public String boundSessionId(AuthenticationTransaction transaction) {
    if (!crossSite) {
      return null;
    }
    AuthenticationTransactionAttributes attributes = attributesOf(transaction);
    return attributes.hasOpSessionId() ? attributes.opSessionId() : null;
  }

  /**
   * Whether {@code /authorize} may proceed, before anything is minted.
   *
   * <p>The code is minted in {@code /authorize} and leaves in its response, so whatever answers
   * "which browser is calling" has to answer it there.
   *
   * @param presentedProof the {@code auth_proof} in the authorize body, or null
   */
  public AuthorizeGate gateAuthorize(
      AuthenticationTransaction transaction,
      AuthenticationInteractors interactors,
      Object presentedProof) {
    if (handedOffForCompletion(transaction)) {
      return AuthorizeGate.ALREADY_HANDED_OFF;
    }
    if (!crossSite) {
      return AuthorizeGate.CHECK_BROWSER_COOKIE;
    }
    // Read from the transaction, not from anything that can fail to answer: a store that cannot
    // answer must not turn into "no browser step" and waive the proof.
    if (!transaction.browserProvedPossession(interactors)) {
      // No browser step proved anything, so there is nothing to ask the browser for that an
      // attacker could not also produce. Bound to the starting browser at /complete instead.
      return AuthorizeGate.PROCEED;
    }
    String presented = presentedProof instanceof String string ? string : null;
    boolean accepted =
        AuthenticationProof.authenticatedOn(attributesOf(transaction))
            .authorizes(presented, subjectOf(transaction.user()));
    return accepted ? AuthorizeGate.PROCEED : AuthorizeGate.PROOF_REJECTED;
  }

  /**
   * Whether this authorization has already been authorized and is waiting for {@code /complete}.
   *
   * <p>Cross-site the transaction stays until {@code /complete}, and for flows that ask for no
   * proof at {@code /authorize} anyone holding the request id can call it. Authorizing again would
   * mint another code and replace the hand-off the browser was given, locking that browser out. The
   * first hand-off stands.
   */
  public boolean handedOffForCompletion(AuthenticationTransaction transaction) {
    return crossSite
        && AuthenticationProof.completionOn(attributesOf(transaction), aesCipher).exists();
  }

  /**
   * Issues the value the same browser must present to {@code /complete}, spending the one for
   * {@code /authorize}. The redirect travels inside it, so the completing request carries nothing a
   * caller could point elsewhere.
   *
   * @return the hand-off, or null where there is nothing to hand off (same-site, or no redirect)
   */
  public HandOff handOff(AuthenticationTransaction transaction, User user, String redirectTarget) {
    if (!crossSite || redirectTarget == null || redirectTarget.isEmpty()) {
      return null;
    }
    AuthenticationTransactionAttributes attributes = attributesOf(transaction);
    attributes = AuthenticationProof.authenticatedOn(attributes).spendOn(attributes);
    AuthenticationProof completion =
        AuthenticationProof.forCompletion(subjectOf(user), new RedirectUri(redirectTarget));
    return new HandOff(
        transaction.withAttributes(completion.storeOn(attributes, aesCipher)), completion.value());
  }

  /**
   * What {@code /complete} accepts, in the order it has to be checked.
   *
   * <ol>
   *   <li>The browser binding — the browser that started the request. Checked before the proof is
   *       touched: the other way round, a caller with no binding could spend the proof and lock out
   *       the browser it was issued to.
   *   <li>The second-stage proof — the browser that authenticated. A first-stage one is refused, or
   *       a caller could skip {@code /authorize}.
   *   <li>The redirect it carries, against the one the request is entitled to.
   * </ol>
   *
   * <p>A transaction with no binding at all is refused rather than let through: for flows that ask
   * for no proof at {@code /authorize} this is the only thing tying the code to a browser. A policy
   * that opts out of the binding is honoured, since a tenant may opt out for reasons of its own,
   * but logged: cross-site deployments used to need the opt-out, and one left in place removes the
   * binding these flows rely on.
   *
   * @param cookieAuthSessionId the {@code IDP_AUTH_SESSION} cookie this navigation carried
   * @param presentedProof the {@code auth_proof} in the query
   * @param entitledRedirectUri the redirect URI this request is entitled to
   */
  public CompletionCheck checkCompletion(
      AuthenticationTransaction transaction,
      AuthSessionId cookieAuthSessionId,
      String presentedProof,
      RedirectUri entitledRedirectUri) {
    if (!startedByThisBrowser(transaction, cookieAuthSessionId)) {
      return CompletionCheck.rejected("this browser did not start the authorization request.");
    }
    AuthenticationProof proof =
        AuthenticationProof.completionOn(attributesOf(transaction), aesCipher);
    if (!proof.completes(presentedProof)) {
      return CompletionCheck.rejected("authorization request is not in progress.");
    }
    RedirectUri to = proof.redirectUri();
    if (!entitledRedirectUri.addressesSameTarget(to)) {
      return CompletionCheck.rejected("redirect target does not match the authorization request.");
    }
    return CompletionCheck.accepted(to);
  }

  private boolean startedByThisBrowser(
      AuthenticationTransaction transaction, AuthSessionId cookieAuthSessionId) {
    if (!transaction.hasAuthSessionId()) {
      return false;
    }
    AuthenticationPolicy authenticationPolicy = transaction.authenticationPolicy();
    if (authenticationPolicy != null && !authenticationPolicy.authSessionBindingRequired()) {
      log.warn(
          "/complete is not bound to the browser that started the request:"
              + " auth_session_binding_required=false in the authentication policy. authorization_id={}",
          transaction.authorizationIdentifier().value());
    }
    try {
      AuthSessionValidator.validate(
          transaction, cookieAuthSessionId != null ? cookieAuthSessionId : new AuthSessionId());
      return true;
    } catch (UnauthorizedException e) {
      return false;
    }
  }

  private AuthenticationProof proofFor(User user) {
    // A proof that cannot say whose it is would be refused by /authorize anyway. Not issuing one
    // has the same outcome, without reading a sub that is not there.
    if (user == null || !user.exists()) {
      return null;
    }
    return AuthenticationProof.authenticated(subjectOf(user));
  }

  private static Subject subjectOf(User user) {
    return user != null && user.exists() ? new Subject(user.sub()) : new Subject();
  }

  private static AuthenticationTransactionAttributes attributesOf(
      AuthenticationTransaction transaction) {
    return transaction.hasAttributes()
        ? transaction.attributes()
        : new AuthenticationTransactionAttributes();
  }

  /** What {@link #gateAuthorize} decided. */
  public enum AuthorizeGate {
    /** The proof for {@code /complete} has already been issued; a second authorize is refused. */
    ALREADY_HANDED_OFF,
    /** Cross-site, and the proof the browser earned was not presented. */
    PROOF_REJECTED,
    /** Same-site: the caller checks the {@code IDP_AUTH_SESSION} cookie as usual. */
    CHECK_BROWSER_COOKIE,
    /** Cross-site, and nothing more is asked here. */
    PROCEED
  }

  /**
   * The transaction carrying the proof for {@code /complete}, and the value handed to the browser.
   */
  public record HandOff(AuthenticationTransaction transaction, String proofValue) {}

  /** What {@link #checkCompletion} decided. */
  public static class CompletionCheck {

    RedirectUri redirectUri;
    String errorDescription;

    private CompletionCheck(RedirectUri redirectUri, String errorDescription) {
      this.redirectUri = redirectUri;
      this.errorDescription = errorDescription;
    }

    static CompletionCheck accepted(RedirectUri redirectUri) {
      return new CompletionCheck(redirectUri, null);
    }

    static CompletionCheck rejected(String errorDescription) {
      return new CompletionCheck(null, errorDescription);
    }

    public boolean isAccepted() {
      return redirectUri != null;
    }

    public RedirectUri redirectUri() {
      return redirectUri;
    }

    public String errorDescription() {
      return errorDescription;
    }
  }
}
