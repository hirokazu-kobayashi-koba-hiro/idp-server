package org.idp.server.core.oauth.grant;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class GrantedClaims implements Iterable<String> {

    Set<String> values;

    public GrantedClaims() {
        this.values = new HashSet<>();
    }

    public GrantedClaims(Set<String> values) {
        this.values = values;
    }

    public GrantedClaims merge(GrantedClaims other) {
        Set<String> newValues = new HashSet<>(this.values);
        newValues.addAll(other.values);
        return new GrantedClaims(newValues);
    }

    @Override
    public Iterator<String> iterator() {
        return values.iterator();
    }
}
