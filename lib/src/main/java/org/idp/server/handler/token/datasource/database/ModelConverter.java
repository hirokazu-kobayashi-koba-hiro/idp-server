package org.idp.server.handler.token.datasource.database;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.rar.AuthorizationDetail;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.oauth.token.RefreshToken;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.OAuthTokenBuilder;
import org.idp.server.token.OAuthTokenIdentifier;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.Claims;
import org.idp.server.type.oidc.ClaimsValue;
import org.idp.server.type.oidc.IdToken;

class ModelConverter {

  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static OAuthToken convert(Map<String, String> stringMap) {
    OAuthTokenIdentifier id = new OAuthTokenIdentifier(stringMap.get("id"));
    TokenIssuer tokenIssuer = new TokenIssuer(stringMap.get("token_issuer"));
    TokenType tokenType = TokenType.valueOf(stringMap.get("token_type"));
    AccessTokenValue accessTokenValue = new AccessTokenValue(stringMap.get("access_token"));
    User user = jsonParser.read(stringMap.get("user_payload"), User.class);
    Authentication authentication =
        jsonParser.read(stringMap.get("authentication"), Authentication.class);
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Scopes scopes = new Scopes(stringMap.get("scopes"));
    CustomProperties customProperties = new CustomProperties();
    ClaimsPayload claimsPayload = convertClaimsPayload(stringMap.get("claims"));
    AuthorizationDetails authorizationDetails =
        convertAuthorizationDetails(stringMap.get("authorization_details"));
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(
            user,
            authentication,
            clientId,
            scopes,
            claimsPayload,
            customProperties,
            authorizationDetails);
    ExpiresIn expiresIn = new ExpiresIn(stringMap.get("expires_in"));
    ExpiredAt accessTokenExpiredAt = new ExpiredAt(stringMap.get("access_token_expired_at"));
    CreatedAt accessTokenCreatedAt = new CreatedAt(stringMap.get("access_token_created_at"));
    AccessToken accessToken =
        new AccessToken(
            tokenIssuer,
            tokenType,
            accessTokenValue,
            authorizationGrant,
            accessTokenCreatedAt,
            expiresIn,
            accessTokenExpiredAt);
    RefreshTokenValue refreshTokenValue = new RefreshTokenValue(stringMap.get("refresh_token"));
    ExpiredAt refreshTokenExpiredAt = new ExpiredAt(stringMap.get("refresh_token_expired_at"));
    CreatedAt refreshTokenCreatedAt = new CreatedAt(stringMap.get("refresh_token_created_at"));
    RefreshToken refreshToken =
        new RefreshToken(refreshTokenValue, refreshTokenCreatedAt, refreshTokenExpiredAt);
    IdToken idToken = new IdToken(stringMap.get("id_token"));

    return new OAuthTokenBuilder(id).add(accessToken).add(refreshToken).add(idToken).build();
  }

  private static ClaimsPayload convertClaimsPayload(String value) {
    if (value.isEmpty()) {
      return new ClaimsPayload();
    }
    try {
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      return jsonParser.read(value, ClaimsPayload.class);
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
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      List list = jsonParser.read(value, List.class);
      List<Map> details = (List<Map>) list;
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
