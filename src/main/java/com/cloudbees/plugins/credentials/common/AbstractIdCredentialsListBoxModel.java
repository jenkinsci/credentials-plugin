/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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
package com.cloudbees.plugins.credentials.common;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.util.ListBoxModel;

import java.util.Arrays;
import java.util.Iterator;
import jenkins.model.Jenkins;

/**
 * {@link ListBoxModel} with support for credentials.
 * <p/>
 * This class is convenient for providing the {@code config.groovy} or {@code config.jelly} fragment for a collection of objects of some {@link IdCredentials} subtype.
 * <p/>
 * If you want to let the user configure a credentials object, do the following:
 * <p/>
 * First, create a field that stores the credentials ID and defines a corresponding parameter in the constructor:
 * <p/>
 * <pre>
 * private String credentialsId;
 *
 * &#64;DataBoundConstructor
 * public MyModel( .... , String credentialsId) {
 *     this.credentialsId = credentialsId;
 *     ...
 * }
 * public String getCredentialsId() {return credentialsId;}
 * </pre>
 * <p/>
 * Your <tt>config.groovy</tt> should have the following entry to render a drop-down list box:
 * <p/>
 * <pre>
 * f.entry(title:_("Credentials"), field:"credentialsId") {
 *     f.select()
 * }
 * </pre>
 * <p/>
 * Finally, your {@link Descriptor} implementation should have the <tt>doFillCredentialsIdItems</tt> method, which
 * lists up the credentials available in this context:
 * <p/>
 * <pre>
 * public ListBoxModel doFillCredentialsIdItems() {
 *     if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) { // or whatever permission is appropriate for this page
 *         // Important! Otherwise you expose credentials metadata to random web requests.
 *         return new ListBoxModel();
 *     }
 *     return new StandardUsernameListBoxModel().withEmptySelection().withAll(
 *         CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class,...));
 * }
 * </pre>
 * <p/>
 * <p/>
 * Exactly which overloaded version of the {@link CredentialsProvider#lookupCredentials(Class)} depends on
 * the context in which your model operates. Here are a few common examples:
 * <p/>
 * <dl>
 * <dt>System-level settings
 * <dd>
 * If your model is a singleton in the whole Jenkins instance, things that belong to the root {@link Jenkins}
 * (such as slaves), or do not have any ancestors serving as the context, then use {@link Jenkins#getInstance} as the context.
 * <p/>
 * <dt>Job-level settings
 * <dd>
 * If your model is a configuration fragment added to a {@link Item} (such as its major subtype {@link Job}),
 * then use that {@link Item} as the context.
   For example:
 * <p/>
 * <pre>
 * public ListBoxModel doFillCredentialsIdItems(&#64;AncestorInPath Item context, &#64;QueryParameter String source) {
 *     if (context == null || !context.hasPermission(Item.CONFIGURE)) {
 *         return new ListBoxModel();
 *     }
 *     return new StandardUsernameListBoxModel().withEmptySelection().withAll(
 *         CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM, URIRequirementBuilder.fromUri(source).build()));
 * }
 * </pre>
 * </dl>
 *
 * @since 1.6
 */
public abstract class AbstractIdCredentialsListBoxModel<T extends AbstractIdCredentialsListBoxModel<T, C>,
        C extends IdCredentials>
        extends ListBoxModel {

    /**
     * Generate a description of the supplied credential.
     *
     * @param c the credential.
     * @return the description.
     */
    @NonNull
    protected abstract String describe(@NonNull C c);

    /**
     * Adds a single credential.
     *
     * @param u the credential to add.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> with(@CheckForNull C u) {
        if (u != null) {
            add(describe(u), u.getId());
        }
        return this;
    }

    /**
     * Adds an "empty" credential to signify selection of no credential.
     *
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withEmptySelection() {
        add(Messages.AbstractIdCredentialsListBoxModel_EmptySelection(), "");
        return this;
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull C... credentials) {
        return withMatching(CredentialsMatchers.always(), Arrays.asList(credentials));
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterable<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials.iterator());
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterator<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials);
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull C... credentials) {
        return withMatching(matcher, Arrays.asList(credentials));
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull Iterable<? extends C> credentials) {
        return withMatching(matcher, credentials.iterator());
    }

    /**
     * Adds the matching subset of suppled credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull Iterator<? extends C> credentials) {
        while (credentials.hasNext()) {
            C c = credentials.next();
            if (matcher.matches(c)) {
                with(c);
            }
        }
        return this;
    }

}
