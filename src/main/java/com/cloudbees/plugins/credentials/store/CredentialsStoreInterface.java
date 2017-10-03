package com.cloudbees.plugins.credentials.store;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.model.*;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import java.util.List;
import java.util.Set;

/**
 * A store for {@link Credentials}.
 */
public interface CredentialsStoreInterface extends AccessControlled {
    /**
     * Returns the {@link CredentialsProvider} or dies trying.
     *
     * @return the {@link CredentialsProvider}
     * @since 2.0
     */
    @NonNull
    CredentialsProvider getProviderOrDie();

    /**
     * Returns the {@link CredentialsProvider}.
     *
     * @return the {@link CredentialsProvider} (may be {@code null} if the admin has removed the provider from
     * the {@link ExtensionList})
     * @since 2.0
     */
    @Nullable
    CredentialsProvider getProvider();

    /**
     * Returns the {@link CredentialsScope} instances that are applicable to this store.
     *
     * @return the {@link CredentialsScope} instances that are applicable to this store or {@code null} if the store
     * instance is no longer enabled.
     * @since 2.1.5
     */
    @Nullable
    Set<CredentialsScope> getScopes();

    /**
     * Returns the context within which this store operates. Credentials in this store will be available to
     * child contexts (unless {@link CredentialsScope#SYSTEM} is valid for the store) but will not be available to
     * parent contexts.
     *
     * @return the context within which this store operates.
     */
    @NonNull
    ModelObject getContext();

    /**
     * Checks if the given principle has the given permission.
     *
     * @param a          the principle.
     * @param permission the permission.
     * @return {@code false} if the user doesn't have the permission.
     */
    boolean hasPermission(@NonNull Authentication a, @NonNull Permission permission);

    /**
     * Returns all the {@link Domain}s that this credential provider has.
     * Most implementers of {@link BaseCredentialsStore} will probably want to override this method.
     *
     * @return the list of domains.
     */
    @NonNull
    List<Domain> getDomains();

    /**
     * Retrieves the domain with the matching name.
     *
     * @param name the name (or {@code null} to match {@link Domain#global()} as that is the domain with a null name)
     * @return the domain or {@code null} if there is no domain with the supplied name.
     * @since 2.1.1
     */
    @CheckForNull
    Domain getDomainByName(@CheckForNull String name);


    /**
     * Returns an unmodifiable list of credentials for the specified domain.
     *
     * @param domain the domain.
     * @return the possibly empty (e.g. for an unknown {@link Domain}) unmodifiable list of credentials for the
     * specified domain.
     */
    @NonNull
    abstract List<Credentials> getCredentials(@NonNull Domain domain);

    /**
     * Identifies whether this {@link BaseCredentialsStore} supports making changes to the credentials.
     * <p>
     * Note: in order for implementations to return {@code true} the class must implement ModifiableItemsCredentialsStore:
     * </p>
     *
     * @return {@code true} if class implements {@link ModifiableItemsCredentialsStore}
     */
    boolean isDomainsModifiable();

    /**
     * Identifies whether this {@link BaseCredentialsStore} supports making changes to the credentials.
     * <p>
     * Note: in order for implementations to return {@code true} the class must implement ModifiableItemsCredentialsStore:
     * </p>
     *
     * @return {@code true} if class implements {@link ModifiableItemsCredentialsStore}
     */
    boolean isCredentialsModifiable();

    /**
     * @param descriptor the {@link Descriptor} to check.
     * @return {@code true} if the supplied {@link Descriptor} is applicable in this {@link BaseCredentialsStore}
     * @since 2.0
     */
    boolean isApplicable(Descriptor<?> descriptor);

    /**
     * Returns the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link BaseCredentialsStore}.
     *
     * @return the list of {@link CredentialsDescriptor} instances that are applicable within this
     * {@link BaseCredentialsStore}.
     * @since 2.0
     */
    List<CredentialsDescriptor> getCredentialsDescriptors();

    /**
     * Computes the relative path from the current page to this store.
     *
     * @return the relative path from the current page or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public String getRelativeLinkToContext();

    /**
     * Computes the relative path from the current page to this store.
     *
     * @return the relative path from the current page or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public String getRelativeLinkToAction();

    /**
     * Computes the relative path from the current page to the specified domain.
     *
     * @param domain the domain
     * @return the relative path from the current page or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public String getRelativeLinkTo(Domain domain);

    /**
     * Returns the display name of the {@link #getContext()} of this {@link CredentialsStoreInterface}. The default
     * implementation can handle both {@link Item} and {@link ItemGroup} as long as these are accessible from
     * {@link Jenkins}, and {@link User}. If the {@link CredentialsStoreInterface} provides an alternative
     * {@link #getContext()} that is outside of the normal tree then that implementation is responsible for
     * overriding this method to produce the correct display name.
     *
     * @return the display name.
     * @since 2.0
     */
    String getContextDisplayName();

    /**
     * Return the {@link CredentialsStoreAction} for this store. The action will be displayed as a sub-item of the
     * {@link ViewCredentialsAction}. Return {@code null} if this store will take control of displaying its action
     * (which will be the case for legacy implementations)
     *
     * @return the {@link CredentialsStoreAction} for this store to be rendered in {@link ViewCredentialsAction} or
     * {@code null} for old implementations compiled against pre 2.0 versions of credentials plugin.
     * @since 2.0
     */
    @Nullable
    public CredentialsStoreAction getStoreAction();
}
