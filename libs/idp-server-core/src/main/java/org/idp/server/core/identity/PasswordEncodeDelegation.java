package org.idp.server.core.identity;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
