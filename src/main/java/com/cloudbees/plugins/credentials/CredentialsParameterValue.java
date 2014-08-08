package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.WorkUnit;
import hudson.security.ACL;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Stephen Connolly
 */
public class CredentialsParameterValue extends ParameterValue {
    private final String value;
    private final boolean isDefaultValue;

    @DataBoundConstructor
    public CredentialsParameterValue(String name, String value, String description) {
        this(name, value, description, false);
    }

    public CredentialsParameterValue(String name, String value, String description, boolean isDefaultValue) {
        super(name, description);
        this.value = value;
        this.isDefaultValue = isDefaultValue;
    }

    @Override
    public boolean isSensitive() {
        return true;
    }

    public String getValue() {
        return value;
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return CredentialsParameterValue.this.name.equals(name) ? value : null;
            }
        };
    }

    public <C extends IdCredentials> C lookupCredentials(@NonNull Class<C> type, @NonNull Run run,
                                                         DomainRequirement... domainRequirements) {
        return lookupCredentials(type, run, Arrays.asList(domainRequirements));
    }

    public <C extends IdCredentials> C lookupCredentials(@NonNull Class<C> type, @NonNull Run run,
                                                         List<DomainRequirement> domainRequirements) {
        Authentication authentication = Jenkins.getAuthentication();
        final Executor executor = run.getExecutor();
        if (executor != null) {
            final WorkUnit workUnit = executor.getCurrentWorkUnit();
            if (workUnit != null) {
                authentication = workUnit.context.item.authenticate();
            }
        }
        if (authentication == null) {
            return null;
        }
        List<C> candidates = new ArrayList<C>();
        final boolean isSystem = ACL.SYSTEM.equals(authentication);
        if (!isSystem && run.getParent().getACL()
                .hasPermission(CredentialsProvider.USE_OWN)) {
            candidates.addAll(CredentialsProvider
                    .lookupCredentials(type, run.getParent(), authentication, domainRequirements));
        }
        if (run.getParent().getACL().hasPermission(CredentialsProvider.USE_ITEM) || isSystem
                || isDefaultValue) {
            candidates.addAll(
                    CredentialsProvider.lookupCredentials(type, run.getParent(), ACL.SYSTEM, domainRequirements));
        }
        return CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(value));
    }

    public String describe() {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        final Run run = Stapler.getCurrentRequest().findAncestorObject(Run.class);
        if (run == null) {
            throw new IllegalStateException("Should only be called from value.jelly");
        }
        StandardCredentials c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class, run.getParent(), ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return CredentialsNameProvider.name(c);
        }
        c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class, run.getParent(),
                        Jenkins.getAuthentication(),
                        Collections.<DomainRequirement>emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return CredentialsNameProvider.name(c);
        }
        return Messages.CredentialsParameterValue_NotAvailableToCurrentUser();
    }

    public boolean isDefaultValue() {
        return isDefaultValue;
    }
}
