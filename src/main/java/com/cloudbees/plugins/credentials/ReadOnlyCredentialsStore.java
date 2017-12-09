/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * A read only store of {@link Credentials}. Each {@link ReadOnlyCredentialsStore} is associated with one and only one
 * {@link CredentialsProvider} though a {@link CredentialsProvider} may provide multiple {@link ReadOnlyCredentialsStore}s
 * (for example a folder scoped {@link CredentialsProvider} may provide a {@link ReadOnlyCredentialsStore} for each folder
 * or a user scoped {@link CredentialsProvider} may provide a {@link ReadOnlyCredentialsStore} for each user).
 *
 * @author Hosh Sadiq
 * @since TODO
 */
public abstract class ReadOnlyCredentialsStore extends CredentialsStore {
    public ReadOnlyCredentialsStore(Class<? extends CredentialsProvider> providerClass) {
        super(providerClass);
    }

    public ReadOnlyCredentialsStore() {
        super();
    }

    /**
     * If this method is overridden, ensure permissions {@link CredentialsProvider#CREATE}, {@link CredentialsProvider#UPDATE},
     * {@link CredentialsProvider#DELETE} will return false
     *
     * @param a          the principle.
     * @param permission the permission.
     * @return boolean
     */
    @Override
    public boolean hasPermission(@NonNull Authentication a, @NonNull Permission permission) {
        return getACL().hasPermission(a, permission);
    }

    @NonNull
    public ACL getACL() {
        return new ACL() {
            @Override
            public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                if (permission.equals(CredentialsProvider.CREATE) || permission.equals(CredentialsProvider.UPDATE) || permission.equals(CredentialsProvider.DELETE)) {
                    return false;
                }

                // todo this shouldn't be necessary, but findbugs is failing.
                Jenkins jenkins = Jenkins.getInstance();
                if(jenkins != null) {
                    return jenkins.getACL().hasPermission(a, permission);
                }

                return false;
            }
        };
    }

    @Override
    public final boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
        throw new UnsupportedOperationException("Cannot add items to read-only credentials store");
    }

    @Override
    public final boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials
    ) throws IOException {
        throw new UnsupportedOperationException("Cannot remove items to read-only credentials store");
    }

    @Override
    public final boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                           @NonNull Credentials replacement
    ) throws IOException {
        throw new UnsupportedOperationException("Cannot update items to read-only credentials store");
    }
}
