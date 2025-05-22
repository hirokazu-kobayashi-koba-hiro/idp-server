/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.pkce;

import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.hash.MessageDigestable;
import org.idp.server.basic.type.pkce.CodeChallenge;
import org.idp.server.basic.type.pkce.CodeVerifier;

public class CodeChallengeCalculator implements MessageDigestable, Base64Codeable {

  CodeVerifier codeVerifier;

  public CodeChallengeCalculator(CodeVerifier codeVerifier) {
    this.codeVerifier = codeVerifier;
  }

  public CodeChallenge calculateWithPlain() {
    return new CodeChallenge(codeVerifier.value());
  }

  public CodeChallenge calculateWithS256() {
    byte[] bytes = digestWithSha256(codeVerifier.value());
    String encodedValue = encodeWithUrlSafe(bytes);
    return new CodeChallenge(encodedValue);
  }
}
