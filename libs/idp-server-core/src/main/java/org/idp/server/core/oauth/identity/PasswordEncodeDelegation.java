package org.idp.server.core.oauth.identity;

public interface PasswordEncodeDelegation {

  String encode(String rawPassword);
}
