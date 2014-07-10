/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
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

import hudson.model.Descriptor;
import hudson.model.ModelObject;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;

import java.util.Set;

/**
 * Descriptor for credentials.
 */
public abstract class CredentialsDescriptor extends Descriptor<Credentials> {

    /**
     * Constructor.
     *
     * @param clazz The concrete credentials class.
     * @since 1.2
     */
    protected CredentialsDescriptor(Class<? extends Credentials> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link Credentials} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.3
     */
    protected CredentialsDescriptor() {
    }

    /**
     * Fills in the scopes for a scope list-box.
     *
     * @return the scopes for the nearest request object that acts as a container for credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    public ListBoxModel doFillScopeItems() {
        ListBoxModel m = new ListBoxModel();
        Ancestor ancestor = Stapler.getCurrentRequest().findAncestor(Object.class);
        while (ancestor != null) {
            if (ancestor.getObject() instanceof ModelObject) {
                Set<CredentialsScope> scopes =
                        CredentialsProvider.lookupScopes((ModelObject) ancestor.getObject());
                if (scopes != null) {
                    for (CredentialsScope scope : scopes) {
                        m.add(scope.getDisplayName(), scope.toString());
                    }
                    break;
                }
            }
            ancestor = ancestor.getPrev();
        }
        return m;
    }

    /**
     * Checks if asking for a credentials scope is relevant. For example, when a scope will be stored in
     * {@link UserCredentialsProvider}, there is no need to specify the scope,
     * as it can only be {@link CredentialsScope#USER}, but where the credential will be stored in
     * {@link SystemCredentialsProvider}, there are multiple scopes relevant for that container, so the scope
     * field is relevant.
     *
     * @return {@code true} if the nearest request object that acts as a container for credentials needs a scope
     *         to be specified.
     */
    @SuppressWarnings("unused") // used by stapler
    public boolean isScopeRelevant() {
        Ancestor ancestor = Stapler.getCurrentRequest().findAncestor(Object.class);
        while (ancestor != null) {
            if (ancestor.getObject() instanceof ModelObject) {
                Set<CredentialsScope> scopes =
                        CredentialsProvider.lookupScopes((ModelObject) ancestor.getObject());
                if (scopes != null) {
                    return scopes.size() > 1;
                }
            }
            ancestor = ancestor.getPrev();
        }
        return false;
    }

    /**
     * Similar to {@link #isScopeRelevant()} but operating on a specific {@link ModelObject} rather than trying to
     * infer from the stapler request.
     *
     * @param object the object that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     *         object.
     */
    @SuppressWarnings("unused") // used by stapler
    public boolean isScopeRelevant(ModelObject object) {
        Set<CredentialsScope> scopes = CredentialsProvider.lookupScopes(object);
        return scopes != null && scopes.size() > 1;
    }

    /**
     * Similar to {@link #isScopeRelevant()} but used by {@link CredentialsStoreAction}.
     *
     * @param wrapper the wrapper for the domain that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     *         object.
     */
    @SuppressWarnings("unused") // used by stapler
    public boolean isScopeRelevant(CredentialsStoreAction.DomainWrapper wrapper) {
        if (wrapper != null) {
            return isScopeRelevant(wrapper.getStore().getContext());
        }
        CredentialsStoreAction action =
                Stapler.getCurrentRequest().findAncestorObject(CredentialsStoreAction.class);
        if (action != null) {
            return isScopeRelevant(action.getStore().getContext());
        }
        return isScopeRelevant();
    }

    /**
     * Similar to {@link #isScopeRelevant()} but used by {@link CredentialsStoreAction}.
     *
     * @param wrapper the wrapper for the domain that is going to contain the credential.
     * @return {@code true} if there is more than one {@link CredentialsScope} that can be used for the specified
     *         object.
     */
    @SuppressWarnings("unused") // used by stapler
    public boolean isScopeRelevant(CredentialsStoreAction.CredentialsWrapper wrapper) {
        if (wrapper != null) {
            return isScopeRelevant(wrapper.getStore().getContext());
        }
        CredentialsStoreAction action =
                Stapler.getCurrentRequest().findAncestorObject(CredentialsStoreAction.class);
        if (action != null) {
            return isScopeRelevant(action.getStore().getContext());
        }
        return isScopeRelevant();
    }

    /**
     * Returns the config page for the credentials.
     *
     * @return the config page for the credentials.
     */
    @SuppressWarnings("unused") // used by stapler
    public String getCredentialsPage() {
        return getViewPage(clazz, "credentials.jelly");
    }
}
