package org.idp.server.core.oauth.interaction;

import java.util.Map;
import java.util.Objects;

public class OAuthUserInteractors {

    Map<OAuthUserInteractionType, OAuthUserInteractor> values;

    public OAuthUserInteractors(Map<OAuthUserInteractionType, OAuthUserInteractor> values) {
        this.values = values;
    }

    public OAuthUserInteractor get(OAuthUserInteractionType type) {
        OAuthUserInteractor interactor = values.get(type);

        if (Objects.isNull(interactor)) {
            throw new OAuthInteractorUnSupportedException("No OAuthInteractor found for type " + type);
        }

        return interactor;
    }
}
