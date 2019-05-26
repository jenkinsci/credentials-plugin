package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.CredentialsResolver;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.ResolveWith;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author stephenc
 * @since 02/08/2013 12:54
 */
@ResolveWith(DummyLegacyCredentials.ResolverImpl.class)
public class DummyLegacyCredentials extends BaseCredentials implements UsernamePasswordCredentials {

    private final String username;

    private final Secret password;

    @DataBoundConstructor
    public DummyLegacyCredentials(CredentialsScope scope, String username, String password) {
        super(scope);
        this.username = username;
        this.password = Secret.fromString(password);
    }

    public String getUsername() {
        return username;
    }

    @NonNull
    public Secret getPassword() {
        return password;
    }

    private Object readResolve() {
        return new DummyCredentials(getScope(), username, password.getEncryptedValue());
    }

    public static class ResolverImpl extends CredentialsResolver<UsernamePasswordCredentials, DummyLegacyCredentials> {

        public ResolverImpl() {
            super(UsernamePasswordCredentials.class);
        }

        @NonNull
        @Override
        protected DummyLegacyCredentials doResolve(@NonNull UsernamePasswordCredentials original) {
            return new DummyLegacyCredentials(original.getScope(), original.getUsername(),
                    original.getPassword().getEncryptedValue());
        }
    }
}
