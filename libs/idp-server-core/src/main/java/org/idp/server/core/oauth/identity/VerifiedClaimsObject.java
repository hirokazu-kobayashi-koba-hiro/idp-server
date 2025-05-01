package org.idp.server.core.oauth.identity;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.json.JsonReadable;

public class VerifiedClaimsObject implements JsonReadable {
  Map<String, Object> verification;
  Map<String, Object> claims;

  public VerifiedClaimsObject() {}

  public VerifiedClaimsObject(Map<String, Object> verification, Map<String, Object> claims) {
    this.verification = verification;
    this.claims = claims;
  }

  public JsonNodeWrapper verificationNodeWrapper() {
    return JsonNodeWrapper.fromObject(verification);
  }

  public JsonNodeWrapper claimsNodeWrapper() {
    return JsonNodeWrapper.fromObject(claims);
  }
}
