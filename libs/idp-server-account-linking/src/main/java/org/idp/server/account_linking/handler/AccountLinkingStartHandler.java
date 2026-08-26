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

package org.idp.server.account_linking.handler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.validator.AccountLinkingStartRequestValidator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.random.RandomStringGenerator;

/** Opens a linking session for the authenticated user and says where to send the browser. */
public class AccountLinkingStartHandler {

  public static final long SESSION_DURATION_SECONDS = 900;

  AccountLinkingStartRequestValidator validator;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  AccountLinkingErrorHandler errorHandler;

  public AccountLinkingStartHandler(
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.validator = new AccountLinkingStartRequestValidator();
    this.sessionCommandRepository = sessionCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.errorHandler = new AccountLinkingErrorHandler();
  }

  public AccountLinkingResult handle(AccountLinkingStartRequest request) {

    Tenant tenant = request.tenant();

    try {
      ClientConfiguration clientConfiguration =
          clientConfigurationQueryRepository.get(tenant, request.oAuthToken().requestedClientId());
      validator.validate(request, clientConfiguration.linkingReturnUris());

      LocalDateTime now = SystemDateTime.now();
      AccountLinkingPkce pkce = AccountLinkingPkce.generate();

      AccountLinkingSession session =
          new AccountLinkingSession.Builder()
              .state(new AccountLinkingState(new RandomStringGenerator(32).generate()))
              .tenantIdentifier(tenant.identifier())
              .userIdentifier(request.user().userIdentifier())
              .requestedClientId(request.oAuthToken().requestedClientId())
              .provider(request.provider())
              .redirectUri(request.redirectUri())
              .requestedScope(request.scope())
              .codeVerifier(pkce.codeVerifier())
              .nonce(new RandomStringGenerator(16).generate())
              .status(AccountLinkingSessionStatus.PENDING)
              .expiresAt(now.plusSeconds(SESSION_DURATION_SECONDS))
              .build();

      sessionCommandRepository.register(tenant, session);

      Map<String, Object> contents = new HashMap<>();
      contents.put("start_url", startUri(tenant, session));
      contents.put("state", session.state().value());
      contents.put("expires_in", SESSION_DURATION_SECONDS);

      return AccountLinkingResult.success(AccountLinkingStatus.CREATED, contents, request.user());

    } catch (Exception exception) {
      return errorHandler
          .handle(exception)
          .withContext(request.oAuthToken().requestedClientId(), request.user());
    }
  }

  /**
   * The URL the RP navigates to, which is on this server rather than the external IdP.
   *
   * <p>Built from the authorization server issuer rather than {@code Tenant#tokenIssuer()}. The
   * latter returns the tenant's {@code domain} column verbatim, which is free-form and need not
   * carry the tenant identifier — a tenant configured as {@code https://api.example.com} would
   * yield {@code https://api.example.com/v1/linking/start}, where {@code v1} is then read as the
   * tenant id.
   */
  private String startUri(Tenant tenant, AccountLinkingSession session) {
    String issuer = authorizationServerConfigurationQueryRepository.get(tenant).issuer();
    return String.format("%s/v1/linking/start?state=%s", issuer, session.state().value());
  }
}
