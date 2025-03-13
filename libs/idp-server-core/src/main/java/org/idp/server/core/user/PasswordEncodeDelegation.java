package org.idp.server.core.user;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
