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

import org.idp.server.core.openid.session.OPSessionIdentifier;

/**
 * LogoutContext
 *
 * <p>Encapsulates the context needed for logout operations. This reduces the number of arguments
 * passed to logout methods and provides a clear contract for what information is needed.
 */
public class LogoutContext {

  private final OPSessionIdentifier opSessionId;
  private final String sub;
  private final String initiatorClientId;
  private final String issuer;
  private final String signingKeyJwks;
  private final String signingAlgorithm;

  public LogoutContext(
      OPSessionIdentifier opSessionId,
      String sub,
      String initiatorClientId,
      String issuer,
      String signingKeyJwks,
      String signingAlgorithm) {
    this.opSessionId = opSessionId;
    this.sub = sub;
    this.initiatorClientId = initiatorClientId;
    this.issuer = issuer;
    this.signingKeyJwks = signingKeyJwks;
    this.signingAlgorithm = signingAlgorithm;
  }

  public OPSessionIdentifier opSessionId() {
    return opSessionId;
  }

  public String sub() {
    return sub;
  }

  public String initiatorClientId() {
    return initiatorClientId;
  }

  public String issuer() {
    return issuer;
  }

  public String signingKeyJwks() {
    return signingKeyJwks;
  }

  public String signingAlgorithm() {
    return signingAlgorithm;
  }
}
