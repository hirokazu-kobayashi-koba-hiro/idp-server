package org.idp.server.oauth.pkce;

import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.hash.MessageDigestable;
import org.idp.server.type.pkce.CodeChallenge;
import org.idp.server.type.pkce.CodeVerifier;

public class CodeChallengeCalculator implements MessageDigestable, Base64Codeable {

  public CodeChallenge calculateWithPlain(CodeVerifier codeVerifier) {
    return new CodeChallenge(codeVerifier.value());
  }

  public CodeChallenge calculateWithS256(CodeVerifier codeVerifier) {
    byte[] bytes = digestWithSha256(codeVerifier.value());
    String encodedValue = encodeWithUrlSafe(bytes);
    return new CodeChallenge(encodedValue);
  }
}
