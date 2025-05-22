/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.repository;

import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionKey;

public interface OAuthSessionRepository {

  void register(OAuthSession oAuthSession);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void update(OAuthSession oAuthSession);

  void delete(OAuthSessionKey oAuthSessionKey);
}
