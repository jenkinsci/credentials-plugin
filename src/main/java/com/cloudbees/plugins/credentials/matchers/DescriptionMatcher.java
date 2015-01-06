package com.cloudbees.plugins.credentials.matchers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Matches on a credentials description (case insensitive)
 */
public class DescriptionMatcher implements CredentialsMatcher {

    private final String description;

    public DescriptionMatcher(@NonNull String description) {
        this.description = description;
    }

    @Override
    public boolean matches(@NonNull Credentials item) {
        String itemDescription = null;

        if(item instanceof StandardCredentials) {
            itemDescription = ((StandardCredentials) item).getDescription();
        }
        return description.equalsIgnoreCase(itemDescription);
    }

    @Override
    public String toString() {
        return "DescriptionMatcher{description=" + description + "}";
    }
}
