/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.service;

import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenRequestContext;

public interface OAuthTokenCreationService {

  GrantType grantType();

  OAuthToken create(TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials);
}
