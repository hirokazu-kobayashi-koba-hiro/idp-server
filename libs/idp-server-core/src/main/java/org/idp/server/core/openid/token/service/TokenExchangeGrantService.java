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

package org.idp.server.core.openid.token.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.id_token.RequestedUserinfoClaims;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.AvailableFederation;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.validator.TokenExchangeGrantValidator;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategies;
import org.idp.server.core.openid.token.verifier.SubjectTokenVerificationStrategy;
import org.idp.server.core.openid.token.verifier.TokenExchangeDelegationVerifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * TokenExchangeGrantService
 *
 * <p>Implements RFC 8693 OAuth 2.0 Token Exchange. Supports Impersonation (subject_token only) and
 * Delegation (subject_token + actor_token) patterns, with optional JIT Provisioning.
 *
 * <p>actor_token is verified as a token issued by this authorization server (self-introspection),
 * not via external federation. This follows the industry standard approach (ZITADEL, Curity).
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>
 */
public class TokenExchangeGrantService implements OAuthTokenCreationService, RefreshTokenCreatable {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(TokenExchangeGrantService.class);

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  SubjectTokenVerificationStrategies subjectTokenVerificationStrategies;
  UserRegistrator userRegistrator;
  AccessTokenCreator accessTokenCreator;

  public TokenExchangeGrantService(
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      UserRegistrator userRegistrator,
      SubjectTokenVerificationStrategies subjectTokenVerificationStrategies) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.subjectTokenVerificationStrategies = subjectTokenVerificationStrategies;
    this.userRegistrator = userRegistrator;
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.token_exchange;
  }

  /**
   * Processes a token exchange request per RFC 8693 Section 2.
   *
   * <p>Flow: validate → verify subject_token → verify actor_token (Delegation) → verify may_act →
   * resolve user → issue token with optional act claim.
   */
  @Override
  public OAuthToken create(TokenRequestContext context, ClientCredentials clientCredentials) {
    TokenExchangeGrantValidator validator = new TokenExchangeGrantValidator(context);
    validator.validate();

    SubjectTokenVerificationStrategy strategy =
        subjectTokenVerificationStrategies.get(context.subjectTokenType());
    SubjectTokenVerificationResult verificationResult =
        strategy.verify(context, context.subjectToken());

    OAuthToken actorOAuthToken = verifyActorTokenIfPresent(context);

    if (actorOAuthToken != null) {
      TokenExchangeDelegationVerifier delegationVerifier =
          new TokenExchangeDelegationVerifier(
              verificationResult, actorOAuthToken, context.serverConfiguration());
      delegationVerifier.verify();
    }

    User user = resolveUser(context, verificationResult);
    return issueToken(context, clientCredentials, user, actorOAuthToken);
  }

  /**
   * Verifies the actor_token as a token issued by this authorization server.
   *
   * <p>RFC 8693 Section 2.1:
   *
   * <blockquote>
   *
   * The authorization server MUST perform the appropriate validation procedures for the indicated
   * token type and, if the actor token is present, also perform the appropriate validation
   * procedures for its indicated token type.
   *
   * </blockquote>
   *
   * <p>Following industry standard (ZITADEL, Curity), actor_token is verified via
   * self-introspection: the token must be a valid access token issued by this server.
   *
   * @return the actor's OAuthToken, or null if no actor_token is present
   */
  private OAuthToken verifyActorTokenIfPresent(TokenRequestContext context) {
    if (!context.hasActorToken()) {
      return null;
    }

    log.info("Delegation: verifying actor_token as self-issued access token");

    Tenant tenant = context.tenant();
    AccessTokenEntity actorAccessToken = new AccessTokenEntity(context.actorToken().value());
    OAuthToken actorOAuthToken = oAuthTokenQueryRepository.find(tenant, actorAccessToken);

    if (!actorOAuthToken.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          "actor_token is not a valid access token issued by this authorization server");
    }

    if (actorOAuthToken.isExpiredAccessToken(SystemDateTime.now())) {
      throw new TokenBadRequestException("invalid_grant", "actor_token is expired");
    }

    return actorOAuthToken;
  }

  /**
   * Resolves the user for the token exchange. If the user exists, optionally updates attributes via
   * JIT. If the user does not exist and JIT Provisioning is enabled, auto-creates a new user with
   * status {@link UserStatus#FEDERATED}.
   *
   * @throws TokenBadRequestException with {@code invalid_grant} if the user is not found and JIT
   *     Provisioning is disabled, or if no claims are available for provisioning
   */
  private User resolveUser(
      TokenRequestContext context, SubjectTokenVerificationResult verificationResult) {
    Tenant tenant = context.tenant();
    AvailableFederation federation = verificationResult.federation();

    JwtBearerUserFindingDelegate delegate = context.jwtBearerUserFindingDelegate();
    User existingUser =
        delegate.findUser(
            tenant,
            verificationResult.providerId(),
            verificationResult.subject(),
            verificationResult.subjectClaimMapping());

    if (existingUser.exists()) {
      if (federation.canJitUpdate() && verificationResult.hasClaims()) {
        log.info(
            "JIT updating existing user: providerId={}, subject={}",
            verificationResult.providerId(),
            verificationResult.subject());
        User mappedUser = verificationResult.toUser();
        mappedUser.setSub(existingUser.sub());
        mappedUser.setStatus(existingUser.status());
        mappedUser.applyIdentityPolicy(tenant.identityPolicyConfig());
        userRegistrator.registerOrUpdate(tenant, mappedUser);
        return mappedUser;
      }
      return existingUser;
    }

    if (!federation.jitProvisioningEnabled()) {
      throw new TokenBadRequestException(
          "invalid_grant", "User not found for the given subject_token subject");
    }

    if (!verificationResult.hasClaims()) {
      throw new TokenBadRequestException(
          "invalid_grant", "User not found and no claims available for JIT provisioning");
    }

    log.info(
        "JIT provisioning new user: providerId={}, subject={}",
        verificationResult.providerId(),
        verificationResult.subject());
    User newUser = verificationResult.toUser();
    newUser.setSub(UUID.randomUUID().toString());
    newUser.setStatus(UserStatus.FEDERATED);
    newUser.applyIdentityPolicy(context.tenant().identityPolicyConfig());
    userRegistrator.registerOrUpdate(tenant, newUser);
    return newUser;
  }

  /**
   * Issues a new access token and optional refresh token per RFC 8693 Section 2.2.1.
   *
   * <p>RFC 8693 Section 2.2.1:
   *
   * <blockquote>
   *
   * access_token REQUIRED. The security token issued by the authorization server in response to the
   * token exchange request.
   *
   * </blockquote>
   */
  private OAuthToken issueToken(
      TokenRequestContext context,
      ClientCredentials clientCredentials,
      User user,
      OAuthToken actorOAuthToken) {
    Tenant tenant = context.tenant();
    AuthorizationServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    Set<String> filteredScopes =
        clientConfiguration.filteredScope(context.scopes().toStringValues());
    Scopes scopes = new Scopes(filteredScopes);

    List<String> supportedClaims = serverConfiguration.claimsSupported();
    GrantUserinfoClaims grantUserinfoClaims =
        GrantUserinfoClaims.create(scopes, supportedClaims, new RequestedUserinfoClaims());

    CustomProperties customProperties = buildCustomProperties(context, actorOAuthToken);
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(
                context.tenantIdentifier(),
                context.requestedClientId(),
                GrantType.token_exchange,
                scopes)
            .add(customProperties)
            .add(clientConfiguration.clientAttributes())
            .add(user)
            .add(grantUserinfoClaims)
            .build();

    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);

    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);

    OAuthTokenBuilder tokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

    OAuthToken oAuthToken = tokenBuilder.build();
    oAuthTokenCommandRepository.register(tenant, oAuthToken);

    return oAuthToken;
  }

  /**
   * Builds CustomProperties with optional actor claim data for Delegation (RFC 8693 Section 4.1).
   */
  private CustomProperties buildCustomProperties(
      TokenRequestContext context, OAuthToken actorOAuthToken) {
    CustomProperties customProperties = context.customProperties();
    if (actorOAuthToken == null) {
      return customProperties;
    }

    customProperties.setValue("act_sub", actorOAuthToken.subject().value());

    return customProperties;
  }
}
