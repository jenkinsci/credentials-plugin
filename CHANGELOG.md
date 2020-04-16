# Version History

### Version 2.3.7 (April 16th, 2019)
- JCasC support for `GlobalCredentialsConfiguration` ([JENKINS-61880](https://issues.jenkins-ci.org/browse/JENKINS-61880))

### Version 2.3.6 (April 15th, 2020)
- JCasC support for `CredentialsProvider` extensions ([JENKINS-61900](https://issues.jenkins-ci.org/browse/JENKINS-61900)).

### Version 2.3.5 (March 23rd, 2020)
- Add system property `com.cloudbees.plugins.credentials.CredentialsProvider.fingerprintEnabled` which can be set to `false` to disable credentials tracking using fingerprints.

### Version 2.3.4 (March 18th, 2020)
- Add category to system settings for modern Jenkins releases.

### Version 2.3.3 (February 27th, 2020)
- Use pass-through conversion for `SecretBytes` to avoid JCasC errors ([PR-135](https://github.com/jenkinsci/credentials-plugin/pull/135)).

### Version 2.3.2 (February 27th, 2020)
- Show credentials id in DomainWrapper view ([PR-120](https://github.com/jenkinsci/credentials-plugin/pull/120)).
- Migrate changelog to repository ([PR-134](https://github.com/jenkinsci/credentials-plugin/pull/134)).

### Version 2.3.1 (August 26th, 2019)

-   Use GitHub for documentation root instead of wiki (
     [PR-128](https://github.com/jenkinsci/credentials-plugin/pull/128)
    ).
-   Various code cleanups (
     [PR-133](https://github.com/jenkinsci/credentials-plugin/pull/133) - JCasC test harness,
     [PR-132](https://github.com/jenkinsci/credentials-plugin/pull/132) - Use latest parent pom,
     [PR-131](https://github.com/jenkinsci/credentials-plugin/pull/131) - Minor documentation grammar fix,
     [PR-130](https://github.com/jenkinsci/credentials-plugin/pull/132) - Test with configuration as code plugin 1.34,
     [PR-127](https://github.com/jenkinsci/credentials-plugin/pull/127) - Use try with resources and ACL.as, other cleanups
    ).

### Version 2.3.0 (August 26th, 2019)

-   Allow credentials parameters to shadow credentials with the same id in credentials lookup
    ([JENKINS-58170](https://issues.jenkins-ci.org/browse/JENKINS-58170)).
-   Various code cleanups (
     [PR-125](https://github.com/jenkinsci/credentials-plugin/pull/125) - Use Java 8 syntax more widely, other cleanup,
     [PR-124](https://github.com/jenkinsci/credentials-plugin/pull/124) - Documentation updates
    ).

### Version 2.2.1 (August 1st, 2019)

-   Fix incorrect permission check for MANAGE\_DOMAINS
    ([JENKINS-56607](https://issues.jenkins-ci.org/browse/JENKINS-56607)).
-   Fix memory leak in credentials fingerprint tracking
    ([JENKINS-49235](https://issues.jenkins-ci.org/browse/JENKINS-49235)).
-   Clean up various typos.
-   Add [incrementals](https://github.com/jenkinsci/incrementals-tools) support.

### Version 2.2.0 (May 31, 2019)

-   Jenkins LTS 2.138.4  is now the minimal requirement
-   Support of Jenkins [Configuration-as-Code plugin](https://plugins.jenkins.io/configuration-as-code-support)
    was moved to the plugin from [Configuration-as-Code: Support plugin](https://plugins.jenkins.io/configuration-as-code-support)
    ([JENKINS-57559](https://issues.jenkins-ci.org/browse/JENKINS-57559))
-   Add button was overlapping with down arrow in some conditions ([JENKINS-52936](https://issues.jenkins-ci.org/browse/JENKINS-52936))
-   Chinese localization was moved to the [Chinese localization plugin](https://github.com/jenkinsci/localization-zh-cn-plugin)

### Version 2.1.19 (May 21st, 2019)

-   [Fix security issue SECURITY-1322](https://jenkins.io/security/advisory/2019-05-21/#SECURITY-1322)

### Version 2.1.18 (July 20th, 2018)

-   Add a CLI command named `list-credentials-as-xml` to list all credentials in a store in XML format ([JENKINS-52175](https://issues.jenkins-ci.org/browse/JENKINS-52175))

### Version 2.1.17 (June 25th, 2018)

-   Ensure credentials are loaded as system ([prerequisite for SSH Credentials security fix](https://jenkins.io/security/advisory/2018-06-25/#SECURITY-440))

### Version 2.1.16 (September 14th, 2017)

-   All
    -   Minor code change to credentials action in order to aid comprehension by anyone reading the code

### Version 2.1.15 (September 6th, 2017)

-   All
    -   Canonical [reference documentation](https://github.com/jenkinsci/credentials-plugin/tree/master/docs) for plugin released.
	This documentation should be taken on a canonical basis, in other words, where behaviour deviates from the canonical documentation there is a bug.
	Sources such as <https://jenkins.io/doc/> and <https://jenkins.io/doc/developer/> are
        expected to use the canonical documentation as a basis for
        authoring original content that describes in a cohesive narative
        how to use the credentials plugin.
-   User
    -   Mix a hash of the secret value into the fingerprints to remove false
        duplicate tracking ([JENKINS-43263](https://issues.jenkins-ci.org/browse/JENKINS-43263))
        **NOTE: all existing credentials fingerprint tracking history
        will be lost**.
    -   Editing the description field of a credential will no longer change its
        fingerpint ([JENKINS-44171](https://issues.jenkins-ci.org/browse/JENKINS-44171)) **NOTE: all
        existing credentials fingerprint tracking history will be
        lost**.
    -   All BASE-64 handling has been standardized so that chunkend and
        url-safe variants are handled consistently ([JENKINS-45185](https://issues.jenkins-ci.org/browse/JENKINS-45185))
-   Consumer
    -   Allow \<c:select\> tags to specify the checkMethod
-   Provider
    -   No changes affecting plugins implementing credentials providers

### Version 2.1.14 (June 12th, 2017)

-   Remove references to Trilead classes ([JENKINS-43610](https://issues.jenkins-ci.org/browse/JENKINS-43610))
-   Add credentials symbol to parameter ([JENKINS-44588](https://issues.jenkins-ci.org/browse/JENKINS-44588))

### Version 2.1.13 (March 2nd, 2017)

-   CSS width problems with credentials select drop-down ([JENKINS-41512](https://issues.jenkins-ci.org/browse/JENKINS-41512))

### Version 2.1.12 (February 22nd, 2017)

-   IllegalArgumentException: unable to convert to
    class `com.cloudbees.plugins.credentials.SecretBytes` ([JENKINS-41946](https://issues.jenkins-ci.org/browse/JENKINS-41946))
-   Inconsistency in encoding of keystores ([JENKINS-41952](https://issues.jenkins-ci.org/browse/JENKINS-41952))

### Version 2.1.11 (January 26th, 2017)

-   When duplicate credentials have the same ID, the first one should
    win ([JENKINS-41004](https://issues.jenkins-ci.org/browse/JENKINS-41004))
-   The credentials usage tracking should warn that it may give false
    negatives ([JENKINS-40701](https://issues.jenkins-ci.org/browse/JENKINS-40701))
-   The Add button in a credentials select control should be enabled if
    the user has create permission in any stores in scope, not just the
    root store ([JENKINS-41478](https://issues.jenkins-ci.org/browse/JENKINS-41478))
-   Use the Jenkins.XSTREAM2 instance so that plugins can use alias to
    assist migration of credentials ([JENKINS-40914](https://issues.jenkins-ci.org/browse/JENKINS-40914))

### Version 2.1.10 (November 23, 2016)

-   Modified API method name introduced in 2.1.9

### Version 2.1.9 (November 17, 2016)

-   Add API method which allows to check if a given String is of type
    SecretBytes ([JENKINS-39381](https://issues.jenkins-ci.org/browse/JENKINS-39381))
-   Provide a mechanism for forcing a save of all credential store which
    will only be available via groovy scripting ([JENKINS-39317](https://issues.jenkins-ci.org/browse/JENKINS-39317))

### Version 2.1.8 (October 25, 2016)

-   Add additional diagnostic logging to certificate credentials to help
    local malformed credentials
-   Add additional exception safety to name inference of credentials

### Version 2.1.7 (October 18, 2016)

-   Add support for ESC closing the add credentials dialog ([JENKINS-38961](https://issues.jenkins-ci.org/browse/JENKINS-38961))

### Version 2.1.6 (October 10, 2016)

-   Suppress incorrect duplicate ID warning when updating credentials
    ([JENKINS-38861](https://issues.jenkins-ci.org/browse/JENKINS-38861))

### Version 2.1.5 (September 20, 2016)

-   Resolve confusion for plugin authors on how to get form validation
    URLs in config.jelly ([JENKINS-36315](https://issues.jenkins-ci.org/browse/JENKINS-36315))
-   Provide a mechanism to report that a credential's secrets are
    unavailable ([JENKINS-36431](https://issues.jenkins-ci.org/browse/JENKINS-36431))
-   Provide a SecretBytes type for space efficient local storage of an
    encrypted byte\[\] ([JENKINS-36432](https://issues.jenkins-ci.org/browse/JENKINS-36432))
-   Fix some failing test cases when using the PCT ([JENKINS-37801](https://issues.jenkins-ci.org/browse/JENKINS-37801))
-   Saving SecretBuildWrapper for the first time fails due to duplicated
    credentialsId field unless git also installed ([JENKINS-37707](https://issues.jenkins-ci.org/browse/JENKINS-37707))

### Version 2.1.4 (June 23, 2016)

-   Make it easier for CredentialProvider implementers to handle context
    objects that are both an Item and an ItemGroup ([JENKINS-36161](https://issues.jenkins-ci.org/browse/JENKINS-36161))

### Version 2.1.3 (June 20, 2016)

-   Context menu icon URLs were incorrect when using a context path of /
    so the icons would not display on the main Credentials view pages

### Version 2.1.2 (June 20, 2016)

-   If you added type restrictions you could not completely remove them
    ([JENKINS-36090](https://issues.jenkins-ci.org/browse/JENKINS-36090))
-   The workaround for [JENKINS-26578](https://issues.jenkins-ci.org/browse/JENKINS-26578) was
    breaking the unit tests for ssh-credentials (bug in htmlunit) so
    delay the "workaround" by 1ms so that htmlunit does not bomb out
    ([JENKINS-36034](https://issues.jenkins-ci.org/browse/JENKINS-36034))

### Version 2.1.1 (June 15, 2016)

-   Add support to track where a credential is used ([JENKINS-20139](https://issues.jenkins-ci.org/browse/JENKINS-20139)) - Note
    that tracking relies on credentials consumers recording the usage,
    so if there are issues with this please file issues **against the
    credential consuming plugin** as it is not a problem with the
    credentials API.
-   Create credentials through CLI ([JENKINS-28407](https://issues.jenkins-ci.org/browse/JENKINS-28407))

### Version 2.1.0 (June 9, 2016)

-   Credentials store XML/JSON REST API cannot browse into domains
    ([JENKINS-24631](https://issues.jenkins-ci.org/browse/JENKINS-24631))
-   Added some extra NPE safety to try and prevent a NPE in plugins that
    do not use the API correctly ([JENKINS-35317](https://issues.jenkins-ci.org/browse/JENKINS-35317))
-   System credentials store showing twice for credentials parameter Add
    button drop down when logged in as a user ([JENKINS-35488](https://issues.jenkins-ci.org/browse/JENKINS-35488))
-   Credentials providers need to be able to list credentials without
    retrieving the backing secret ([JENKINS-35306](https://issues.jenkins-ci.org/browse/JENKINS-35306)) -
    *this change changes the recommended way to populate drop down
    select boxes for plugin authors.* *The old way still works but is no
    longer recommended, hence the minor version bump.* An example of a
    new style implementation is as follows:

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job context, @QueryParameter String source, @QueryParameter String value) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                // previously it was recommended to just return an empty ListBoxModel
                // now recommended to return a model with just the current value
                return new StandardUsernameListBoxModel().includeCurrentValue(value);
            }
            // previously it was recommended to use the withXXX methods providing the credentials instances directly
            // now recommended to populate the model using the includeXXX methods which call through to
            // CredentialsProvider.listCredentials and to ensure that the current value is always present using
            // includeCurrentValue
            return new StandardUsernameListBoxModel()
                    .includeEmptySelection()
                    .includeAs(Tasks.getAuthenticationOf(context), context, StandardUsernameCredentials.class,
                        URIRequirementBuilder.fromUri(source).build())
                    .includeCurrentValue(value);
        }

    The main changes in this sample are:

1.  \#\* Adding a QueryParameter for the current value. We can then
    ensure that the current value is always available by using the
    includeCurrentValue helper method
    -   Using includeAs to add the credentials using
        CredentialsProvider.listCredentials rather than the old pattern
        whereby the credentials would be retrieved directly and then
        converted into display name & id
    -   Now recommended to use Tasks.getAuthenticationOf(job) to get the
        authentication that the job will run as. When [JENKINS-35081](https://issues.jenkins-ci.org/browse/JENKINS-35081) is
        resolved this will become more important

### Version 2.0.7 (May 27, 2016)

-   Delete and Move credentials does not work with providers that do not
    have modifiable domains ([JENKINS-35130](https://issues.jenkins-ci.org/browse/JENKINS-35130))
-   Add credentials button broken on Build with Parameters screen
    ([JENKINS-35168](https://issues.jenkins-ci.org/browse/JENKINS-35168))
-   Be more forgiving of users who have not configured their HTTPS
    front-end to forward the protocol details correctly ([JENKINS-35150](https://issues.jenkins-ci.org/browse/JENKINS-35150))

### Version 2.0.6 (May 25, 2016)

-   The fix in 2.0.5 introduced a javascript error for configuring
    existing jobs that have already got the Add button. This has been
    fixed.

### Version 2.0.5 (May 24, 2016)

-   Improve the JENKINS-26578 workaround so that the in-place Add button
    works in newly created hetero lists again (was broken since 2.0)

### Version 2.0.4 (May 24, 2016)

-   Add a workaround for the Jenkins core bug with bottom sticker bars
    ([issue
    \#24662](https://issues.jenkins-ci.org/browse/JENKINS-24662))
-   Fix minor bug in credential descriptor visibility filtering

### Version 2.0.3 (May 24, 2016)

-   Infinite loop in traversing the list of available credential stores
    for ComputerSet, Node, Computer context objects ([JENKINS-35075](https://issues.jenkins-ci.org/browse/JENKINS-35075))

### Version 2.0.2 (May 24, 2016)

-   Follow-up for one remaining incorrect icon sizing when using a
    custom theme ([JENKINS-33191](https://issues.jenkins-ci.org/browse/JENKINS-33191))
-   The help text for the credentials providers was reporting the
    Credentials/UseItem permission in cases where that permission was
    disabled and the Item/Configure permission should have been reported

### Version 2.0.1 (May 23, 2016)

-   Moved the Credential ID out of the advanced box
-   Where possible, the parameters view page of a build will present the
    credential parameter as a link to the credential to assist in
    disambiguation ([JENKINS-31991](https://issues.jenkins-ci.org/browse/JENKINS-31991))
-   Impossible to scroll down the Add Credentials window content ([JENKINS-28864](https://issues.jenkins-ci.org/browse/JENKINS-28864))
-   Users should be able to view their own credentials ([JENKINS-33872](https://issues.jenkins-ci.org/browse/JENKINS-33872))
-   Incorrect icon sizing when using a custom theme ([JENKINS-33191](https://issues.jenkins-ci.org/browse/JENKINS-33191))
-   Notify SaveableListener for global credentials updates ([JENKINS-33111](https://issues.jenkins-ci.org/browse/JENKINS-33111))

### Version 2.0 (May 20, 2016)

-   The Add button now features a drop-down menu to allow selecting the
    destination store
-   The Add modal dialog now supports selecting the credential domain to
    add into
-   The credentials management has been moved fully into the Credentials
    action links
-   The main page for the Credentials action has been reworked to show
    the effective credentials available within the current scope (as
    well as any masked credentials from parent scopes) as well as all
    the credentials stores contributing to the current scope. All the
    links are now context menu links.
-   The Manage Jenkins » Configure Credentials screen has been reworked
    to actually allow for managing the credentials providers and types.
    It is now possible to restrict the credential types available per
    credential store as well as globally disable individual credential
    stores.

### Version 1.28 (Apr 30, 2016)

-   Stop allowing to update domain with blank names ([JENKINS-34329](https://issues.jenkins-ci.org/browse/JENKINS-34329))
-   Add french translation
-   Sort credentials by credential name in select lists
-   Upgrade to new parent pom

### Version 1.27 (Apr 4, 2016)

-   After looking up user-scoped credentials, the SecurityContext is set
    to null causing user-scoped credentials to not be retrieved properly
    ([JENKINS-33944](https://issues.jenkins-ci.org/browse/JENKINS-33944))

### Version 1.26 (Mar 23, 2016)

-   User may view some information in credential-store of other users
    ([JENKINS-31610](https://issues.jenkins-ci.org/browse/JENKINS-31610))

### Version 1.25 (Feb 19, 2016)

-   Consider default value to be the default ([JENKINS-32642](https://issues.jenkins-ci.org/browse/JENKINS-32642))
-   Fix incorrect parameter order that breaks Rebuild plugin with
    credentials parameters ([JENKINS-29646](https://issues.jenkins-ci.org/browse/JENKINS-29646))

### Version 1.24 (Oct 12, 2015)

-   Fix NPE when taking a snapshot of certificate credentials.

### Version 1.23 (Sep 7, 2015)

-   Fixed interaction with [credentials binding plugin](https://plugins.jenkins.io/credentials-binding)
    and the [authorize project plugin](https://plugins.jenkins.io/authorize-project)
    ([JENKINS-30326](https://issues.jenkins-ci.org/browse/JENKINS-30326))
-   Baseline version of Jenkins bumped to 1.565
-   Fixed a bug where when a path was used in the URIRequirementBuilder,
    it cleared any SchemeRequirement already set.

### Version 1.22 (Jan 25, 2015)

-   Added a work-around
    for [JENKINS-26578](https://issues.jenkins-ci.org/browse/JENKINS-26578) until
    the baseline version of Jenkins has fixed that issue

### Version 1.21 (Jan 15, 2015)

-   [JENKINS-26099](https://issues.jenkins-ci.org/browse/JENKINS-26099)
    Allow the user to specify the ID of newly created credentials. (For
    username/password and certificate credentials. Credentials defined
    in other plugins need to use `BaseStandardCredentialsDescriptor` to
    pick up this feature.)
-   Suppressing a stack trace in case of a failure to unlock certificate
    credentials due to an empty password.

### Version 1.20 (Dec 19, 2014)

-   [JENKINS-25682](https://issues.jenkins-ci.org/browse/JENKINS-25682)
    amendment.

### Version 1.19 (Dec 18, 2014)

-   [JENKINS-25682](https://issues.jenkins-ci.org/browse/JENKINS-25682)
-   [JENKINS-23131](https://issues.jenkins-ci.org/browse/JENKINS-23131)
-   [JENKINS-22097](https://issues.jenkins-ci.org/browse/JENKINS-22097)
-   [JENKINS-21634](https://issues.jenkins-ci.org/browse/JENKINS-21634)

### Version 1.18 (Oct 19, 2014)

-   UI glitch with icon tags

### Version 1.17 (Oct 17, 2014)

-   Simplified handling of uploaded-file certificates on slaves.
-   Allowing parameter values to be used from workflow projects.
-   Improved Javadoc for list box models.
-   [JENKINS-21051](https://issues.jenkins-ci.org/browse/JENKINS-21051)
    Japanese translation fixes.
-   Exported description and displayName for use by REST API.

### Version 1.16.1 (Aug 11, 2014)

-   Fix NPE in new parameter resolving helper method

### Version 1.16 (Aug 11, 2014)

-   Add support for credentials parameters (note these are not exposed
    as environment variables, rather the IDs are exposed and plugin
    support is required to retrieve the credentials from the respective
    credential stores and act on those credentials as necessary)

### Version 1.15 (Jul 10, 2014)

-   Fix the check for \`isScopeRelevant(x) so that updating credentials
    within a credentials domain does not reset the scope to 'Global'
    ([SECURITY-137](https://issues.jenkins-ci.org/browse/SECURITY-137))

### Version 1.14 (Jun 16, 2014)

-   Added support for snapshotting credentials.

### Version 1.13 (May 30, 2014)

-   Added a defensive NPE check to UserCredentialsProvider to prevent
    log file spamming when using private security realm.

### Version 1.12 (May 23, 2014)

-   Added a URI path domain requirement and specification to the
    standard API.

### Version 1.11 (May 21, 2014)

-   Fix the permission scope to flag that credential store permissions
    are scoped to items, item groups and Jenkins and not limited in
    scope to just Jenkins.
-   Added an annotation to provide future assistance in identifying
    string fields that hold credential ids.

### Version 1.10 (Feb 11, 2014)

-   Add /api/ support
-   Add support for domain restricted credentials that can further
    restrict themselves within a domain

### Version 1.9.4 (Dec 6, 2013)

-   Fixed issue with c:select and renderOnDemand on 1.500ish+ Jenkins
    instances
    ([JENKINS-20647](https://issues.jenkins-ci.org/browse/JENKINS-20647))

### Version 1.9.3 (Nov 8, 2013)

-   Minimum version of Jenkins is now 1.466
-   Added support for in-place adding of new credentials
    ([JENKINS-20072](https://issues.jenkins-ci.org/browse/JENKINS-20072))

### Version 1.9.2 (Nov 8, 2013)

-   UI improvements and bugfixes

### Version 1.9.1 (Oct 16, 2013)

-   Fix data binding issue with /lib/credentials/select.jelly

### Version 1.9 (Oct 11, 2013)

-   Make DomainRequirement serializable as it may need to be transferred
    across remoting channels
-   Update to German L10N
-   Add a /lib/credentials/select.jelly taglib to make it possible to
    retrofit and add credentials UI to plugins that use this for
    selecting a credential from a drop-down list (note there is a bug in
    this version that is fixed in 1.9.1 where it fails to correctly
    prepare data-binding)

### Version 1.8.3 (Sep 25, 2013)

-   Fixed [JENKINS-19735](https://issues.jenkins-ci.org/browse/JENKINS-19735)

### Version 1.8.2 (Sep 13, 2013)

-   Fixed [JENKINS-19575](https://issues.jenkins-ci.org/browse/JENKINS-19575)

### Version 1.8.1 (Sep 12, 2013)

-   Fixed some minor layout issues.
-   There is a bug in core with lazy rendering which will affect the
    ability to configure the credential scope via the new UI. Suspect
    this will [require a fix in Jenkins
    core](https://issues.jenkins-ci.org/browse/JENKINS-19565).

### Version 1.8 (Sep 12, 2013)

-   Added an API to allow plugins to configure credentials
-   Added an abstract Action to allow credential stores which permit
    configuration of credentials to expose a user-space UI for
    credential management
-   Added distinct permissions for viewing the credential management UI;
    managing credential domains; adding credentials; removing
    credentials; and updating credentials.
-   Added the user space UI to the system credentials
    provider: [JENKINS-19563](https://issues.jenkins-ci.org/browse/JENKINS-19563)

### Version 1.7.6 (Aug 28, 2013)

-   Exception in Manage Credentials screen in 1.7.5.

### Version 1.7.5 (Aug 28, 2013)

-   Fix issue with null values in domainCredentials.jelly taglib

### Version 1.7.4 (Aug 22, 2013)

-   Include fix
    for [JENKINS-19308](https://issues.jenkins-ci.org/browse/JENKINS-19308)
-   Add some more German translations

### Version 1.7.3 (Aug 16, 2013)

-   Include fix
    for [JENKINS-19216](https://issues.jenkins-ci.org/browse/JENKINS-19216)

### Version 1.7.2 (Aug 15, 2013)

-   Fix naming of StandardUsernamePasswordCredentials

### Version 1.7.1 (Aug 15, 2013)

-   Minor bug-fix in looking up names of credential instances.

### Version 1.7 (Aug 15, 2013)

-   Provide a standard client certificate credential implementation
    type.

### Version 1.6 (Aug 7, 2013)

-   Provide a standard username & password credential implementation
    type.
-   Add a builder for URI based domain requirements.
-   Add a ListBoxModel implementation to assist the common task of
    selecting a credential from a set of credentials.

### Version 1.5 (Jul 23, 2013)

-   Add some common credential type marker interfaces
-   Add API support for filtering credentials
-   Add support for partitioning credentials into domains

### Version 1.4 (Apr 15, 2013)

-   Add help page for scope.

###  Version 1.3 (Feb 27, 2012)

-   Missed renaming a critical stapler view.

### Version 1.2 (Feb 27, 2012)

-   Missed a critical constructor.

### Version 1.1 (Feb 27, 2012)

-   Missed a couple of cosmetic references in open-sourcing this
    previously closed source plugin

### Version 1.0 (Feb 27, 2012)

-   Initial release
