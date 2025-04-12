package org.idp.server.core.adapters.datasource.token;

import org.idp.server.core.basic.crypto.AesCipher;
import org.idp.server.core.basic.crypto.HmacHasher;
import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.token.repository.OAuthTokenRepository;

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
