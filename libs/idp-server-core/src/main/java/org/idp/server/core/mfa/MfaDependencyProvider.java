package org.idp.server.core.mfa;

public interface MfaDependencyProvider<T> {
    Class<T> type();
    T provide();
}
