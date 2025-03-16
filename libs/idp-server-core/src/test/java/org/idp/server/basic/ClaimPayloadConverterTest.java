package org.idp.server.basic;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.identity.ClaimsPayload;
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

    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    ClaimsPayload claimsPayload = jsonConverter.read(claimsValue, ClaimsPayload.class);
    System.out.println(claimsPayload);
    Assertions.assertTrue(claimsPayload.exists());
  }
}
