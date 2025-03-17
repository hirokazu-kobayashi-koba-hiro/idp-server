package org.idp.server.core.adapters.datasource.token.database;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.rar.AuthorizationDetail;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.oauth.token.RefreshToken;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.OAuthTokenBuilder;
import org.idp.server.core.token.OAuthTokenIdentifier;
import org.idp.server.core.type.extension.CreatedAt;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.IdToken;
import org.idp.server.core.type.verifiablecredential.CNonce;
import org.idp.server.core.type.verifiablecredential.CNonceExpiresIn;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static OAuthToken convert(Map<String, String> stringMap) {
    OAuthTokenIdentifier id = new OAuthTokenIdentifier(stringMap.get("id"));
    TokenIssuer tokenIssuer = new TokenIssuer(stringMap.get("token_issuer"));
    TokenType tokenType = TokenType.valueOf(stringMap.get("token_type"));
    AccessTokenEntity accessTokenEntity = new AccessTokenEntity(stringMap.get("access_token"));
    // TODO refactor
    User user;
    if (Objects.nonNull(stringMap.get("user_payload"))
        && !stringMap.get("user_payload").isEmpty()) {
      user = jsonConverter.read(stringMap.get("user_payload"), User.class);
    } else {
      user = new User();
    }
    Authentication authentication =
        jsonConverter.read(stringMap.get("authentication"), Authentication.class);
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    ClaimsPayload claimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrantBuilder(clientId, scopes)
            .add(user)
            .add(authentication)
            .add(claimsPayload)
            .add(customProperties)
            .add(authorizationDetails)
            .build();

    ClientCertificationThumbprint thumbprint =
        new ClientCertificationThumbprint(stringMap.get("client_certification_thumbprint"));
    ExpiresIn expiresIn = new ExpiresIn(stringMap.get("expires_in"));
    ExpiredAt accessTokenExpiredAt = new ExpiredAt(stringMap.get("access_token_expired_at"));
    CreatedAt accessTokenCreatedAt = new CreatedAt(stringMap.get("access_token_created_at"));

    OAuthTokenBuilder oAuthTokenBuilder = new OAuthTokenBuilder(id);

    AccessToken accessToken =
        new AccessToken(
            tokenIssuer,
            tokenType,
            accessTokenEntity,
            authorizationGrant,
            thumbprint,
            accessTokenCreatedAt,
            expiresIn,
            accessTokenExpiredAt);
    if (!Objects.nonNull(stringMap.get("refresh_token"))
        && !stringMap.get("refresh_token").isEmpty()) {
      RefreshTokenEntity refreshTokenEntity =
          new RefreshTokenEntity(stringMap.get("refresh_token"));
      ExpiredAt refreshTokenExpiredAt = new ExpiredAt(stringMap.get("refresh_token_expired_at"));
      CreatedAt refreshTokenCreatedAt = new CreatedAt(stringMap.get("refresh_token_created_at"));
      RefreshToken refreshToken =
          new RefreshToken(refreshTokenEntity, refreshTokenCreatedAt, refreshTokenExpiredAt);
      oAuthTokenBuilder.add(refreshToken);
    }

    IdToken idToken = new IdToken(stringMap.get("id_token"));
    CNonce cNonce = new CNonce(stringMap.get("c_nonce"));
    CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn(stringMap.get("c_nonce_expires_in"));

    return oAuthTokenBuilder.add(accessToken).add(idToken).add(cNonce).add(cNonceExpiresIn).build();
  }

  private static ClaimsPayload convertClaimsPayload(String value) {
    if (value.isEmpty()) {
      return new ClaimsPayload();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      return jsonConverter.read(value, ClaimsPayload.class);
    } catch (Exception exception) {
      return new ClaimsPayload();
    }
  }

  // TODO
  private static AuthorizationDetails convertAuthorizationDetails(String value) {
    if (value.isEmpty()) {
      return new AuthorizationDetails();
    }
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      List list = jsonConverter.read(value, List.class);
      List<Map> details = (List<Map>) list;
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
