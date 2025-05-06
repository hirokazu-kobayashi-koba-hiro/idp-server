package org.idp.server.basic.jose;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** JoseContextTest */
public class JoseContextTest {

  @Test
  void createAndVerify() throws JoseInvalidException, JsonWebKeyInvalidException {
    JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();
    Map<String, Object> claims = Map.of("client_id", "123", "redirect_uri", "https://example.com/callback", "scope", "openid phone email address", "state", "state");
    String jwkValue = """
        {
              "kty": "EC",
              "d": "Ae0ukDMXt5y1eLU0ZlRFhL-kxO2StbgEV1vqWU7JajeLr5NaJhh_gDlA3UHOot38Sv-xIV4nWE5AEa3T-xWWp6no",
              "use": "sig",
              "crv": "P-521",
              "kid": "request_object",
              "x": "ATSp2c5ZsWleJRoBQV6ZWu2knJrgxFGIC_9MuZEd9-XNiRczdphUOLwLQpPMoMSkTY3mU-ku9YmJe8Ue4TuthrHv",
              "y": "Ae4y3oTp471l5oS2tUrClQjjCfmfjmore3lK8614MBeTUmPCM6YAp8Bw_qZ-QKpkqvOMAqqjUeaXJqQSHdQC2NPJ",
              "alg": "ES512"
          }
        """;
    JsonWebSignature jsonWebSignature = jsonWebSignatureFactory.createWithAsymmetricKey(claims, Map.of(), jwkValue);
    String requestObject = jsonWebSignature.serialize();
    System.out.println(requestObject);
    String jwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "use": "sig",
                    "crv": "P-521",
                    "kid": "request_object",
                    "x": "ATSp2c5ZsWleJRoBQV6ZWu2knJrgxFGIC_9MuZEd9-XNiRczdphUOLwLQpPMoMSkTY3mU-ku9YmJe8Ue4TuthrHv",
                    "y": "Ae4y3oTp471l5oS2tUrClQjjCfmfjmore3lK8614MBeTUmPCM6YAp8Bw_qZ-QKpkqvOMAqqjUeaXJqQSHdQC2NPJ",
                    "alg": "ES512"
                }
            ]
        }
        """;
    JoseHandler joseHandler = new JoseHandler();
    JoseContext joseContext = joseHandler.handle(requestObject, jwks, "", "");
    System.out.println(joseContext.claims().payload());
    joseContext.verifySignature();
  }
}
