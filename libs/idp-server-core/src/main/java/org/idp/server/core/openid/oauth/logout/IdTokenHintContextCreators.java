/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.logout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.platform.exception.UnSupportedException;

/**
 * IdTokenHintContextCreators
 *
 * <p>Registry for id_token_hint context creators. Maps each pattern to its corresponding creator
 * implementation.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class IdTokenHintContextCreators {

  Map<IdTokenHintPattern, IdTokenHintContextCreator> values;

  public IdTokenHintContextCreators() {
    values = new HashMap<>();
    values.put(IdTokenHintPattern.JWS, new JwsIdTokenHintContextCreator());
    values.put(IdTokenHintPattern.SYMMETRIC_JWE, new SymmetricJweIdTokenHintContextCreator());
    values.put(IdTokenHintPattern.ASYMMETRIC_JWE, new AsymmetricJweIdTokenHintContextCreator());
  }

  /**
   * Gets the creator for the specified pattern.
   *
   * @param pattern the id_token_hint pattern
   * @return the corresponding creator
   * @throws UnSupportedException if pattern is not supported
   */
  public IdTokenHintContextCreator get(IdTokenHintPattern pattern) {
    IdTokenHintContextCreator creator = values.get(pattern);
    if (Objects.isNull(creator)) {
      throw new UnSupportedException(
          String.format("not supported id_token_hint pattern (%s)", pattern.name()));
    }
    return creator;
  }
}
