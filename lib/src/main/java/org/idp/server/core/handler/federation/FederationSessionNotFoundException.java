package org.idp.server.core.handler.federation;

public class FederationSessionNotFoundException extends RuntimeException {
    public FederationSessionNotFoundException(String message) {
        super(message);
    }
}
