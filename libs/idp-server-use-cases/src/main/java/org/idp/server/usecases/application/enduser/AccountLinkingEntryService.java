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
import org.idp.server.account_linking.exception.AccountLinkingOperatorMismatchException;
import org.idp.server.account_linking.io.AccountLinkingResponse;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Entry service for external IdP account linking.
 *
 * <p>Reads the operator for {@code /linking/start} from the OP session rather than from a Bearer
 * token, because that endpoint is reached by a top level browser navigation.
 */
@Transaction
public class AccountLinkingEntryService implements AccountLinkingApi {

  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  LinkedExternalAccountQueryRepository linkedExternalAccountQueryRepository;
  AccountLinkingService accountLinkingService;
  OIDCSessionHandler oidcSessionHandler;
  SessionCookieDelegate sessionCookieDelegate;
  AccountLinkingCookieDelegate accountLinkingCookieDelegate;
  LoggerWrapper log = LoggerWrapper.getLogger(AccountLinkingEntryService.class);

  static final long LINKING_SESSION_DURATION_SECONDS = 900;

  public AccountLinkingEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      LinkedExternalAccountQueryRepository linkedExternalAccountQueryRepository,
      AccountLinkingService accountLinkingService,
      OIDCSessionHandler oidcSessionHandler,
      SessionCookieDelegate sessionCookieDelegate,
      AccountLinkingCookieDelegate accountLinkingCookieDelegate) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.linkedExternalAccountQueryRepository = linkedExternalAccountQueryRepository;
    this.accountLinkingService = accountLinkingService;
    this.oidcSessionHandler = oidcSessionHandler;
    this.sessionCookieDelegate = sessionCookieDelegate;
    this.accountLinkingCookieDelegate = accountLinkingCookieDelegate;
  }

  @Override
  public AccountLinkingResponse startLink(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      ExternalIdpProvider provider,
      AccountLinkingStartRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, oAuthToken.requestedClientId());
    List<String> allowedRedirectUris = clientConfiguration.linkingReturnUris();

    AccountLinkingSession session =
        accountLinkingService.start(
            tenant,
            user,
            oAuthToken.requestedClientId(),
            provider,
            request.redirectUri(),
            request.scope(),
            allowedRedirectUris);

    Map<String, Object> contents = new HashMap<>();
    String issuer = authorizationServerConfigurationQueryRepository.get(tenant).issuer();
    contents.put("start_url", accountLinkingService.startUri(issuer, session));
    contents.put("state", session.state().value());
    contents.put("expires_in", 900);

    return new AccountLinkingResponse(201, contents);
  }

  @Override
  public AccountLinkingResponse authorizeStart(
      TenantIdentifier tenantIdentifier,
      AccountLinkingState state,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // The subject is established here, not carried over from link. Skipping this check is what
    // lets an attacker's session be walked through by a victim, so an absent OP session is an
    // error rather than something to fall through: a step-up authentication belongs here.
    Optional<OPSession> opSession =
        oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate);
    if (opSession.isEmpty()) {
      throw new UnauthorizedException(
          "Account linking requires an authenticated browser session at this endpoint.");
    }

    AccountLinkingAuthorization authorization =
        accountLinkingService.authorize(tenant, state, opSession.get().userIdentifier());

    // The callback carries no Bearer token and is reached from the external IdP, so this cookie is
    // the only thing that will tell it the returning browser is the one leaving here now.
    accountLinkingCookieDelegate.setBrowserBindingCookie(
        tenant, authorization.browserBindingSecret(), LINKING_SESSION_DURATION_SECONDS);

    return AccountLinkingResponse.redirect(authorization.authorizationUri());
  }

  @Override
  public AccountLinkingResponse handleCallback(
      TenantIdentifier tenantIdentifier,
      AccountLinkingState state,
      String code,
      String error,
      String errorDescription,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (error != null && !error.isEmpty()) {
      log.warn("Account linking denied at the external identity provider. error={}", error);
      accountLinkingCookieDelegate.clearBrowserBindingCookie(tenant);
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", error);
      contents.put("error_description", errorDescription);
      return new AccountLinkingResponse(400, contents);
    }

    String browserBindingSecret =
        accountLinkingCookieDelegate.getBrowserBindingSecret().orElse(null);

    String redirectUri;
    try {
      redirectUri = accountLinkingService.handleCallback(tenant, state, code, browserBindingSecret);
    } catch (AccountLinkingOperatorMismatchException e) {
      // A browser that cannot present the binding is not the one that started this link. Drop the
      // cookie so a stale value cannot be replayed, and leave the session for the browser that can.
      accountLinkingCookieDelegate.clearBrowserBindingCookie(tenant);
      throw e;
    }

    accountLinkingCookieDelegate.clearBrowserBindingCookie(tenant);

    return AccountLinkingResponse.redirect(
        String.format("%s?linking=done&state=%s", redirectUri, state.value()));
  }

  @Override
  public AccountLinkingResponse complete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AccountLinkingState state,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    LinkedExternalAccount account =
        accountLinkingService.complete(tenant, state, user.userIdentifier());

    return new AccountLinkingResponse(201, account.toMap());
  }

  @Override
  @Transaction(readOnly = true)
  public AccountLinkingResponse findList(
      TenantIdentifier tenantIdentifier, User user, RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<Map<String, Object>> list =
        linkedExternalAccountQueryRepository.findList(tenant, user.userIdentifier()).stream()
            .map(LinkedExternalAccount::toMap)
            .toList();

    Map<String, Object> contents = new HashMap<>();
    contents.put("list", list);
    return new AccountLinkingResponse(200, contents);
  }
}
