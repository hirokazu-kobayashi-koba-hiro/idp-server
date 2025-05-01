package org.idp.server.core.authentication.fidouaf;

import java.io.Serializable;
import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

public class FidoUafChallengeResponse implements Serializable, JsonReadable {

  int statusCode;
  Map<String, Object> contents;

  public FidoUafChallengeResponse() {}

  public FidoUafChallengeResponse(int statusCode, Map<String, Object> contents) {
    this.statusCode = statusCode;
    this.contents = contents;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> contents() {
    return contents;
  }

  public boolean isSuccess() {
    return statusCode < 400;
  }

  public boolean isError() {
    return statusCode >= 400;
  }
}
