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
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.util.ListBoxModel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.springframework.security.core.Authentication;

/**
 * {@link ListBoxModel} with support for credentials.
 * <p>
 * This class is convenient for providing the {@code config.groovy} or {@code config.jelly} fragment for a collection
 * of objects of some {@link IdCredentials} subtype.
 * </p><p>
 * If you want to let the user configure a credentials object, do the following:
 * </p><p>
 * First, create a field that stores the credentials ID and defines a corresponding parameter in the constructor:
 * </p>
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
 * <p>
 * Your {@code config.groovy} should have the following entry to render a drop-down list box:
 * </p>
 * <pre>
 * f.entry(title:_("Credentials"), field:"credentialsId") {
 *     f.select()
 * }
 * </pre>
 * <p>
 * Finally, your {@link Descriptor} implementation should have the {@code doFillCredentialsIdItems} method, which
 * lists up the credentials available in this context:
 * </p>
 * <pre>
 * public ListBoxModel doFillCredentialsIdItems(&#64;QueryParam String value) {
 *     if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) { // or whatever permission is appropriate for
 *     this page
 *         // Important! Otherwise you expose credentials metadata to random web requests.
 *         return new StandardUsernameListBoxModel().includeCurrentValue(value);
 *     }
 *     return new StandardUsernameListBoxModel()
 *             .includeEmptySelection()
 *             .include(StandardUsernameCredentials.class,...))
 *             .includeCurrentValue(value);
 * }
 * </pre>
 * <p>
 * Exactly which overloaded version of the {@link #include(Item, Class)} depends on
 * the context in which your model operates. Here are a few common examples:
 * </p>
 * <dl>
 * <dt>System-level settings
 * <dd>
 * If your model is a singleton in the whole Jenkins instance, things that belong to the root {@link Jenkins}
 * (such as agents), or do not have any ancestors serving as the context, then use {@link Jenkins#get} as the
 * context.
 * <dt>Job-level settings
 * <dd>
 * If your model is a configuration fragment added to a {@link Item} (such as its major subtype {@link Job}),
 * then use that {@link Item} as the context.
 * For example:
 * <pre>
 * public ListBoxModel doFillCredentialsIdItems(&#64;AncestorInPath Item context, &#64;QueryParameter String source) {
 *     if (context == null || !context.hasPermission(Item.CONFIGURE)) {
 *         return new StandardUsernameListBoxModel().includeCurrentValue(value);
 *     }
 *     return new StandardUsernameListBoxModel()
 *             .includeEmptySelection()
 *             .includeAs(Tasks.getAuthenticationOf(context), context, StandardUsernameCredentials.class,
 *                 URIRequirementBuilder.fromUri(source).build())
 *             .includeCurrentValue(value);
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
     * @deprecated use {@link #includeEmptyValue()}
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withEmptySelection() {
        return includeEmptyValue();
    }

    /**
     * Adds an "empty" credential to signify selection of no credential.
     *
     * @return {@code this} for method chaining.
     * @since 2.1.0
     */
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> includeEmptyValue() {
        for (Option a : this) {
            if (StringUtils.equals("", a.value)) {
                return this;
            }
        }
        add(0, new Option(Messages.AbstractIdCredentialsListBoxModel_EmptySelection(), ""));
        return this;
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #include(Item, Class)} or {@link #includeAs(Authentication, Item, Class)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull C... credentials) {
        return withMatching(CredentialsMatchers.always(), Arrays.asList(credentials));
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #include(Item, Class)} or {@link #includeAs(Authentication, Item, Class)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterable<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials.iterator());
    }

    /**
     * Adds supplied credentials to the model.
     *
     * @param credentials the credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #include(Item, Class)} or {@link #includeAs(Authentication, Item, Class)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withAll(@NonNull Iterator<? extends C> credentials) {
        return withMatching(CredentialsMatchers.always(), credentials);
    }

    /**
     * Adds the matching subset of supplied credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #includeMatching(Item, Class, List, CredentialsMatcher)}
     * or {@link #includeMatchingAs(Authentication, Item, Class, List, CredentialsMatcher)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull C... credentials) {
        return withMatching(matcher, Arrays.asList(credentials));
    }

    /**
     * Adds the matching subset of supplied credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #includeMatching(Item, Class, List, CredentialsMatcher)}
     * or {@link #includeMatchingAs(Authentication, Item, Class, List, CredentialsMatcher)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
    @NonNull
    public AbstractIdCredentialsListBoxModel<T, C> withMatching(@NonNull CredentialsMatcher matcher,
                                                                @NonNull Iterable<? extends C> credentials) {
        return withMatching(matcher, credentials.iterator());
    }

    /**
     * Adds the matching subset of supplied credentials to the model.
     *
     * @param matcher     the matcher.
     * @param credentials the superset of credentials.
     * @return {@code this} for method chaining.
     * @deprecated prefer using the {@link #includeMatching(Item, Class, List, CredentialsMatcher)}
     * or {@link #includeMatchingAs(Authentication, Item, Class, List, CredentialsMatcher)}
     * methods to build the list box contents in order to allow credentials providers to not have to instantiate
     * a full credential instance where those credential providers store the secrets external from Jenkins.
     */
    @Deprecated
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

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication.
     *
     * @param context the context to add credentials from.
     * @param type    the base class of the credentials to add.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> include(@Nullable Item context, @NonNull Class<? extends C> type) {
        return include(context, type, Collections.emptyList());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication.
     *
     * @param context the context to add credentials from.
     * @param type    the base class of the credentials to add.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> include(@NonNull ItemGroup context,
                                                           @NonNull Class<? extends C> type) {
        return include(context, type, Collections.emptyList());
    }

    /**
     * @deprecated Use {@link #includeAs(Authentication, Item, Class)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull org.acegisecurity.Authentication authentication,
                                                             @Nullable Item context,
                                                             @NonNull Class<? extends C> type) {
        return includeAs(authentication, context, type, Collections.emptyList());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication.
     *
     * @param authentication the authentication to search with
     * @param context        the context to add credentials from.
     * @param type           the base class of the credentials to add.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull Authentication authentication,
                                                             @Nullable Item context,
                                                             @NonNull Class<? extends C> type) {
        return includeAs(authentication, context, type, Collections.emptyList());
    }

    /**
     * @deprecated Use {@link #includeAs(Authentication, ItemGroup, Class)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull org.acegisecurity.Authentication authentication,
                                                             @NonNull ItemGroup context,
                                                             @NonNull Class<? extends C> type) {
        return includeAs(authentication, context, type, Collections.emptyList());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication.
     *
     * @param authentication the authentication to search with
     * @param context        the context to add credentials from.
     * @param type           the base class of the credentials to add.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull Authentication authentication,
                                                             @NonNull ItemGroup context,
                                                             @NonNull Class<? extends C> type) {
        return includeAs(authentication, context, type, Collections.emptyList());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication with the specified domain requirements.
     *
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> include(@Nullable Item context, @NonNull Class<? extends C> type,
                                                           @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatching(context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication with the specified domain requirements.
     *
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> include(@NonNull ItemGroup context, @NonNull Class<? extends C> type,
                                                           @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatching(context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * @deprecated Use {@link #includeAs(Authentication, Item, Class, List)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull org.acegisecurity.Authentication authentication,
                                                             @Nullable Item context,
                                                             @NonNull Class<? extends C> type,
                                                             @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatchingAs(authentication, context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication with the specified domain requirements.
     *
     * @param authentication     the authentication to search with
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull Authentication authentication,
                                                             @Nullable Item context,
                                                             @NonNull Class<? extends C> type,
                                                             @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatchingAs(authentication, context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * @deprecated Use {@link #includeAs(Authentication, ItemGroup, Class, List)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull org.acegisecurity.Authentication authentication,
                                                             @NonNull ItemGroup context,
                                                             @NonNull Class<? extends C> type,
                                                             @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatchingAs(authentication.toSpring(), context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication with the specified domain requirements.
     *
     * @param authentication     the authentication to search with
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeAs(@NonNull Authentication authentication,
                                                             @NonNull ItemGroup context,
                                                             @NonNull Class<? extends C> type,
                                                             @NonNull List<DomainRequirement> domainRequirements) {
        return includeMatchingAs(authentication, context, type, domainRequirements, CredentialsMatchers.always());
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication with the specified domain requirements and match the specified filter.
     *
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @param matcher            the filter to apply to the credentials.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeMatching(@Nullable Item context,
                                                                   @NonNull Class<? extends C> type,
                                                                   @NonNull List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        return includeMatchingAs(Jenkins.getAuthentication2(), context, type, domainRequirements, matcher);
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the current
     * authentication with the specified domain requirements and match the specified filter.
     *
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @param matcher            the filter to apply to the credentials.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeMatching(@NonNull ItemGroup context,
                                                                   @NonNull Class<? extends C> type,
                                                                   @NonNull List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        return includeMatchingAs(Jenkins.getAuthentication2(), context, type, domainRequirements, matcher);
    }

    /**
     * @deprecated Use {@link #includeMatchingAs(Authentication, Item, Class, List, CredentialsMatcher)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeMatchingAs(@NonNull org.acegisecurity.Authentication authentication,
                                                                     @Nullable Item context,
                                                                     @NonNull Class<? extends C> type,
                                                                     @NonNull
                                                                             List<DomainRequirement> domainRequirements,
                                                                     @NonNull CredentialsMatcher matcher) {
        return includeMatchingAs(authentication.toSpring(), context, type, domainRequirements, matcher);
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication with the specified domain requirements and match the specified filter.
     *
     * @param authentication     the authentication to search with
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @param matcher            the filter to apply to the credentials.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItem(Class, Item, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeMatchingAs(@NonNull Authentication authentication,
                                                                     @Nullable Item context,
                                                                     @NonNull Class<? extends C> type,
                                                                     @NonNull
                                                                             List<DomainRequirement> domainRequirements,
                                                                     @NonNull CredentialsMatcher matcher) {
        addMissing(CredentialsProvider.listCredentialsInItem(type, context, authentication, domainRequirements, matcher));
        return this;
    }

    /**
     * @deprecated Use {@link #includeMatchingAs(Authentication, ItemGroup, Class, List, CredentialsMatcher)} instead.
     */
    @Deprecated
    public AbstractIdCredentialsListBoxModel<T, C> includeMatchingAs(@NonNull org.acegisecurity.Authentication authentication,
                                                                     @NonNull ItemGroup context,
                                                                     @NonNull Class<? extends C> type,
                                                                     @NonNull
                                                                             List<DomainRequirement> domainRequirements,
                                                                     @NonNull CredentialsMatcher matcher) {
        return includeMatchingAs(authentication.toSpring(), context, type, domainRequirements, matcher);
    }

    /**
     * Adds the ids of the specified credential type that are available to the specified context as the specified
     * authentication with the specified domain requirements and match the specified filter.
     *
     * @param authentication     the authentication to search with
     * @param context            the context to add credentials from.
     * @param type               the base class of the credentials to add.
     * @param domainRequirements the domain requirements.
     * @param matcher            the filter to apply to the credentials.
     * @return {@code this} for method chaining.
     * @see CredentialsProvider#listCredentialsInItemGroup(Class, ItemGroup, Authentication, List, CredentialsMatcher)
     * @since TODO
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeMatchingAs(@NonNull Authentication authentication,
                                                                     @NonNull ItemGroup context,
                                                                     @NonNull Class<? extends C> type,
                                                                     @NonNull
                                                                             List<DomainRequirement> domainRequirements,
                                                                     @NonNull CredentialsMatcher matcher) {
        addMissing(CredentialsProvider.listCredentialsInItemGroup(type, context, authentication, domainRequirements, matcher));
        return this;
    }

    /**
     * Ensures that the current value is present so that the form can be idempotently saved in those cases where the
     * user saving the form cannot view the current credential
     *
     * @param value the current value.
     * @return {@code this} for method chaining.
     * @since 2.1.0
     */
    public AbstractIdCredentialsListBoxModel<T, C> includeCurrentValue(@NonNull String value) {
        if (StringUtils.isEmpty(value)) {
            return includeEmptyValue();
        }
        for (Option a : this) {
            if (StringUtils.equals(value, a.value)) {
                return this;
            }
        }
        // the current should be the first (unless the first is the empty selection
        int index = isEmpty() ? 0 : "".equals(get(0).value) ? 1 : 0;
        add(index, new Option(Messages.AbstractIdCredentialsListBoxModel_CurrentSelection(), value));
        return this;
    }

    /**
     * Appends all of the missing elements from the specified collection to the end of
     * this list, in the order that they are returned by the
     * specified collection's Iterator.  The behavior of this operation is
     * undefined if the specified collection is modified while the operation
     * is in progress.  (This implies that the behavior of this call is
     * undefined if the specified collection is this list, and this
     * list is nonempty.)
     *
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @since 2.1.0
     */
    public boolean addMissing(@NonNull Collection<? extends Option> c) {
        Set<String> existing = new HashSet<>();
        for (Option o: this) {
            existing.add(o.value);
        }
        boolean changed = false;
        for (Option o : c) {
            if (!existing.contains(o.value)) {
                add(o);
                changed = existing.add(o.value) || changed;
            }
        }
        return changed;
    }
}
