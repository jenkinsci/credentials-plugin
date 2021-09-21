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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.RootElementConfigurator;
import io.jenkins.plugins.casc.impl.attributes.DescribableAttribute;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.HashSet;
import java.util.Set;

import static io.jenkins.plugins.casc.Attribute.noop;


/**
 * a root element configurator used for configuring Jenkins credentials through {@link io.jenkins.plugins.casc.ConfigurationAsCode}
 * https://github.com/jenkinsci/configuration-as-code-plugin
 *
 * Credentials from the default {@link SystemCredentialsProvider} are handled by default.
 * Credentials provided by {@link CredentialsProvider} extensions are handled if there is a {@link BaseConfigurator} extension for it,
 * they will be ignored otherwise.
 */
@Extension(optional = true, ordinal = 2)
@Restricted(NoExternalUse.class)
public class CredentialsRootConfigurator extends BaseConfigurator<GlobalCredentialsConfiguration> implements RootElementConfigurator<GlobalCredentialsConfiguration> {

    @Override
    @NonNull
    public String getName() {
        return "credentials";
    }

    @Override
    public Class<GlobalCredentialsConfiguration> getTarget() {
        return GlobalCredentialsConfiguration.class;
    }

    @Override
    public GlobalCredentialsConfiguration getTargetComponent(ConfigurationContext context) {
        return GlobalCredentialsConfiguration.all().get(GlobalCredentialsConfiguration.class);
    }

    @Override
    public GlobalCredentialsConfiguration instance(Mapping mapping, ConfigurationContext context) {
        return getTargetComponent(context);
    }

    @Override
    @NonNull
    public Set<Attribute<GlobalCredentialsConfiguration,?>> describe() {
        HashSet<Attribute<GlobalCredentialsConfiguration, ?>> set = new HashSet<>();
        Attribute<GlobalCredentialsConfiguration, SystemCredentialsProvider> system = new Attribute<GlobalCredentialsConfiguration, SystemCredentialsProvider>("system", SystemCredentialsProvider.class)
                .getter(t -> SystemCredentialsProvider.getInstance())
                .setter(noop());
        set.add(system);
        for (CredentialsProvider provider : CredentialsProvider.all()) {
            String symbol = DescribableAttribute.getPreferredSymbol(provider.getDescriptor(), CredentialsProvider.class, provider.getClass());
            set.add(new Attribute<GlobalCredentialsConfiguration, CredentialsProvider>(symbol, provider.getClass())
                    .getter(t -> provider)
                    .setter(noop()));
        }
        return set;
    }

    @CheckForNull
    @Override
    public CNode describe(GlobalCredentialsConfiguration instance, ConfigurationContext context) throws Exception {
        Mapping mapping = new Mapping();
        for (Attribute attribute : describe()) {
            mapping.put(attribute.getName(), attribute.describe(instance, context));
        }
        return mapping;
    }

}
