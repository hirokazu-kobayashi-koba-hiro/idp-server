package org.idp.server.basic.jose;

/** JoseContextCreator */
public interface JoseContextCreator {
  JoseContext create(String jose, String publicJwks, String privateJwks, String secret) throws JoseInvalidException;
}
