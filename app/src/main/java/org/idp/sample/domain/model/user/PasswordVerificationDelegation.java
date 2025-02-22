package org.idp.sample.domain.model.user;

public interface PasswordVerificationDelegation {

  boolean verify(String rawPassword, String encodedPassword);
}
