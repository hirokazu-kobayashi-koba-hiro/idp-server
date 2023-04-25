package org.idp.server.basic;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClaimPayloadConverterTest {

  @Test
  void convertable() {
    String claimsValue =
        """
                {
                   "userinfo":
                    {
                     "given_name": {"essential": true},
                     "nickname": null,
                     "email": {"essential": true},
                     "email_verified": {"essential": true},
                     "picture": null,
                     "http://example.info/claimsValue/groups": null
                    },
                   "id_token":
                    {
                     "auth_time": {"essential": true},
                     "acr": {"values": ["urn:mace:incommon:iap:silver"] }
                    }
                  }
                """;

    JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
    ClaimsPayload claimsPayload = jsonParser.read(claimsValue, ClaimsPayload.class);
    System.out.println(claimsPayload);
    Assertions.assertTrue(claimsPayload.exists());
  }
}
