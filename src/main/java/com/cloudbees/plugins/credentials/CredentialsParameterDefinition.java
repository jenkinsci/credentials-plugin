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
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
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
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
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

        /**
         * Resolves the {@link CredentialsDescriptor} whose concrete implementation class name matches the
         * supplied {@code credentialType}, or {@code null} if the type could not be resolved (e.g. {@code "Any"}).
         */
        private CredentialsDescriptor decodeTypeDescriptor(String credentialType) {
            for (Descriptor<Credentials> d : CredentialsProvider.allCredentialsDescriptors()) {
                if (!(d instanceof CredentialsDescriptor)) {
                    continue;
                }
                CredentialsDescriptor descriptor = (CredentialsDescriptor) d;
                if (!StandardCredentials.class.isAssignableFrom(descriptor.clazz)) {
                    continue;
                }
                if (credentialType.equals(descriptor.clazz.getName())) {
                    return descriptor;
                }
            }
            return null;
        }

        /**
         * Builds a {@link CredentialsMatcher} for the supplied {@code credentialType} that matches credentials
         * which either are an instance of the concrete implementation class or, for external providers that
         * return credentials backed by e.g. a {@link java.lang.reflect.Proxy} of the public API interfaces,
         * whose {@link Descriptor} is the one associated with the requested type. This ensures that such
         * proxy-backed credentials are not excluded purely because they do not extend the concrete
         * implementation class.
         */
        private CredentialsMatcher decodeTypeMatcher(String credentialType) {
            CredentialsDescriptor descriptor = decodeTypeDescriptor(credentialType);
            if (descriptor == null) {
                return CredentialsMatchers.always();
            }
            return CredentialsMatchers.anyOf(
                    CredentialsMatchers.instanceOf(descriptor.clazz),
                    new DescriptorMatcher(descriptor)
            );
        }

        /**
         * A {@link CredentialsMatcher} that matches credentials whose {@link Descriptor} is the supplied
         * {@link CredentialsDescriptor}. This allows matching credentials that do not extend the concrete
         * implementation class associated with the descriptor, such as {@link java.lang.reflect.Proxy} backed
         * credentials returned by external credential providers.
         */
        private static class DescriptorMatcher implements CredentialsMatcher {
            private static final long serialVersionUID = 1L;
            private final CredentialsDescriptor descriptor;

            DescriptorMatcher(CredentialsDescriptor descriptor) {
                this.descriptor = descriptor;
            }

            @Override
            public boolean matches(@NonNull Credentials item) {
                return descriptor.equals(item.getDescriptor());
            }
        }

        public StandardListBoxModel doFillDefaultValueItems(@AncestorInPath Item context,
                                                            @QueryParameter(required = true) String credentialType) {
            Jenkins jenkins = Jenkins.get();
            final ACL acl = context == null ? jenkins.getACL() : context.getACL();
            final CredentialsMatcher matcher = decodeTypeMatcher(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            if (acl.hasPermission(CredentialsProvider.USE_ITEM)) {
                result.includeMatchingAs(CredentialsProvider.getDefaultAuthenticationOf2(context), context,
                        StandardCredentials.class, domainRequirements, matcher);
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
            final CredentialsMatcher matcher = decodeTypeMatcher(credentialType);
            final List<DomainRequirement> domainRequirements = Collections.emptyList();
            final StandardListBoxModel result = new StandardListBoxModel();
            if (!required) {
                result.includeEmptyValue();
            }
            if (!isSystem && acl.hasPermission(CredentialsProvider.USE_OWN) && includeUser) {
                result.includeMatchingAs(authentication, context, StandardCredentials.class, domainRequirements, matcher);
            }
            if (acl.hasPermission(CredentialsProvider.USE_ITEM) || isSystem || itemAuthentication
                    .equals(authentication)) {
                result.includeMatchingAs(itemAuthentication, context, StandardCredentials.class, domainRequirements, matcher);
            }
            result.includeCurrentValue(value);
            return result;
        }
    }
}
