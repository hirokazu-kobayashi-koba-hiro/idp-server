/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
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
