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

package org.idp.server.usecases.application.enduser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.handler.AccountLinkingAuthorizeHandler;
import org.idp.server.account_linking.handler.AccountLinkingCallbackHandler;
import org.idp.server.account_linking.handler.AccountLinkingCompleteHandler;
import org.idp.server.account_linking.handler.AccountLinkingStartHandler;
import org.idp.server.account_linking.handler.AccountLinkingUnlinkHandler;
import org.idp.server.account_linking.handler.LinkedExternalAccountTokenHandler;
import org.idp.server.account_linking.io.AccountLinkingAuthorizeRequest;
import org.idp.server.account_linking.io.AccountLinkingAuthorizeResult;
import org.idp.server.account_linking.io.AccountLinkingCallbackRequest;
import org.idp.server.account_linking.io.AccountLinkingCompleteRequest;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenResult;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.UserEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Entry service for external IdP account linking.
 *
 * <p>Owns the transaction, resolves the tenant, and turns each handler result into a response plus
 * the security event it should be recorded as. The handlers never throw on a failed check, so a
 * rejected browser binding or a mismatched operator is recorded rather than lost — a run of those
 * is what an attempt to attach someone else's external account looks like.
 */
@Transaction
public class AccountLinkingEntryService implements AccountLinkingApi {

  TenantQueryRepository tenantQueryRepository;
  LinkedExternalAccountQueryRepository linkedExternalAccountQueryRepository;
  AccountLinkingStartHandler startHandler;
  AccountLinkingAuthorizeHandler authorizeHandler;
  AccountLinkingCallbackHandler callbackHandler;
  AccountLinkingCompleteHandler completeHandler;
  AccountLinkingUnlinkHandler unlinkHandler;
  LinkedExternalAccountTokenHandler tokenHandler;
  OIDCSessionHandler oidcSessionHandler;
  SessionCookieDelegate sessionCookieDelegate;
  AccountLinkingCookieDelegate accountLinkingCookieDelegate;
  UserEventPublisher eventPublisher;

  public AccountLinkingEntryService(
      TenantQueryRepository tenantQueryRepository,
      LinkedExternalAccountQueryRepository linkedExternalAccountQueryRepository,
      AccountLinkingStartHandler startHandler,
      AccountLinkingAuthorizeHandler authorizeHandler,
      AccountLinkingCallbackHandler callbackHandler,
      AccountLinkingCompleteHandler completeHandler,
      AccountLinkingUnlinkHandler unlinkHandler,
      LinkedExternalAccountTokenHandler tokenHandler,
      OIDCSessionHandler oidcSessionHandler,
      SessionCookieDelegate sessionCookieDelegate,
      AccountLinkingCookieDelegate accountLinkingCookieDelegate,
      UserEventPublisher eventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.linkedExternalAccountQueryRepository = linkedExternalAccountQueryRepository;
    this.startHandler = startHandler;
    this.authorizeHandler = authorizeHandler;
    this.callbackHandler = callbackHandler;
    this.completeHandler = completeHandler;
    this.unlinkHandler = unlinkHandler;
    this.tokenHandler = tokenHandler;
    this.oidcSessionHandler = oidcSessionHandler;
    this.sessionCookieDelegate = sessionCookieDelegate;
    this.accountLinkingCookieDelegate = accountLinkingCookieDelegate;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public AccountLinkingResult startLink(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      ExternalIdpProvider provider,
      Map<String, Object> body,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (!AccountLinkingScope.MANAGE.isGrantedBy(oAuthToken)) {
      return AccountLinkingResult.insufficientScope(AccountLinkingScope.MANAGE);
    }

    AccountLinkingResult result =
        startHandler.handle(
            new AccountLinkingStartRequest(tenant, user, oAuthToken, provider, body));

    publish(tenant, oAuthToken, result, requestAttributes);

    return result;
  }

  @Override
  public AccountLinkingResult authorizeStart(
      TenantIdentifier tenantIdentifier,
      AccountLinkingState state,
      boolean returnedFromSignIn,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // The subject is established here, not carried over from link. Skipping this check is what
    // lets an attacker's session be walked through by a victim, so an absent OP session is never
    // fallen through: the browser is sent to sign in as the link's user and back here.
    // Only a session this browser signed in to counts. A session cookie placed in it from a
    // sibling subdomain would otherwise make the attacker the operator here, and the binding
    // issued below would then go to the victim's browser.
    Optional<OPSession> opSession =
        oidcSessionHandler.getBrowserHeldOPSessionFromCookie(tenant, sessionCookieDelegate);
    if (opSession.isEmpty()) {
      AccountLinkingResult result =
          authorizeHandler.handleWithoutBrowserSession(tenant, state, returnedFromSignIn);
      publish(tenant, result, requestAttributes);
      return result;
    }

    OPSession browserSession = opSession.get();
    AccountLinkingAuthorizeResult authorization =
        authorizeHandler.handle(
            new AccountLinkingAuthorizeRequest(
                tenant,
                state,
                browserSession.user(),
                browserSession.authentication(),
                returnedFromSignIn));
    AccountLinkingResult result = authorization.result();

    if (authorization.hasBrowserBinding()) {
      // The callback carries no Bearer token and is reached from the external IdP, so this cookie
      // is the only thing that will tell it the returning browser is the one leaving here now.
      accountLinkingCookieDelegate.setBrowserBindingCookie(
          tenant, authorization.browserBindingSecret(), AccountLinkingSession.DURATION_SECONDS);
    }

    publish(
        tenant,
        result.withContext(result.requestedClientId(), browserSession.user()),
        requestAttributes);

    return result;
  }

  @Override
  public AccountLinkingResult handleCallback(
      TenantIdentifier tenantIdentifier,
      ExternalIdpProvider provider,
      AccountLinkingState state,
      String code,
      String error,
      String errorDescription,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    String browserBindingSecret =
        accountLinkingCookieDelegate.getBrowserBindingSecret(tenant).orElse(null);

    AccountLinkingResult result =
        callbackHandler.handle(
            new AccountLinkingCallbackRequest(
                tenant, provider, state, code, error, errorDescription, browserBindingSecret));

    // The cookie has done its job either way. Dropping it on failure keeps a stale value from
    // being replayed; the linking session itself is left for the browser that can present one.
    accountLinkingCookieDelegate.clearBrowserBindingCookie(tenant);

    publish(tenant, result, requestAttributes);

    return result;
  }

  @Override
  public AccountLinkingResult complete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AccountLinkingState state,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (!AccountLinkingScope.MANAGE.isGrantedBy(oAuthToken)) {
      return AccountLinkingResult.insufficientScope(AccountLinkingScope.MANAGE);
    }

    AccountLinkingResult result =
        completeHandler.handle(new AccountLinkingCompleteRequest(tenant, state, user, oAuthToken));

    publish(tenant, oAuthToken, result, requestAttributes);

    return result;
  }

  @Override
  @Transaction(readOnly = true)
  public AccountLinkingResult findList(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (!AccountLinkingScope.READ.isGrantedBy(oAuthToken)) {
      return AccountLinkingResult.insufficientScope(AccountLinkingScope.READ);
    }

    List<Map<String, Object>> list =
        linkedExternalAccountQueryRepository.findList(tenant, user.userIdentifier()).stream()
            .map(LinkedExternalAccount::toMap)
            .toList();

    Map<String, Object> contents = new HashMap<>();
    contents.put("list", list);

    return AccountLinkingResult.success(AccountLinkingStatus.OK, contents, user);
  }

  @Override
  public AccountLinkingResult unlink(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AccountAlias alias,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (!AccountLinkingScope.MANAGE.isGrantedBy(oAuthToken)) {
      return AccountLinkingResult.insufficientScope(AccountLinkingScope.MANAGE);
    }

    AccountLinkingResult result = unlinkHandler.handle(tenant, user, alias);

    publish(tenant, oAuthToken, result, requestAttributes);

    return result;
  }

  @Override
  public AccountLinkingResult retrieveToken(
      TenantIdentifier tenantIdentifier,
      AccountAlias alias,
      HttpRequestInputs inputs,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    LinkedExternalAccountTokenResult tokenResult =
        tokenHandler.handle(new LinkedExternalAccountTokenRequest(tenant, alias, inputs));
    AccountLinkingResult result = tokenResult.result();

    Map<String, Object> details = tokenResult.eventDetails();
    if (result.hasEventType()) {
      publishWithDetails(tenant, result, result.eventType(), details, requestAttributes);
    }
    if (tokenResult.refreshed()) {
      publishWithDetails(
          tenant,
          result,
          DefaultSecurityEventType.external_account_token_refreshed.toEventType(),
          details,
          requestAttributes);
    }
    if (tokenResult.relinkRequired()) {
      publishWithDetails(
          tenant,
          result,
          DefaultSecurityEventType.external_account_relink_required.toEventType(),
          details,
          requestAttributes);
    }

    return result;
  }

  private void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      AccountLinkingResult result,
      RequestAttributes requestAttributes) {
    if (!result.hasEventType()) {
      return;
    }
    eventPublisher.publish(tenant, oAuthToken, result.eventType(), requestAttributes);
  }

  /** Events of a token retrieval carry the provider and the refusal's error under this key. */
  static final String EVENT_DETAILS_KEY = "account_linking";

  private void publishWithDetails(
      Tenant tenant,
      AccountLinkingResult result,
      SecurityEventType eventType,
      Map<String, Object> details,
      RequestAttributes requestAttributes) {
    eventPublisher.publish(
        tenant,
        result.requestedClientId(),
        result.hasUser() ? result.user() : new User(),
        eventType,
        EVENT_DETAILS_KEY,
        details,
        requestAttributes);
  }

  private void publish(
      Tenant tenant, AccountLinkingResult result, RequestAttributes requestAttributes) {
    if (!result.hasEventType()) {
      return;
    }
    eventPublisher.publish(
        tenant,
        result.requestedClientId(),
        result.hasUser() ? result.user() : new User(),
        result.eventType(),
        requestAttributes);
  }
}
