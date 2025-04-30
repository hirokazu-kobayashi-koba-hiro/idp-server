package org.idp.server.core.identity.authentication;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
