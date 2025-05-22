/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.id_token;

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
