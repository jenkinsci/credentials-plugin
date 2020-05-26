package com.cloudbees.plugins.credentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;

/**
 * {@link ManagementLink} to expose a link to the manage credentials configuration screen.
 * 
 * @since TODO
 */
@Extension(ordinal = Integer.MAX_VALUE - 211)
@Restricted(NoExternalUse.class)
public class ManageCredentialsConfiguration extends ManagementLink {

    public String getCategoryName() {
        return "SECURITY";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/credentials/images/48x48/credentials.png";
    }

    @Override
    public String getUrlName() {
        return "credentials";
    }

    @Override
    public String getDescription() {
        return Messages.ManageCredentialsConfiguration_description();
    }

    public String getDisplayName() {
        return Messages.ManageCredentialsConfiguration_displayName();
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return CredentialsProvider.VIEW;
    }
}
