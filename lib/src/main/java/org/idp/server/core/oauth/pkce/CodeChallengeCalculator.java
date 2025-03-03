package org.idp.server.core.oauth.pkce;

import org.idp.server.core.basic.base64.Base64Codeable;
import org.idp.server.core.basic.hash.MessageDigestable;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeVerifier;

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
