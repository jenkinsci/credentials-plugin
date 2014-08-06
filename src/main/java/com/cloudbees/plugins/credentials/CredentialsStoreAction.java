/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc..
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Stephen Connolly
 */
public abstract class CredentialsStoreAction implements Action {

    public static final Permission VIEW = CredentialsProvider.VIEW;
    public static final Permission CREATE = CredentialsProvider.CREATE;
    public static final Permission UPDATE = CredentialsProvider.UPDATE;
    public static final Permission DELETE = CredentialsProvider.DELETE;
    public static final Permission MANAGE_DOMAINS = CredentialsProvider.MANAGE_DOMAINS;

    @NonNull
    public abstract CredentialsStore getStore();

    public String getIconFileName() {
        if (CredentialsProvider.allCredentialsDescriptors().isEmpty()) {
            return null;
        }
        return getStore().hasPermission(CredentialsProvider.VIEW)
                ? "/plugin/credentials/images/48x48/credentials.png"
                : null;
    }

    public String getDisplayName() {
        return Messages.CredentialsStoreAction_DisplayName();
    }

    public String getUrlName() {
        return "credential-store";
    }

    public final String getFullName() {
        String n;
        ModelObject context = getStore().getContext();
        if (context instanceof Item) {
            n = ((Item) context).getFullName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullName();
        } else {
            n = "";
        }
        if (n.length() == 0) {
            return getUrlName();
        } else {
            return n + '/' + getUrlName();
        }
    }

    public final String getFullDisplayName() {
        String n;
        ModelObject context = getStore().getContext();
        if (context instanceof Item) {
            n = ((Item) context).getFullDisplayName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullDisplayName();
        } else {
            n = Jenkins.getInstance().getFullDisplayName();
        }
        if (n.length() == 0) {
            return getDisplayName();
        } else {
            return n + " \u00BB " + getDisplayName();
        }
    }

    @Exported
    public Map<String, DomainWrapper> getDomains() {
        Map<String, DomainWrapper> result = new TreeMap<String, DomainWrapper>();
        for (Domain d : getStore().getDomains()) {
            String name;
            if (d == Domain.global()) {
                name = "_";
            } else {
                name = d.getName();
            }
            result.put(name, new DomainWrapper(this, d));
        }
        return result;
    }

    public DomainWrapper getDomain(String name) {
        return getDomains().get(name);
    }

    public boolean isDomainsModifiable() {
        return getStore().isDomainsModifiable();
    }

    public DomainWrapper.DescriptorImpl getDomainDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DomainWrapper.DescriptorImpl.class);
    }

    /**
     * Gets all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     *
     * @return all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    public DescriptorExtensionList<DomainSpecification, Descriptor<DomainSpecification>> getSpecificationDescriptors() {
        return Jenkins.getInstance().getDescriptorList(DomainSpecification.class);
    }

    @RequirePOST
    public HttpResponse doCreateDomain(StaplerRequest req) throws ServletException, IOException {
        getStore().checkPermission(MANAGE_DOMAINS);
        JSONObject data = req.getSubmittedForm();
        Domain domain = req.bindJSON(Domain.class, data);
        if (getStore().addDomain(domain)) {
            return HttpResponses.redirectTo("./domain/" + Util.rawEncode(domain.getName()));

        }
        return HttpResponses.redirectToDot();
    }

    @ExportedBean
    public static class DomainWrapper extends AbstractDescribableImpl<DomainWrapper> implements ModelObject {

        private final CredentialsStoreAction parent;
        private final Domain domain;

        public DomainWrapper(CredentialsStoreAction parent, Domain domain) {
            this.parent = parent;
            this.domain = domain;
        }

        public Api getApi() {
            return new Api(this);
        }

        public CredentialsStore getStore() {
            return getParent().getStore();
        }

        public Domain getDomain() {
            return domain;
        }

        public CredentialsStoreAction getParent() {
            return parent;
        }

        @Exported
        public String getUrlName() {
            return isGlobal() ? "_" : Util.rawEncode(domain.getName());
        }

        public String getDisplayName() {
            return isGlobal() ? Messages.CredentialsStoreAction_GlobalDomainDisplayName() : domain.getName();
        }

        @Exported
        public final String getFullName() {
            String n = getParent().getFullName();
            if (n.length() == 0) {
                return getUrlName();
            } else {
                return n + '/' + getUrlName();
            }
        }

        public final String getFullDisplayName() {
            String n = getParent().getFullDisplayName();
            if (n.length() == 0) {
                return getDisplayName();
            } else {
                return n + " \u00BB " + getDisplayName();
            }
        }

        public String getDescription() {
            return isGlobal() ? Messages.CredentialsStoreAction_GlobalDomainDescription() : domain.getDescription();
        }

        @Exported
        public boolean isGlobal() {
            return domain == Domain.global();
        }

        public CredentialsWrapper.DescriptorImpl getCredentialDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(CredentialsWrapper.DescriptorImpl.class);
        }

        @Exported
        public Map<String, CredentialsWrapper> getCredentials() {
            Map<String, CredentialsWrapper> result = new LinkedHashMap<String, CredentialsWrapper>();
            int index = 0;
            for (Credentials c : getStore().getCredentials(domain)) {
                String id;
                if (c instanceof IdCredentials) {
                    id = ((IdCredentials) c).getId();
                } else {
                    while (result.containsKey("index-" + index)) {
                        index++;
                    }
                    id = "index-" + index;
                    index++;
                }
                result.put(id, new CredentialsWrapper(this, c, id));
            }
            return result;
        }

        public CredentialsWrapper getCredential(String id) {
            return getCredentials().get(id);
        }

        @RequirePOST
        public HttpResponse doCreateCredentials(StaplerRequest req) throws ServletException, IOException {
            getStore().checkPermission(CREATE);
            JSONObject data = req.getSubmittedForm();
            Credentials credentials = req.bindJSON(Credentials.class, data.getJSONObject("credentials"));
            getStore().addCredentials(domain, credentials);
            return HttpResponses.redirectTo("../../domain/" + getUrlName());
        }

        @RequirePOST
        public HttpResponse doConfigSubmit(StaplerRequest req) throws ServletException, IOException {
            if (!getStore().isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            getStore().checkPermission(MANAGE_DOMAINS);
            JSONObject data = req.getSubmittedForm();
            Domain domain = req.bindJSON(Domain.class, data);
            if (getStore().updateDomain(this.domain, domain)) {
                return HttpResponses.redirectTo("../../domain/" + Util.rawEncode(domain.getName()));

            }
            return HttpResponses.redirectToDot();
        }

        @RequirePOST
        public HttpResponse doDoDelete(StaplerRequest req) throws IOException {
            if (!getStore().isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            getStore().checkPermission(MANAGE_DOMAINS);
            if (getStore().removeDomain(domain)) {
                return HttpResponses.redirectTo("../..");
            }
            return HttpResponses.redirectToDot();
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<DomainWrapper> {

            public DescriptorImpl() {
                super(DomainWrapper.class);
            }

            @Override
            public String getDisplayName() {
                return "Domain";
            }

            public FormValidation doCheckName(@AncestorInPath DomainWrapper wrapper,
                                              @AncestorInPath CredentialsStoreAction action,
                                              @QueryParameter String value) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.warning(Messages.CredentialsStoreAction_EmptyDomainNameMessage());
                }
                try {
                    Jenkins.checkGoodName(value);
                } catch (Failure e) {
                    return FormValidation.error(e.getMessage());
                }
                if (action != null) {
                    for (Domain d : action.getStore().getDomains()) {
                        if (wrapper != null && wrapper.domain == d) {
                            continue;
                        }
                        if (value.equals(d.getName())) {
                            return FormValidation.error(Messages.CredentialsStoreAction_DuplicateDomainNameMessage());
                        }
                    }
                }
                return FormValidation.ok();
            }

        }
    }

    @ExportedBean
    public static class CredentialsWrapper extends AbstractDescribableImpl<CredentialsWrapper> {

        private final DomainWrapper domain;

        private final Credentials credentials;

        private final String id;

        public CredentialsWrapper(DomainWrapper domain, Credentials credentials, String id) {
            this.domain = domain;
            this.credentials = credentials;
            this.id = id;
        }

        public String getUrlName() {
            return Util.rawEncode(id);
        }

        public Api getApi() {
            return new Api(this);
        }

        public String getDisplayName() {
            return CredentialsNameProvider.name(credentials);
        }

        @Exported
        public String getTypeName() {
            return credentials.getDescriptor().getDisplayName();
        }

        public String getDescription() {
            return credentials instanceof StandardCredentials
                    ? ((StandardCredentials) credentials).getDescription()
                    : null;
        }

        @Exported
        public final String getFullName() {
            String n = getDomain().getFullName();
            if (n.length() == 0) {
                return getUrlName();
            } else {
                return n + '/' + getUrlName();
            }
        }

        public final String getFullDisplayName() {
            String n = getDomain().getFullDisplayName();
            if (n.length() == 0) {
                return getDisplayName();
            } else {
                return n + " \u00BB " + getDisplayName();
            }
        }

        public Credentials getCredentials() {
            return credentials;
        }

        public DomainWrapper getDomain() {
            return domain;
        }

        public DomainWrapper getParent() {
            return domain;
        }

        public CredentialsStore getStore() {
            return domain.getStore();
        }

        @RequirePOST
        public HttpResponse doDoDelete(StaplerRequest req) throws IOException {
            if (!getStore().isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            getStore().checkPermission(DELETE);
            if (getStore().removeCredentials(domain.getDomain(),credentials)) {
                return HttpResponses.redirectTo("../..");
            }
            return HttpResponses.redirectToDot();
        }

        @RequirePOST
        public HttpResponse doDoMove(StaplerRequest req, @QueryParameter String destination) throws IOException {
            if (!getStore().isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            getStore().checkPermission(DELETE);
            final String splitKey = domain.getParent().getUrlName() + "/";
            int split = destination.lastIndexOf(splitKey);
            if (split == -1) {
                return HttpResponses.status(400);
            }
            String contextName = destination.substring(0, split);
            String domainName = destination.substring(split + splitKey.length());
            ModelObject context = null;
            if ("".equals(contextName)) {
                context = Jenkins.getInstance();
            } else {
                while (context == null && split > 0) {
                    context = Jenkins.getInstance().getItemByFullName(contextName);
                    if (context == null) {
                        split = destination.lastIndexOf(splitKey, split - 1);
                        contextName = destination.substring(0, split);
                        domainName = destination.substring(split + splitKey.length());
                    }
                }
            }
            if (context == null) {
                return HttpResponses.status(400);
            }
            CredentialsStore destinationStore = null;
            Domain destinationDomain = null;
            for (CredentialsStore store : CredentialsProvider.lookupStores(context)) {
                if (store.getContext() == context) {
                    for (Domain d : store.getDomains()) {
                        if (domainName.equals("_") ? d.getName() == null : domainName.equals(d.getName())) {
                            destinationStore = store;
                            destinationDomain = d;
                            break;
                        }
                    }
                }
            }
            if (destinationDomain == null) {
                return HttpResponses.status(400);
            }
            if (!destinationStore.isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            destinationStore.checkPermission(CREATE);
            if (destinationDomain.equals(domain.getDomain())) {
                return HttpResponses.redirectToDot();
            }

            if (destinationStore.addCredentials(destinationDomain, credentials)) {
                if (getStore().removeCredentials(domain.getDomain(), credentials)) {
                    return HttpResponses.redirectTo("../..");
                } else {
                    destinationStore.removeCredentials(destinationDomain, credentials);
                }
            }
            return HttpResponses.redirectToDot();
        }

        @RequirePOST
        public HttpResponse doUpdateSubmit(StaplerRequest req) throws ServletException, IOException {
            getStore().checkPermission(UPDATE);
            JSONObject data = req.getSubmittedForm();
            Credentials credentials = req.bindJSON(Credentials.class, data);
            if (!getStore().updateCredentials(this.domain.domain, this.credentials, credentials)) {
                return HttpResponses.redirectTo("concurrentModification");
            }
            return HttpResponses.redirectToDot();
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<CredentialsWrapper> {

            @Override
            public String getDisplayName() {
                return "Credential";
            }

            public DescriptorExtensionList<Credentials, CredentialsDescriptor> getCredentialDescriptors() {
                return Jenkins.getInstance().getDescriptorList(Credentials.class);
            }
        }
    }


}
