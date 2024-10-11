package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Executor;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.WorkUnit;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.VariableResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.springframework.security.core.Authentication;

/**
 * A {@link ParameterValue} produced from a {@link CredentialsParameterDefinition}.
 */
public class CredentialsParameterValue extends ParameterValue {
    /**
     * The {@link StandardCredentials#getId()} of the selected credentials.
     */
    private final String value;
    /**
     * {@code true} if and only if the {@link #value} corresponds to
     * {@link CredentialsParameterDefinition#getDefaultValue()} (as this affects the authentication that is
     * used to resolve the actual credential.
     */
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

    public String getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        CredentialsProvider.track(build, lookupCredentials(StandardCredentials.class, build));
        env.put(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        CredentialsProvider.track(build, lookupCredentials(StandardCredentials.class, build));
        return name -> CredentialsParameterValue.this.name.equals(name) ? value : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSensitive() {
        return true;
    }

    public <C extends IdCredentials> C lookupCredentials(@NonNull Class<C> type, @NonNull Run run,
                                                         DomainRequirement... domainRequirements) {
        return lookupCredentials(type, run, Arrays.asList(domainRequirements));
    }

    public <C extends IdCredentials> C lookupCredentials(@NonNull Class<C> type, @NonNull Run run,
                                                         List<DomainRequirement> domainRequirements) {
        Authentication authentication = Jenkins.getAuthentication2();
        final Executor executor = run.getExecutor();
        if (executor != null) {
            final WorkUnit workUnit = executor.getCurrentWorkUnit();
            if (workUnit != null) {
                authentication = workUnit.context.item.authenticate2();
            }
        }
        List<C> candidates = new ArrayList<>();
        final boolean isSystem = ACL.SYSTEM2.equals(authentication);
        if (!isSystem && run.getParent().hasPermission(CredentialsProvider.USE_OWN)) {
            candidates.addAll(CredentialsProvider
                    .lookupCredentialsInItem(type, run.getParent(), authentication, domainRequirements));
        }
        if (run.getParent().hasPermission(CredentialsProvider.USE_ITEM) || isSystem
                || isDefaultValue) {
            candidates.addAll(
                    CredentialsProvider.lookupCredentialsInItem(type, run.getParent(), ACL.SYSTEM2, domainRequirements));
        }
        return CredentialsMatchers.firstOrNull(candidates, CredentialsMatchers.withId(value));
    }

    public String describe() {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        final Run run = Stapler.getCurrentRequest2().findAncestorObject(Run.class);
        if (run == null) {
            throw new IllegalStateException("Should only be called from value.jelly");
        }
        StandardCredentials c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(), ACL.SYSTEM2,
                        Collections.emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return CredentialsNameProvider.name(c);
        }
        c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(),
                        Jenkins.getAuthentication2(),
                        Collections.emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return CredentialsNameProvider.name(c);
        }
        return Messages.CredentialsParameterValue_NotAvailableToCurrentUser();
    }

    public String iconClassName() {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        final Run run = Stapler.getCurrentRequest2().findAncestorObject(Run.class);
        if (run == null) {
            throw new IllegalStateException("Should only be called from value.jelly");
        }
        StandardCredentials c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(), ACL.SYSTEM2,
                        Collections.emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return c.getDescriptor().getIconClassName();
        }
        c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(StandardCredentials.class, run.getParent(),
                        Jenkins.getAuthentication2(),
                        Collections.emptyList()), CredentialsMatchers.withId(value));
        if (c != null) {
            return c.getDescriptor().getIconClassName();
        }
        return "symbol-credentials plugin-credentials";
    }

    public String url() {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        final Run run = Stapler.getCurrentRequest2().findAncestorObject(Run.class);
        if (run == null) {
            throw new IllegalStateException("Should only be called from value.jelly");
        }
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            for (CredentialsStore store : CredentialsProvider.lookupStores(run.getParent())) {
                String url = url(store);
                if (url != null) {
                    return url;
                }
            }
        }
        for (CredentialsStore store: CredentialsProvider.lookupStores(User.current())) {
            String url = url(store);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private String url(CredentialsStore store) {
        for (Domain d: store.getDomains()) {
            for (Credentials c: store.getCredentials(d)) {
                if (c instanceof IdCredentials && value.equals(((IdCredentials) c).getId())) {
                    String link = store.getRelativeLinkToAction();
                    return link == null ? null : link + d.getUrl() + "credential/"+ Util.rawEncode(value);
                }
            }
        }
        return null;
    }

    public boolean isDefaultValue() {
        return isDefaultValue;
    }
}
