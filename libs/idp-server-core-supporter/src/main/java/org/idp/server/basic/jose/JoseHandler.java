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

  public JoseContext handle(String jose, String publicJwks, String privateJwks, String secret) throws JoseInvalidException {
    JoseType joseType = JoseType.parse(jose);
    JoseContextCreator joseContextCreator = creators.get(joseType);
    return joseContextCreator.create(jose, publicJwks, privateJwks, secret);
  }
}
