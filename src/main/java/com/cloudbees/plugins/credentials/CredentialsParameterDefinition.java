package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;

/**
 * A {@link ParameterDefinition} for a parameter that supplies a {@link Credentials}.
 */
public class CredentialsParameterDefinition extends SimpleParameterDefinition {
    /**
     * The default credential id.
     */
    private final String defaultValue;
    /**
     * The type of credential (a class name).
     */
    private final String credentialType;
    /**
     * Whether to fail the build if the credential cannot be resolved.
     */
    private final boolean required;

    @DataBoundConstructor
    public CredentialsParameterDefinition(String name, String description, String defaultValue, String credentialType,
                                          boolean required) {
        super(name, description);
        this.defaultValue = defaultValue;
        this.credentialType = credentialType;
        this.required = required;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof CredentialsParameterValue) {
            CredentialsParameterValue value = (CredentialsParameterValue) defaultValue;
            return new CredentialsParameterDefinition(getName(), getDescription(), value.getValue(),
                    getCredentialType(), isRequired());
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public ParameterValue getDefaultParameterValue() {
        return new CredentialsParameterValue(getName(), getDefaultValue(), getDescription(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParameterValue createValue(String value) {
        return new CredentialsParameterValue(getName(), value, getDescription(),
                StringUtils.equals(value, defaultValue));
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
     * Our descriptor.
     */
    @Extension
    @Symbol("credentials")
    public static class DescriptorImpl extends ParameterDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
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
                if (StandardCredentials.class.isAssignableFrom(descriptor.clazz)) {
                    result.add(descriptor.getDisplayName(), descriptor.clazz.getName());
                }
            }
            return result;
        }

        private Class<? extends StandardCredentials> decodeType(String credentialType) {
            for (Descriptor<Credentials> d : CredentialsProvider.allCredentialsDescriptors()) {
                if (!(d instanceof CredentialsDescriptor)) {
                    continue;
                }
                CredentialsDescriptor descriptor = (CredentialsDescriptor) d;
                if (!StandardCredentials.class.isAssignableFrom(descriptor.clazz)) {
                    continue;
                }
                if (credentialType.equals(descriptor.clazz.getName())) {
                    return descriptor.clazz.asSubclass(StandardCredentials.class);
                }
            }
            return StandardCredentials.class;
        }

        private boolean match(Set<Class<? extends StandardCredentials>> allowed, StandardCredentials instance) {
            for (Class<? extends StandardCredentials> b : allowed) {
                if (b.isInstance(instance)) {
                    return true;
                }
            }
            return false;
        }

        public StandardListBoxModel doFillDefaultValueItems(@AncestorInPath Item context,
                                                            @QueryParameter(required = true) String credentialType) {
            Jenkins jenkins = Jenkins.get();
            final ACL acl = context == null ? jenkins.getACL() : context.getACL();
            final Class<? extends StandardCredentials> typeClass = decodeType(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            if (acl.hasPermission(CredentialsProvider.USE_ITEM)) {
                result.includeAs(CredentialsProvider.getDefaultAuthenticationOf2(context), context, typeClass, domainRequirements);
            }
            return result;
        }

        public StandardListBoxModel doFillValueItems(@AncestorInPath Item context,
                                                     @QueryParameter(required = true) String credentialType,
                                                     @QueryParameter String value,
                                                     @QueryParameter boolean required,
                                                     @QueryParameter boolean includeUser) {
            Jenkins jenkins = Jenkins.get();
            final ACL acl = context == null ? jenkins.getACL() : context.getACL();
            final Authentication authentication = Jenkins.getAuthentication2();
            final Authentication itemAuthentication = CredentialsProvider.getDefaultAuthenticationOf2(context);
            final boolean isSystem = ACL.SYSTEM2.equals(authentication);
            final Class<? extends StandardCredentials> typeClass = decodeType(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            if (!required) {
                result.includeEmptyValue();
            }
            if (!isSystem && acl.hasPermission(CredentialsProvider.USE_OWN) && includeUser) {
                result.includeAs(authentication, context, typeClass, domainRequirements);
            }
            if (acl.hasPermission(CredentialsProvider.USE_ITEM) || isSystem || itemAuthentication
                    .equals(authentication)) {
                result.includeAs(itemAuthentication, context, typeClass, domainRequirements);
            }
            result.includeCurrentValue(value);
            return result;
        }
    }
}
