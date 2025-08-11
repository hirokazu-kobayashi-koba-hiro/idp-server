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

package org.idp.server.core.openid.oauth.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.oauth.OAuthRequestPattern;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactories;
import org.idp.server.core.openid.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.platform.exception.UnSupportedException;

public class OAuthRequestContextCreators {
  Map<OAuthRequestPattern, OAuthRequestContextCreator> values;

  public OAuthRequestContextCreators(
      RequestObjectGateway requestObjectGateway,
      AuthorizationRequestRepository authorizationRequestRepository,
      RequestObjectFactories requestObjectFactories) {
    values = new HashMap<>();
    values.put(OAuthRequestPattern.NORMAL, new NormalPatternContextCreator());
    values.put(
        OAuthRequestPattern.REQUEST_OBJECT,
        new RequestObjectPatternContextCreator(requestObjectFactories));
    values.put(
        OAuthRequestPattern.REQUEST_URI,
        new RequestUriPatternContextCreator(requestObjectGateway, requestObjectFactories));
    values.put(
        OAuthRequestPattern.PUSHED_REQUEST_URI,
        new PushedRequestUriPatternContextCreator(authorizationRequestRepository));
  }

  public OAuthRequestContextCreator get(OAuthRequestPattern pattern) {
    OAuthRequestContextCreator oAuthRequestContextCreator = values.get(pattern);
    if (Objects.isNull(oAuthRequestContextCreator)) {
      throw new UnSupportedException(
          String.format("not support request pattern (%s)", pattern.name()));
    }
    return oAuthRequestContextCreator;
  }
}
