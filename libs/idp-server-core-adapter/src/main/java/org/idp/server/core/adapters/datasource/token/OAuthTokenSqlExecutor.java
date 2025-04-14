package org.idp.server.core.adapters.datasource.token;

import java.util.Map;
import org.idp.server.core.basic.crypto.AesCipher;
import org.idp.server.core.basic.crypto.HmacHasher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;

public interface OAuthTokenSqlExecutor {

  void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);

  Map<String, String> selectOneByAccessToken(
      Tenant tenant,
      AccessTokenEntity accessTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher);

  Map<String, String> selectOneByRefreshToken(
      Tenant tenant,
      RefreshTokenEntity refreshTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher);

  void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);
}
