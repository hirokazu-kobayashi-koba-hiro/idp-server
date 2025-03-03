package org.idp.server.domain.model.user;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
