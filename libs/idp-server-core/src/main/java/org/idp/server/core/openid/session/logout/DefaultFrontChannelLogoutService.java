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

package org.idp.server.core.openid.session.logout;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.session.ClientSession;
import org.idp.server.core.openid.session.ClientSessions;

public class DefaultFrontChannelLogoutService implements FrontChannelLogoutService {

  @Override
  public List<FrontChannelLogoutIframe> buildLogoutIframes(
      String issuer, ClientSessions clientSessions, ClientLogoutUriResolver resolver) {
    List<FrontChannelLogoutIframe> iframes = new ArrayList<>();

    for (ClientSession session : clientSessions) {
      String logoutUri = resolver.resolveFrontChannelLogoutUri(session.clientId());
      if (logoutUri == null || logoutUri.isEmpty()) {
        continue;
      }

      boolean sessionRequired = resolver.isFrontChannelLogoutSessionRequired(session.clientId());
      FrontChannelLogoutIframe iframe =
          FrontChannelLogoutIframe.create(
              session.clientId(), logoutUri, issuer, session.sid(), sessionRequired);
      iframes.add(iframe);
    }

    return iframes;
  }
}
