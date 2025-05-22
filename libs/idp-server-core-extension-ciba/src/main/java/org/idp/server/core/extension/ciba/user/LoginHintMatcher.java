/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.user;

import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.oidc.identity.User;

public interface LoginHintMatcher {
  boolean matches(String hint);

  Pairs<String, String> extractHints(String hint);

  User resolve(Pairs<String, String> hints);
}
