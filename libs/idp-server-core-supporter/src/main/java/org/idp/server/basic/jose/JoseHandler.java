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


package org.idp.server.basic.jose;

import java.util.HashMap;
import java.util.Map;

/** JoseHandler */
public class JoseHandler {

  Map<JoseType, JoseContextCreator> creators;

  public JoseHandler() {
    creators = new HashMap<>();
    creators.put(JoseType.plain, new JwtContextCreator());
    creators.put(JoseType.signature, new JwsContextCreator());
    creators.put(JoseType.encryption, new JweContextCreator());
  }

  public JoseContext handle(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {
    JoseType joseType = JoseType.parse(jose);
    JoseContextCreator joseContextCreator = creators.get(joseType);
    return joseContextCreator.create(jose, publicJwks, privateJwks, secret);
  }
}
