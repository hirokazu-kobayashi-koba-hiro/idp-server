package org.idp.server.core.identity;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
