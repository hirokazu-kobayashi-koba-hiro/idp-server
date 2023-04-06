package org.idp.server.basic.http;

import java.util.Base64;
import java.util.Objects;

public interface BasicAuthConvertable {

  default boolean isBasicAuth(String authorizationHeader) {
    if (Objects.isNull(authorizationHeader) || authorizationHeader.isEmpty()) {
      return false;
    }
    return authorizationHeader.startsWith("Basic ");
  }

  default BasicAuth convertBasicAuth(String authorizationHeader) {
    byte[] decode = Base64.getUrlDecoder().decode(authorizationHeader);
    String decodedValue = new String(decode);
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":");
    return new BasicAuth(splitValues[0], splitValues[1]);
  }
}
