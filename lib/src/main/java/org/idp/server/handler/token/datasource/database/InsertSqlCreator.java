package org.idp.server.handler.token.datasource.database;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.token.OAuthToken;

class InsertSqlCreator {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static String createInsert(OAuthToken oAuthToken) {
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    InsertSqlBuilder builder =
        new InsertSqlBuilder(oAuthToken.identifier().value())
            .setTokenIssuer(oAuthToken.tokenIssuer().value())
            .setTokenTYpe(oAuthToken.tokenType().name())
            .setAccessToken(oAuthToken.accessTokenValue().value())
            .setAuthentication(toJson(authorizationGrant.authentication()))
            .setClientId(authorizationGrant.clientId().value())
            .setScopes(authorizationGrant.scopes().toStringValues())
            .setExpiredIn(oAuthToken.accessToken().expiresIn().toStringValue())
            .setAccessTokenExpiredAt(oAuthToken.accessToken().expiredAt().toStringValue())
            .setAccessTokenCreatedAt(oAuthToken.accessToken().createdAt().toStringValue());
    if (authorizationGrant.hasUser()) {
      builder
          .setUserId(authorizationGrant.user().sub())
          .setUserPayload(toJson(authorizationGrant.user()));
    }
    if (authorizationGrant.hasClaim()) {
      builder.setClaims(toJson(authorizationGrant.claimsPayload()));
    }
    if (authorizationGrant.hasCustomProperties()) {
      builder.setCustomProperties(toJson(authorizationGrant.customProperties().values()));
    }
    if (authorizationGrant.hasAuthorizationDetails()) {
      builder.setAuthorizationDetails(
          toJson(authorizationGrant.authorizationDetails().toMapValues()));
    }
    if (oAuthToken.hasRefreshToken()) {
      builder
          .setRefreshToken(oAuthToken.refreshTokenValue().value())
          .setRefreshTokenCreatedAt(oAuthToken.refreshToken().createdAt().toStringValue())
          .setRefreshTokenExpiredAt(oAuthToken.refreshToken().expiredAt().toStringValue());
    }
    if (oAuthToken.hasIdToken()) {
      builder.setIdToken(oAuthToken.idToken().value());
    }
    if (oAuthToken.hasClientCertification()) {
      builder.setClientCertificationThumbprint(
          oAuthToken.accessToken().clientCertificationThumbprint().value());
    }
    if (oAuthToken.hasCNonce()) {
      builder.setCNonce(oAuthToken.cNonce().value());
    }
    if (oAuthToken.hasCNonceExpiresIn()) {
      builder.setCNonceExpiresIn(oAuthToken.cNonceExpiresIn().toStringValue());
    }
    return builder.build();
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
