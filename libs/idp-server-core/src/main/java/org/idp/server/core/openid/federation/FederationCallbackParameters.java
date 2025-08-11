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

package org.idp.server.core.openid.federation;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.ArrayValueMap;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;

/** FederationCallbackParameters */
public class FederationCallbackParameters {
  ArrayValueMap values;

  public FederationCallbackParameters() {
    this.values = new ArrayValueMap();
  }

  public FederationCallbackParameters(ArrayValueMap values) {
    this.values = values;
  }

  public FederationCallbackParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public State state() {
    return new State(getValueOrEmpty("state"));
  }

  public boolean hasState() {
    return contains("state");
  }

  public String getValueOrEmpty(String key) {
    return values.getFirstOrEmpty(key);
  }

  boolean contains(String key) {
    return values.contains(key);
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(getValueOrEmpty("iss"));
  }

  public boolean hasTokenIssuer() {
    return contains("iss");
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  public String code() {
    return getValueOrEmpty("code");
  }
}
