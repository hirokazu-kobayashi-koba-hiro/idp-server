/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.token;

import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
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
