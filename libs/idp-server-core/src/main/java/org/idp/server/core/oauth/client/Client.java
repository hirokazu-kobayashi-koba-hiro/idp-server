package org.idp.server.core.oauth.client;

public class Client {
    ClientIdentifier identifier;
    ClientName name;

    public Client() {}

    public Client(ClientIdentifier identifier, ClientName name) {
        this.identifier = identifier;
        this.name = name;
    }

    public ClientIdentifier identifier() {
        return identifier;
    }

    public ClientName name() {
        return name;
    }

    public boolean exists() {
        return identifier != null && !identifier.exists();
    }
}
