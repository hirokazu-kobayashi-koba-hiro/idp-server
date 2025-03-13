package org.idp.server.core.adapters.datasource.token.database;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.token.OAuthToken;

import java.util.ArrayList;
import java.util.List;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static List<Object> create(OAuthToken oAuthToken) {
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    List<Object> params = new ArrayList<>();
    params.add(oAuthToken.identifier().value());
    params.add(oAuthToken.tokenIssuer().value());
    params.add(oAuthToken.tokenType().name());
    params.add(oAuthToken.accessTokenValue().value());

    if (authorizationGrant.hasUser()) {
      params.add((authorizationGrant.user().sub()));
      params.add(toJson(authorizationGrant.user()));
    } else {
      params.add("");
      params.add("");
    }

    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.clientId().value());
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasClaim()) {
      params.add(toJson(authorizationGrant.claimsPayload()));
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("");
    }

    params.add(oAuthToken.accessToken().expiresIn().toStringValue());
    params.add(oAuthToken.accessToken().expiredAt().toStringValue());
    params.add(oAuthToken.accessToken().createdAt().toStringValue());

    if (oAuthToken.hasRefreshToken()) {
      params.add(oAuthToken.refreshTokenValue().value());
      params.add(oAuthToken.refreshToken().createdAt().toStringValue());
      params.add(oAuthToken.refreshToken().expiredAt().toStringValue());
    } else {
      params.add("");
      params.add("");
      params.add("");
    }

    if (oAuthToken.hasIdToken()) {
      params.add(oAuthToken.idToken().value());
    } else {
      params.add("");
    }
    if (oAuthToken.hasClientCertification()) {
      params.add(oAuthToken.accessToken().clientCertificationThumbprint().value());
    } else {
      params.add("");
    }

    if (oAuthToken.hasCNonce()) {
      params.add(oAuthToken.cNonce().value());
    } else {
      params.add("");
    }

    if (oAuthToken.hasCNonceExpiresIn()) {
      params.add(oAuthToken.cNonceExpiresIn().toStringValue());
    } else {
      params.add("");
    }

    return params;
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
