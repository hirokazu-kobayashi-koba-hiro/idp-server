package org.idp.server.core.adapters.datasource.token;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.crypto.AesCipher;
import org.idp.server.core.basic.crypto.HmacHasher;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;

public class OAuthTokenDataSource implements OAuthTokenRepository {

  OAuthTokenSqlExecutors executors;
  AesCipher aesCipher;
  HmacHasher hmacHasher;

  public OAuthTokenDataSource(AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executors = new OAuthTokenSqlExecutors();
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public void register(Tenant tenant, OAuthToken oAuthToken) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(oAuthToken, aesCipher, hmacHasher);
  }

  @Override
  public OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.dialect());
    Map<String, String> stringMap = executor.selectOneByAccessToken(tenant, accessTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }

  @Override
  public OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.dialect());
    Map<String, String> stringMap = executor.selectOneByRefreshToken(tenant, refreshTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }

  @Override
  public void delete(Tenant tenant, OAuthToken oAuthToken) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.dialect());
    executor.delete(oAuthToken, aesCipher, hmacHasher);
  }
}
