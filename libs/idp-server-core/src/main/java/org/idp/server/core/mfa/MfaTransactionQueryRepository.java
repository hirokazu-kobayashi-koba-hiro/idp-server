package org.idp.server.core.mfa;

public interface MfaTransactionQueryRepository {

  <T> T get(MfaTransactionIdentifier identifier, String key, Class<T> clazz);
}
