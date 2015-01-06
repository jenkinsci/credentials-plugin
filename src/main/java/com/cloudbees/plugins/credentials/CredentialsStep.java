package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.domains.*;
import com.cloudbees.plugins.credentials.matchers.*;
import com.google.common.collect.Lists;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieves the first credential that matches all of the provided criteria.
 */
public class CredentialsStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(CredentialsStep.class.getName());

    /**
     * Matches against a credential's id
     */
    @Nullable
    private String id;

    /**
     * Matches credentials in the domain named {@code domain}
     */
    @Nullable
    private String domain;

    /**
     * Matches credentials in domains that meet the hostname requirement
     */
    @Nullable
    private String hostname;

    /**
     * Matches credentials with the given username
     */
    @Nullable
    private String username;

    /**
     * Matches credentials implementing or descending from a class named 'type'. Either a fully qualified
     * name or a simple name may be provided.
     */
    @Nullable
    private String type;

    /**
     * Matches credentials with the given {@code description}. Case insensitive.
     */
    @Nullable
    private String description;

    /**
     * Matches credentials in domains meeting the scheme, hostname, and path requirements
     * of the given URL.
     */
    @Nullable
    private String url;

    @DataBoundConstructor
    public CredentialsStep() {
    }

    @Nullable
    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setId(@Nullable String id) {
        this.id = id;
    }

    @Nullable
    public String getDomain() {
        return domain;
    }

    @DataBoundSetter
    public void setDomain(@Nullable String domain) {
        this.domain = domain;
    }

    @Nullable
    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(@Nullable String hostname) {
        this.hostname = hostname;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    @DataBoundSetter
    public void setType(@Nullable String type) {
        this.type = type;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(@Nullable String description) {
        this.description = description;
    }



    private CredentialsMatcher credentialsMatcher() {
        List<CredentialsMatcher> matchers = Lists.newArrayList();
        if(id != null) {
            matchers.add(new IdMatcher(id));
        }
        if(username != null) {
            matchers.add(new UsernameMatcher(username));
        }
        if(description != null) {
            matchers.add(new DescriptionMatcher(description));
        }
        if(type != null) {
            matchers.add(new TypeNameMatcher(type));
        }

        if(matchers.isEmpty()) {
            return new ConstantMatcher(true);
        } else {
            return new AllOfMatcher(matchers);
        }
    }

    private List<DomainRequirement> domainRequirement() {
        List<DomainRequirement> requirements = new ArrayList<DomainRequirement>();
        if(hostname != null) {
            requirements.add(new HostnameRequirement(hostname));
        }
        if(url != null) {
            try {
                URL parsedUrl = new URL(this.url);
                requirements.add(new SchemeRequirement(parsedUrl.getProtocol()));
                requirements.add(new HostnameRequirement(parsedUrl.getHost()));
                requirements.add(new PathRequirement(parsedUrl.getPath()));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Malformed URL: " + this.url);
            }
        }
        return requirements;
    }

    private boolean match(Domain domain) {
        if(this.domain == null) {
            return true;
        }
        return this.domain.equals(domain.getName());
    }

    public static final class ExecutionImpl extends AbstractSynchronousStepExecution<Credentials> {

        @Inject
        private transient CredentialsStep step;

        @StepContextParameter
        private transient Run<?, ?> run;

        @Override
        protected Credentials run() throws Exception {

            List<DomainRequirement> requirements = step.domainRequirement();
            CredentialsMatcher matcher = step.credentialsMatcher();

            LOGGER.log(Level.INFO, "Matcher: " + matcher);

            Map<Domain, List<Credentials>> map = SystemCredentialsProvider.getInstance().getDomainCredentialsMap();
            for (Map.Entry<Domain, List<Credentials>> domainEntry : map.entrySet()) {
                Domain domain = domainEntry.getKey();

                // Match domains both against the human-readable name
                // and against the formal domain requirements
                if(step.match(domain) && domain.test(requirements)) {
                    for (Credentials credentials : domainEntry.getValue()) {
                        if (matcher.matches(credentials)) {
                            return credentials;
                        }
                    }
                }
            }
            throw new AbortException("Could not find any credentials matching " + matcher);
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ExecutionImpl.class);
        }

        @Override
        public String getFunctionName() {
            return "credentials";
        }

        @Override
        public String getDisplayName() {
            return "Retrieve credential";
        }
    }
}
