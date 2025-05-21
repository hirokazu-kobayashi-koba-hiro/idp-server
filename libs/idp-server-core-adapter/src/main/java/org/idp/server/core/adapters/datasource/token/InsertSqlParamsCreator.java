package org.idp.server.core.adapters.datasource.token;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.EncryptedData;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.token.OAuthToken;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static List<Object> create(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    AuthorizationGrant authorizationGrant = oAuthToken.accessToken().authorizationGrant();
    List<Object> params = new ArrayList<>();
    params.add(oAuthToken.identifier().value());
    params.add(oAuthToken.tenantIdentifier().value());
    params.add(oAuthToken.tokenIssuer().value());
    params.add(oAuthToken.tokenType().name());
    params.add(toEncryptedJson(oAuthToken.accessTokenEntity().value(), aesCipher));
    params.add(hmacHasher.hash(oAuthToken.accessTokenEntity().value()));

    if (authorizationGrant.hasUser()) {
      params.add((authorizationGrant.user().sub()));
      params.add(toJson(authorizationGrant.user()));
    } else {
      params.add(null);
      params.add(null);
    }

    params.add(toJson(authorizationGrant.authentication()));
    params.add(authorizationGrant.requestedClientId().value());
    params.add(toJson(authorizationGrant.client()));
    params.add(authorizationGrant.grantType().name());
    params.add(authorizationGrant.scopes().toStringValues());

    if (authorizationGrant.hasIdTokenClaims()) {
      params.add(authorizationGrant.idTokenClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasUserinfoClaim()) {
      params.add(authorizationGrant.userinfoClaims().toStringValues());
    } else {
      params.add("");
    }

    if (authorizationGrant.hasCustomProperties()) {
      params.add(toJson(authorizationGrant.customProperties().values()));
    } else {
      params.add("{}");
    }

    if (authorizationGrant.hasAuthorizationDetails()) {
      params.add(toJson(authorizationGrant.authorizationDetails().toMapValues()));
    } else {
      params.add("[]");
    }

    params.add(oAuthToken.accessToken().expiresIn().toStringValue());
    params.add(oAuthToken.accessToken().expiredAt().toStringValue());
    params.add(oAuthToken.accessToken().createdAt().toStringValue());

    if (oAuthToken.hasRefreshToken()) {
      params.add(toEncryptedJson(oAuthToken.refreshTokenEntity().value(), aesCipher));
      params.add(hmacHasher.hash(oAuthToken.refreshTokenEntity().value()));
      params.add(oAuthToken.refreshToken().createdAt().toStringValue());
      params.add(oAuthToken.refreshToken().expiredAt().toStringValue());
    } else {
      params.add("{}");
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

  private static String toEncryptedJson(String value, AesCipher aesCipher) {
    EncryptedData encrypted = aesCipher.encrypt(value);
    return toJson(encrypted);
  }
}
