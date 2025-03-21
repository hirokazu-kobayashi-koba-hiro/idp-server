package org.idp.server.core.oauth.identity;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
