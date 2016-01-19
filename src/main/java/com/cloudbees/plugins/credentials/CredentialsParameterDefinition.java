package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Stephen Connolly
 */
public class CredentialsParameterDefinition extends SimpleParameterDefinition {
    private final String defaultValue;
    private final String credentialType;
    private final boolean required;

    @DataBoundConstructor
    public CredentialsParameterDefinition(String name, String description, String defaultValue, String credentialType, boolean required) {
        super(name, description);
        this.defaultValue = defaultValue;
        this.credentialType = credentialType;
        this.required = required;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof CredentialsParameterValue) {
            CredentialsParameterValue value = (CredentialsParameterValue) defaultValue;
            return new CredentialsParameterDefinition(getName(), value.getValue(), getDescription(), getCredentialType(), isRequired());
        }
        return this;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new CredentialsParameterValue(getName(), value, getDescription(), StringUtils.equals(value, defaultValue));
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        CredentialsParameterValue value = req.bindJSON(CredentialsParameterValue.class, jo);
        if ((isRequired() && StringUtils.isBlank(value.getValue()))) {
            return new CredentialsParameterValue(value.getName(), getDefaultValue(), getDescription(), true);
        }
        return new CredentialsParameterValue(
                value.getName(), value.getValue(), getDescription(),
                StringUtils.equals(value.getValue(), getDefaultValue())
        );
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        return new CredentialsParameterValue(getName(), getDefaultValue(), getDescription(), true);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * @author Stephen Connolly
     */
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.CredentialsParameterDefinition_DisplayName();
        }

        public ListBoxModel doFillCredentialTypeItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("Any", StandardCredentials.class.getName());
            for (Descriptor<Credentials> d : CredentialsProvider.allCredentialsDescriptors()) {
                if (!(d instanceof CredentialsDescriptor)) {
                    continue;
                }
                CredentialsDescriptor descriptor = (CredentialsDescriptor) d;
                if (StandardCredentials.class.isAssignableFrom(descriptor.clazz))
                    result.add(descriptor.getDisplayName(), descriptor.clazz.getName());
            }
            return result;
        }

        private Class<? extends StandardCredentials> decodeType(String credentialType) {
            for (Descriptor<Credentials> d: CredentialsProvider.allCredentialsDescriptors()) {
                if (!(d instanceof CredentialsDescriptor)) {
                    continue;
                }
                CredentialsDescriptor descriptor = (CredentialsDescriptor) d;
                if (!StandardCredentials.class.isAssignableFrom(descriptor.clazz)) {
                    continue;
                }
                if (credentialType.equals(descriptor.clazz.getName())) {
                    return (Class<? extends StandardCredentials>) descriptor.clazz;
                }
            }
            return StandardCredentials.class;
        }

        private boolean match(Set<Class<? extends StandardCredentials>> allowed, StandardCredentials instance) {
            for (Class<? extends StandardCredentials> b: allowed) {
                if (b.isInstance(instance))
                    return true;
            }
            return false;
        }

        public StandardListBoxModel doFillDefaultValueItems(@AncestorInPath Item context,
                                                            @QueryParameter(required = true) String credentialType) {
            // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
            final ACL acl = context == null ? jenkins.getACL() : context.getACL();
            final Set<String> ids = new HashSet<String>();
            final Class<? extends StandardCredentials> typeClass = decodeType(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.<DomainRequirement>emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            if (acl.hasPermission(CredentialsProvider.USE_ITEM)) {
                for (StandardCredentials s : CredentialsProvider
                        .lookupCredentials(typeClass, context, CredentialsProvider.getDefaultAuthenticationOf(context), domainRequirements)) {
                    if (!ids.contains(s.getId())) {
                        result.with(s);
                        ids.add(s.getId());
                    }
                }
            }
            return result;
        }

        public StandardListBoxModel doFillValueItems(@AncestorInPath Item context,
                                                     @QueryParameter(required = true) String credentialType,
                                                     @QueryParameter boolean required) {
            // TODO switch to Jenkins.getActiveInstance() once 1.590+ is the baseline
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
            final ACL acl = context == null ? jenkins.getACL() : context.getACL();
            final Authentication authentication = Jenkins.getAuthentication();
            final Authentication itemAuthentication = CredentialsProvider.getDefaultAuthenticationOf(context);
            final boolean isSystem = ACL.SYSTEM.equals(authentication);
            final Set<String> ids = new HashSet<String>();
            final Class<? extends StandardCredentials> typeClass = decodeType(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.<DomainRequirement>emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            if (!required) {
                result.withEmptySelection();
            }
            if (!isSystem && acl.hasPermission(CredentialsProvider.USE_OWN)) {
                for (StandardCredentials s : CredentialsProvider
                        .lookupCredentials(typeClass, context, authentication, domainRequirements)) {
                    if (!ids.contains(s.getId())) {
                        result.with(s);
                        ids.add(s.getId());
                    }
                }
            }
            if (acl.hasPermission(CredentialsProvider.USE_ITEM) || isSystem || itemAuthentication.equals(authentication)) {
                for (StandardCredentials s : CredentialsProvider
                        .lookupCredentials(typeClass, context, itemAuthentication, domainRequirements)) {
                    if (!ids.contains(s.getId())) {
                        result.with(s);
                        ids.add(s.getId());
                    }
                }
            }
            return result;
        }
    }
}
