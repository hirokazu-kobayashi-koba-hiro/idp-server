package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGranted;
import org.idp.server.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.IdTokenCustomClaims;
import org.idp.server.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.TokenRequestCodeGrantValidator;
import org.idp.server.token.verifier.TokenRequestCodeGrantVerifier;
import org.idp.server.type.extension.GrantFlow;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

/**
 * 4.1.3. Access Token Request authorization code handling
 *
 * <p>The client makes a request to the token endpoint by sending the following parameters using the
 * "application/x-www-form-urlencoded" format per Appendix B with a character encoding of UTF-8 in
 * the HTTP request entity-body:
 *
 * <p>grant_type REQUIRED. Value MUST be set to "authorization_code".
 *
 * <p>code REQUIRED. The authorization code received from the authorization server.
 *
 * <p>redirect_uri REQUIRED, if the "redirect_uri" parameter was included in the authorization
 * request as described in Section 4.1.1, and their values MUST be identical.
 *
 * <p>client_id REQUIRED, if the client is not authenticating with the authorization server as
 * described in Section 3.2.1.
 *
 * <p>If the client type is confidential or the client was issued client credentials (or assigned
 * other authentication requirements), the client MUST authenticate with the authorization server as
 * described in Section 3.2.1.
 *
 * <p>For example, the client makes the following HTTP request using TLS (with extra line breaks for
 * display purposes only):
 *
 * <p>POST /token HTTP/1.1 Host: server.example.com Authorization: Basic
 * czZCaGRSa3F0MzpnWDFmQmF0M2JW Content-Type: application/x-www-form-urlencoded
 *
 * <p>grant_type=authorization_code&code=SplxlOBeZQQYbYS6WxSbIA
 * &redirect_uri=https%3A%2F%2Fclient%2Eexample%2Ecom%2Fcb
 *
 * <p>The authorization server MUST:
 *
 * <p>o require client authentication for confidential clients or for any client that was issued
 * client credentials (or with other authentication requirements),
 *
 * <p>o authenticate the client if client authentication is included,
 *
 * <p>o ensure that the authorization code was issued to the authenticated confidential client, or
 * if the client is public, ensure that the code was issued to "client_id" in the request,
 *
 * <p>o verify that the authorization code is valid, and
 *
 * <p>o ensure that the "redirect_uri" parameter is present if the "redirect_uri" parameter was
 * included in the initial authorization request as described in Section 4.1.1, and if included
 * ensure that their values are identical.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
 *     Request</a>
 */
public class AuthorizationCodeGrantService
    implements OAuthTokenCreationService,
        AccessTokenCreatable,
        RefreshTokenCreatable,
        IdTokenCreatable {

  AuthorizationRequestRepository authorizationRequestRepository;
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;

  public AuthorizationCodeGrantService(
      AuthorizationRequestRepository authorizationRequestRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
  }

  @Override
  public OAuthToken create(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    TokenRequestCodeGrantValidator validator =
        new TokenRequestCodeGrantValidator(tokenRequestContext.parameters());
    validator.validate();

    AuthorizationCode code = tokenRequestContext.code();
    AuthorizationCodeGrant authorizationCodeGrant = authorizationCodeGrantRepository.find(code);
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.find(
            authorizationCodeGrant.authorizationRequestIdentifier());

    TokenRequestCodeGrantVerifier verifier =
        new TokenRequestCodeGrantVerifier(
            tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    verifier.verify();

    ServerConfiguration serverConfiguration = tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    AuthorizationGrant authorizationGrant = authorizationCodeGrant.authorizationGrant();
    AccessToken accessToken =
        createAccessToken(
            authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);

    if (authorizationRequest.isOidcProfile()) {
      IdTokenCustomClaims idTokenCustomClaims =
          new IdTokenCustomClaimsBuilder()
              .add(authorizationCodeGrant.authorizationCode())
              .add(accessToken.accessTokenValue())
              .add(authorizationRequest.nonce())
              .add(authorizationRequest.state())
              .build();
      IdToken idToken =
          createIdToken(
              authorizationGrant.user(),
              authorizationCodeGrant.authentication(),
              GrantFlow.authorization_code,
              authorizationCodeGrant.scopes(),
              authorizationRequest.claimsPayload().idToken(),
              idTokenCustomClaims,
              serverConfiguration,
              clientConfiguration);
      oAuthTokenBuilder.add(idToken);
    }

    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(
            authorizationGrantedIdentifier, authorizationCodeGrant.authorizationGrant());
    authorizationGrantedRepository.register(authorizationGranted);

    OAuthToken oAuthToken = oAuthTokenBuilder.build();

    oAuthTokenRepository.register(oAuthToken);
    authorizationCodeGrantRepository.delete(authorizationCodeGrant);

    return oAuthToken;
  }
}
