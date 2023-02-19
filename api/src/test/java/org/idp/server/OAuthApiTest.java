package org.idp.server;

import java.util.Map;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.type.OAuthRequestResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OAuthApiTest {

  OAuthApi sut = new OAuthApi();

  @Test
  void bad_request_duplicateValue() {
    Map<String, String[]> params =
        Map.of(
            "client_id",
            new String[] {"123", "345"},
            "response_type",
            new String[] {"code", "token"});
    OAuthRequest oAuthRequest = new OAuthRequest(params, "issuer");
    OAuthRequestResponse requestResponse = sut.request(oAuthRequest);
    Assertions.assertEquals(OAuthRequestResult.BAD_REQUEST, requestResponse.getResult());
  }
}
