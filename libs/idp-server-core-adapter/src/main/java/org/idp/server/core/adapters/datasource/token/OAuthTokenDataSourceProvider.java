package org.idp.server.core.adapters.datasource.token;

import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class OAuthTokenDataSourceProvider
    implements ApplicationComponentProvider<OAuthTokenRepository> {

  @Override
  public Class<OAuthTokenRepository> type() {
    return OAuthTokenRepository.class;
  }

  @Override
  public OAuthTokenRepository provide(ApplicationComponentDependencyContainer container) {
    AesCipher aesCipher = container.resolve(AesCipher.class);
    HmacHasher hmacHasher = container.resolve(HmacHasher.class);
    return new OAuthTokenDataSource(aesCipher, hmacHasher);
  }
}
