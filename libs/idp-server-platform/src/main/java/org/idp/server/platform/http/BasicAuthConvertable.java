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

package org.idp.server.platform.http;

import java.util.Base64;

public interface BasicAuthConvertable {

  default BasicAuth convertBasicAuth(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isEmpty()) {
      return new BasicAuth();
    }

    if (!authorizationHeader.startsWith("Basic ")) {
      return new BasicAuth();
    }

    String value = authorizationHeader.substring("Basic ".length());
    byte[] decode = Base64.getUrlDecoder().decode(value);
    String decodedValue = new String(decode);
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":");
    return new BasicAuth(splitValues[0], splitValues[1]);
  }
}
