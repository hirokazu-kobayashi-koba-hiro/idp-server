package org.idp.server.basic.jose;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.Header;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64URL;
import java.text.ParseException;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;

/** JoseType */
public enum JoseType {
  plain,
  signature,
  encryption;

  public static JoseType parse(String jose) throws JoseInvalidException {
    try {
      String headerValue = jose.split("\\.")[0];
      Base64URL header = new Base64URL(headerValue);
      JsonConverter jsonConverter = JsonConverter.create();
      Map<String, Object> headerPayload = jsonConverter.read(header.decodeToString(), Map.class);
      Algorithm alg = Header.parseAlgorithm(headerPayload);

      if (alg.equals(Algorithm.NONE)) {
        return plain;
      } else if (alg instanceof JWSAlgorithm) {
        return signature;
      } else if (alg instanceof JWEAlgorithm) {
        return encryption;
      } else {
        throw new JoseInvalidException("Unexpected algorithm type: " + alg);
      }
    } catch (ParseException e) {
      throw new JoseInvalidException("parse failed, invalid jose header", e);
    }
  }

  public boolean isPlain() {
    return this == plain;
  }
}
