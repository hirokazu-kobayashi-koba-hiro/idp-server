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

package org.idp.server.core.openid.oauth.type.oauth;

/**
 * 7.1. Access Token Types
 *
 * <p>The access token type provides the client with the information required to successfully
 * utilize the access token to make a protected resource request (along with type-specific
 * attributes). The client MUST NOT use an access token if it does not understand the token type.
 *
 * <p>For example, the "bearer" token type defined in [RFC6750] is utilized by simply including the
 * access token string in the request:
 *
 * <p>GET /resource/1 HTTP/1.1 Host: example.com Authorization: Bearer mF_9.B5f-4.1JqM
 *
 * <p>while the "mac" token type defined in [OAuth-HTTP-MAC] is utilized by issuing a Message
 * Authentication Code (MAC) key together with the access token that is used to sign certain
 * components of the HTTP requests:
 *
 * <p>GET /resource/1 HTTP/1.1 Host: example.com Authorization: MAC id="h480djs93hd8",
 * nonce="274312:dj83hs9s", mac="kDZvddkndxvhGRXZhvuDjEWhGeE="
 *
 * <p>The above examples are provided for illustration purposes only. Developers are advised to
 * consult the [RFC6750] and [OAuth-HTTP-MAC] specifications before use.
 *
 * <p>Each access token type definition specifies the additional attributes (if any) sent to the
 * client together with the "access_token" response parameter. It also defines the HTTP
 * authentication method used to include the access token when making a protected resource request.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-7.1">7.1. Access Token Types</a>
 */
public enum TokenType {
  Bearer,
  DPoP,
  undefined;

  public boolean isDefined() {
    return this != undefined;
  }
}
