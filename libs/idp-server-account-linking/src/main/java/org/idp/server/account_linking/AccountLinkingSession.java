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

package org.idp.server.account_linking;

import java.time.LocalDateTime;
import org.idp.server.account_linking.exception.AccountLinkingOperatorMismatchException;
import org.idp.server.account_linking.exception.AccountLinkingSessionStateException;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * One in-flight attempt to link an external IdP account, from {@code link} through {@code
 * complete}.
 *
 * <h3>Why the user is bound here</h3>
 *
 * <p>The external IdP returns through a browser navigation that carries no Bearer token, so the
 * callback cannot tell whose link this is. The user is therefore captured from the Bearer token at
 * link time and never re-derived afterwards.
 *
 * <p>Binding alone is not enough. Two directions of linking CSRF exist and each needs its own
 * check:
 *
 * <ul>
 *   <li>An attacker replays a <em>victim's</em> state to attach the attacker's external account to
 *       the victim. Caught at {@code complete} by {@link #consume}.
 *   <li>An attacker lures a victim through the <em>attacker's own</em> state, so the victim's
 *       external account lands on the attacker's record. This one survives {@code complete}, since
 *       the attacker holds the state and can claim it with a matching Bearer. It is caught only at
 *       {@code /linking/start} by {@link #authorize}, before the victim ever reaches the external
 *       IdP.
 * </ul>
 *
 * <p>Both call {@link #verifyOperator}. Dropping the one in {@link #authorize} reopens the second
 * attack, which is why it is not deferred to a later phase.
 */
public class AccountLinkingSession {

  AccountLinkingState state;
  TenantIdentifier tenantIdentifier;
  UserIdentifier userIdentifier;
  RequestedClientId requestedClientId;
  ExternalIdpProvider provider;
  AccountAlias accountAlias;
  String redirectUri;
  String requestedScope;
  String codeVerifier;
  String nonce;
  String acr;
  String amr;
  LocalDateTime authenticatedAt;
  AccountLinkingSessionStatus status;
  String browserBindingHash;
  ParkedCredentials parkedCredentials;
  LocalDateTime expiresAt;

  public AccountLinkingSession() {}

  AccountLinkingSession(Builder builder) {
    this.state = builder.state;
    this.tenantIdentifier = builder.tenantIdentifier;
    this.userIdentifier = builder.userIdentifier;
    this.requestedClientId = builder.requestedClientId;
    this.provider = builder.provider;
    this.accountAlias = builder.accountAlias;
    this.redirectUri = builder.redirectUri;
    this.requestedScope = builder.requestedScope;
    this.codeVerifier = builder.codeVerifier;
    this.nonce = builder.nonce;
    this.acr = builder.acr;
    this.amr = builder.amr;
    this.authenticatedAt = builder.authenticatedAt;
    this.status = builder.status;
    this.browserBindingHash = builder.browserBindingHash;
    this.parkedCredentials = builder.parkedCredentials;
    this.expiresAt = builder.expiresAt;
  }

  /**
   * Verifies that {@code operator} is the user this session was opened for.
   *
   * @throws AccountLinkingOperatorMismatchException if it is anyone else
   */
  public void verifyOperator(UserIdentifier operator) {
    if (operator == null || !operator.equals(userIdentifier)) {
      throw new AccountLinkingOperatorMismatchException(
          "Account linking session is bound to a different user. state="
              + state.value()
              + ", provider="
              + provider.value());
    }
  }

  /**
   * @throws AccountLinkingSessionStateException if the session is past its expiry
   */
  public void verifyNotExpired(LocalDateTime now) {
    if (isExpired(now)) {
      throw new AccountLinkingSessionStateException(
          "Account linking session is expired. state=" + state.value());
    }
  }

  public boolean isExpired(LocalDateTime now) {
    return expiresAt == null || !now.isBefore(expiresAt);
  }

  /**
   * Marks the session as ready to leave for the external IdP.
   *
   * <p>Called from {@code /linking/start}, where the operator is established from the browser
   * session or from a step-up authentication.
   */
  public AccountLinkingSession authorize(
      UserIdentifier operator,
      AccountLinkingBrowserBinding browserBinding,
      String acr,
      String amr,
      LocalDateTime authenticatedAt) {
    verifyOperator(operator);
    verifyTransitionTo(AccountLinkingSessionStatus.AUTHORIZED);

    return toBuilder()
        .browserBindingHash(browserBinding.hash())
        .acr(acr)
        .amr(amr)
        .authenticatedAt(authenticatedAt)
        .status(AccountLinkingSessionStatus.AUTHORIZED)
        .build();
  }

  /**
   * Verifies that the browser presenting the callback is the one that walked {@code
   * /linking/start}.
   *
   * @throws AccountLinkingOperatorMismatchException if the secret is absent or does not match
   */
  public void verifyBrowserBinding(String presentedSecret) {
    if (!AccountLinkingBrowserBinding.matches(browserBindingHash, presentedSecret)) {
      throw new AccountLinkingOperatorMismatchException(
          "Account linking callback came from a browser that did not start this link. state="
              + state.value()
              + ", provider="
              + provider.value());
    }
  }

  /**
   * Holds the exchanged tokens against the session without finalizing the link.
   *
   * <p>Called from the unauthenticated callback, which is why no operator is verified here and why
   * nothing is written to {@code linked_external_accounts}.
   */
  public AccountLinkingSession park(ParkedCredentials credentials) {
    verifyTransitionTo(AccountLinkingSessionStatus.PARKED);

    return toBuilder()
        .parkedCredentials(credentials)
        .status(AccountLinkingSessionStatus.PARKED)
        .build();
  }

  /**
   * Claims the parked tokens on behalf of {@code operator}.
   *
   * <p>Called from {@code complete}, where the operator comes from a Bearer token.
   */
  public AccountLinkingSession consume(UserIdentifier operator) {
    verifyOperator(operator);
    verifyTransitionTo(AccountLinkingSessionStatus.CONSUMED);

    return toBuilder().status(AccountLinkingSessionStatus.CONSUMED).build();
  }

  private void verifyTransitionTo(AccountLinkingSessionStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new AccountLinkingSessionStateException(
          "Account linking session cannot transition. state="
              + state.value()
              + ", from="
              + status
              + ", to="
              + next);
    }
  }

  public AccountLinkingState state() {
    return state;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public ExternalIdpProvider provider() {
    return provider;
  }

  public AccountAlias accountAlias() {
    return accountAlias;
  }

  public boolean hasAccountAlias() {
    return accountAlias != null && accountAlias.exists();
  }

  public String redirectUri() {
    return redirectUri;
  }

  public String requestedScope() {
    return requestedScope;
  }

  public String codeVerifier() {
    return codeVerifier;
  }

  public String nonce() {
    return nonce;
  }

  public String acr() {
    return acr;
  }

  public String amr() {
    return amr;
  }

  public LocalDateTime authenticatedAt() {
    return authenticatedAt;
  }

  public AccountLinkingSessionStatus status() {
    return status;
  }

  public String browserBindingHash() {
    return browserBindingHash;
  }

  public ParkedCredentials parkedCredentials() {
    return parkedCredentials;
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public boolean exists() {
    return state != null && state.exists();
  }

  public Builder toBuilder() {
    return new Builder()
        .state(state)
        .tenantIdentifier(tenantIdentifier)
        .userIdentifier(userIdentifier)
        .requestedClientId(requestedClientId)
        .provider(provider)
        .accountAlias(accountAlias)
        .redirectUri(redirectUri)
        .requestedScope(requestedScope)
        .codeVerifier(codeVerifier)
        .nonce(nonce)
        .acr(acr)
        .amr(amr)
        .authenticatedAt(authenticatedAt)
        .status(status)
        .browserBindingHash(browserBindingHash)
        .parkedCredentials(parkedCredentials)
        .expiresAt(expiresAt);
  }

  public static class Builder {

    AccountLinkingState state;
    TenantIdentifier tenantIdentifier;
    UserIdentifier userIdentifier;
    RequestedClientId requestedClientId;
    ExternalIdpProvider provider;
    AccountAlias accountAlias;
    String redirectUri;
    String requestedScope;
    String codeVerifier;
    String nonce;
    String acr;
    String amr;
    LocalDateTime authenticatedAt;
    AccountLinkingSessionStatus status = AccountLinkingSessionStatus.PENDING;
    String browserBindingHash;
    ParkedCredentials parkedCredentials;
    LocalDateTime expiresAt;

    public Builder state(AccountLinkingState state) {
      this.state = state;
      return this;
    }

    public Builder tenantIdentifier(TenantIdentifier tenantIdentifier) {
      this.tenantIdentifier = tenantIdentifier;
      return this;
    }

    public Builder userIdentifier(UserIdentifier userIdentifier) {
      this.userIdentifier = userIdentifier;
      return this;
    }

    public Builder requestedClientId(RequestedClientId requestedClientId) {
      this.requestedClientId = requestedClientId;
      return this;
    }

    public Builder provider(ExternalIdpProvider provider) {
      this.provider = provider;
      return this;
    }

    public Builder accountAlias(AccountAlias accountAlias) {
      this.accountAlias = accountAlias;
      return this;
    }

    public Builder redirectUri(String redirectUri) {
      this.redirectUri = redirectUri;
      return this;
    }

    public Builder requestedScope(String requestedScope) {
      this.requestedScope = requestedScope;
      return this;
    }

    public Builder codeVerifier(String codeVerifier) {
      this.codeVerifier = codeVerifier;
      return this;
    }

    public Builder nonce(String nonce) {
      this.nonce = nonce;
      return this;
    }

    public Builder acr(String acr) {
      this.acr = acr;
      return this;
    }

    public Builder amr(String amr) {
      this.amr = amr;
      return this;
    }

    public Builder authenticatedAt(LocalDateTime authenticatedAt) {
      this.authenticatedAt = authenticatedAt;
      return this;
    }

    public Builder status(AccountLinkingSessionStatus status) {
      this.status = status;
      return this;
    }

    public Builder browserBindingHash(String browserBindingHash) {
      this.browserBindingHash = browserBindingHash;
      return this;
    }

    public Builder parkedCredentials(ParkedCredentials parkedCredentials) {
      this.parkedCredentials = parkedCredentials;
      return this;
    }

    public Builder expiresAt(LocalDateTime expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public AccountLinkingSession build() {
      return new AccountLinkingSession(this);
    }
  }
}
