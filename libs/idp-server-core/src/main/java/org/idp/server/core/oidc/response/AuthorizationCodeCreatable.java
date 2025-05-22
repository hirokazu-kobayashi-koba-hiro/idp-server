/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.response;

import org.idp.server.basic.random.RandomStringGenerator;
import org.idp.server.basic.type.oauth.AuthorizationCode;

public interface AuthorizationCodeCreatable {

  default AuthorizationCode createAuthorizationCode() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);
    return new AuthorizationCode(randomStringGenerator.generate());
  }
}
