/*
 * The MIT License
 *
 *  Copyright (c) 2018, CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */

package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.impl.attributes.MultivaluedAttribute;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A configurator for system credentials provider located beneath the {@link CredentialsRootConfigurator}. The default
 * merge strategy will replace all existing credentials. To merge CasC credentials with existing credentials use
 * the env var {@code CASC_CREDENTIALS_MERGE_STRATEGY} or system property {@code casc.credentials.merge.strategy}
 * to set the strategy to "{@code merge}". The "{@code merge}" strategy will not remove credentials don't exist in
 * CasC configuration.
 * 
 * @see SystemCredentialsProvider#mergeDomainCredentialsMap(Map) 
 * @see SystemCredentialsProvider#setDomainCredentialsMap(Map)
 */
@Extension(optional = true, ordinal = 2)
@Restricted(NoExternalUse.class)
public class SystemCredentialsProviderConfigurator extends BaseConfigurator<SystemCredentialsProvider> {

    @Override
    public Class<SystemCredentialsProvider> getTarget() {
        return SystemCredentialsProvider.class;
    }

    @Override
    protected SystemCredentialsProvider instance(Mapping mapping, ConfigurationContext context) {
        return SystemCredentialsProvider.getInstance();
    }

    @NonNull
    @Override
    public Set<Attribute<SystemCredentialsProvider, ?>> describe() {
        return Collections.singleton(
            new MultivaluedAttribute<SystemCredentialsProvider, DomainCredentials>("domainCredentials", DomainCredentials.class)
                .setter((target, value) -> {
                    String strategy = Util.fixEmptyAndTrim(
                            System.getProperty("casc.credentials.merge.strategy",
                            System.getenv("CASC_CREDENTIALS_MERGE_STRATEGY")
                    ));

                    if ("merge".equalsIgnoreCase(strategy)) {
                        target.mergeDomainCredentialsMap(DomainCredentials.asMap(value));
                    } else {
                        target.setDomainCredentialsMap(DomainCredentials.asMap(value));
                    }
                })
        );
    }

    @CheckForNull
    @Override
    public CNode describe(SystemCredentialsProvider instance, ConfigurationContext context) throws Exception {
        Mapping mapping = new Mapping();
        for (Attribute<SystemCredentialsProvider, ?> attribute : describe()) {
            mapping.put(attribute.getName(), attribute.describe(instance, context));
        }
        return mapping;
    }

}
