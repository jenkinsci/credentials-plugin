/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc..
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
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Fingerprint;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import hudson.util.XStream2;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.util.xml.XMLUtils;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.xml.sax.SAXException;

import static com.cloudbees.plugins.credentials.ContextMenuIconUtils.getMenuItemIconUrlByClassSpec;

/**
 * An action for a {@link CredentialsStore}
 */
@ExportedBean
public abstract class CredentialsStoreAction
        implements Action, IconSpec, AccessControlled, ModelObjectWithContextMenu, ModelObjectWithChildren {

    /**
     * Expose {@link CredentialsProvider#VIEW} for Jelly.
     */
    public static final Permission VIEW = CredentialsProvider.VIEW;
    /**
     * Expose {@link CredentialsProvider#CREATE} for Jelly.
     */
    public static final Permission CREATE = CredentialsProvider.CREATE;
    /**
     * Expose {@link CredentialsProvider#UPDATE} for Jelly.
     */
    public static final Permission UPDATE = CredentialsProvider.UPDATE;
    /**
     * Expose {@link CredentialsProvider#DELETE} for Jelly.
     */
    public static final Permission DELETE = CredentialsProvider.DELETE;
    /**
     * Expose {@link CredentialsProvider#MANAGE_DOMAINS} for Jelly.
     */
    public static final Permission MANAGE_DOMAINS = CredentialsProvider.MANAGE_DOMAINS;

    /**
     * An {@link XStream2} that replaces {@link Secret} instances with {@literal REDACTED}
     *
     * @since 2.1.1
     */
    public static final XStream2 SECRETS_REDACTED;

    static {
        SECRETS_REDACTED = new XStream2();
        SECRETS_REDACTED.registerConverter(new Converter() {
            /**
             * {@inheritDoc}
             */
            public boolean canConvert(Class type) {
                return type == Secret.class || type == SecretBytes.class;
            }

            /**
             * {@inheritDoc}
             */
            public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                writer.startNode("secret-redacted");
                writer.endNode();
            }

            /**
             * {@inheritDoc}
             */
            public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
                return null;
            }
        });
    }

    /**
     * Returns the {@link CredentialsStore} backing this action.
     *
     * @return the {@link CredentialsStore}.
     */
    @NonNull
    public abstract CredentialsStore getStore();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return isVisible()
                ? "/plugin/credentials/images/24x24/credentials.png"
                : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        CredentialsStore store = getStore();
        if (this == store.getStoreAction()) {
            Class<?> c = store.getClass();
            while (c.getEnclosingClass() != null) {
                c = c.getEnclosingClass();
            }
            String name = c.getSimpleName().replaceAll("(?i)(Impl|Credentials|Provider|Store)+", "");
            if (StringUtils.isBlank(name)) {
                name = c.getSimpleName();
            }
            return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(name), ' ');
        } else {
            return Messages.CredentialsStoreAction_DisplayName();
        }
    }

    /**
     * Any additional actions to display for this {@link CredentialsStore}.
     *
     * @return Any additional actions to display for this {@link CredentialsStore}.
     * @since 2.0
     */
    @NonNull
    public List<Action> getActions() {
        return Collections.emptyList();
    }

    /**
     * Exposes the {@link #getActions()} for Stapler.
     *
     * @param token the name of the action.
     * @return the {@link Action} or {@code null}
     * @since 2.0
     */
    @CheckForNull
    @SuppressWarnings("unused") // stapler binding
    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url == null) {
                continue;
            }
            if (url.equals(token)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Creates the context menu with the supplied prefix to all URLs.
     *
     * @param prefix the prefix to prepend to relative urls.
     * @return the {@link ContextMenu} or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public ContextMenu getContextMenu(String prefix) {
        ContextMenu menu = new ContextMenu();
        if (getStore().isDomainsModifiable() && getStore().hasPermission(MANAGE_DOMAINS)) {
            menu.add(ContextMenuIconUtils.buildUrl(prefix, "newDomain"),
                    getMenuItemIconUrlByClassSpec("icon-credentials-new-domain icon-md"),
                    Messages.CredentialsStoreAction_AddDomainAction()
            );
        }
        for (Action action : getActions()) {
            ContextMenuIconUtils.addMenuItem(menu, prefix, action);
        }
        return menu.items.isEmpty() ? null : menu;
    }

    /**
     * Creates the children context menu with the supplied prefix to all URLs.
     *
     * @param prefix the prefix to prepend to relative urls.
     * @return the {@link ContextMenu} or {@code null}
     * @since 2.0
     */
    @CheckForNull
    public ContextMenu getChildrenContextMenu(String prefix) {
        ContextMenu menu = new ContextMenu();
        for (Domain d : getStore().getDomains()) {
            MenuItem item =
                    new MenuItem(d.getUrl(), getMenuItemIconUrlByClassSpec("icon-credentials-domain icon-md"),
                            d.isGlobal()
                                    ? Messages.CredentialsStoreAction_GlobalDomainDisplayName()
                                    : d.getName()
                    );
            item.subMenu = new DomainWrapper(this, d).getContextMenu(ContextMenuIconUtils.buildUrl(prefix, d.getUrl()));
            menu.add(item);
        }
        return menu.items.isEmpty() ? null : menu;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return getContextMenu("");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request,
                                             StaplerResponse response) throws Exception {
        return getChildrenContextMenu("");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        CredentialsStore store = getStore();
        if (this == store.getStoreAction()) {
            Class<?> c = store.getClass();
            while (c.getEnclosingClass() != null) {
                c = c.getEnclosingClass();
            }
            String name = c.getSimpleName().replaceAll("(?i)(Impl|Credentials|Provider|Store)+", "");
            if (StringUtils.isBlank(name)) {
                name = c.getSimpleName();
            }
            return StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(name), '-').toLowerCase(Locale.ENGLISH);
        } else {
            return "credential-store";
        }
    }

    /**
     * Expose the action's {@link Api}.
     *
     * @return the action's {@link Api}.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Checks if this action should be visible.
     *
     * @return {@code true} if the action should be visible.
     */
    public boolean isVisible() {
        CredentialsStore store = getStore();
        if (!store.getProvider().isEnabled()) {
            return false;
        }
        CredentialsStoreAction storeAction = store.getStoreAction();
        if (storeAction != null && this != storeAction) {
            // 2.0+ implementations of CredentialsStore should be returning their action via getStoreAction()
            // and we want to display that action from ViewCredentialsAction
            // Old implementations will be returning null from getStoreAction() so we let them display as before
            // Forward looking implementations written against the old API will want to "hide" their old
            // action and display the new one returned from getStoreAction() which is what this hook enables.
            return false;
        }
        return store.hasPermission(CredentialsProvider.VIEW) && !store.getCredentialsDescriptors().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return isVisible()
                ? "icon-credentials-credentials"
                : null;
    }

    /**
     * Returns the {@link Item#getFullName()} or nearest approximation.
     *
     * @return the {@link Item#getFullName()} or nearest approximation.
     */
    public final String getFullName() {
        String n;
        ModelObject context = getStore().getContext();
        if (context instanceof Item) {
            n = ((Item) context).getFullName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullName();
        } else if (context instanceof User) {
            n = "user:" + ((User) context).getId();
        } else {
            n = "";
        }
        if (n.length() == 0) {
            return getUrlName();
        } else {
            return n + '/' + getUrlName();
        }
    }

    /**
     * Returns the {@link Item#getFullDisplayName()} or nearest approximation.
     *
     * @return the {@link Item#getFullDisplayName()} or nearest approximation.
     */
    public final String getFullDisplayName() {
        String n;
        ModelObject context = getStore().getContext();
        if (context instanceof Item) {
            n = ((Item) context).getFullDisplayName();
        } else if (context instanceof ItemGroup) {
            n = ((ItemGroup) context).getFullDisplayName();
        } else if (context instanceof User) {
            n = Messages.CredentialsStoreAction_UserDisplayName(((User) context).getDisplayName());
        } else {
            // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
            n = Jenkins.getActiveInstance().getFullDisplayName();
        }
        if (n.length() == 0) {
            return getDisplayName();
        } else {
            return n + " \u00BB " + getDisplayName();
        }
    }

    /**
     * Returns the map of {@link DomainWrapper} instances.
     *
     * @return the map of {@link DomainWrapper} instances.
     */
    @Exported
    @NonNull
    public Map<String, DomainWrapper> getDomains() {
        Map<String, DomainWrapper> result = new TreeMap<String, DomainWrapper>();
        for (Domain d : getStore().getDomains()) {
            String name;
            if (d.isGlobal()) {
                name = "_";
            } else {
                name = d.getName();
            }
            result.put(name, new DomainWrapper(this, d));
        }
        return result;
    }

    /**
     * Gets the named {@link DomainWrapper}.
     *
     * @param name the name.
     * @return the named {@link DomainWrapper}.
     */
    @CheckForNull
    public DomainWrapper getDomain(String name) {
        return getDomains().get(name);
    }

    /**
     * Exposes {@link CredentialsStore#isDomainsModifiable()} for Jelly.
     *
     * @return {@link CredentialsStore#isDomainsModifiable()}.
     */
    public boolean isDomainsModifiable() {
        return getStore().isDomainsModifiable();
    }

    /**
     * Exposes {@link DomainWrapper.DescriptorImpl} for Jelly.
     *
     * @return {@link DomainWrapper.DescriptorImpl}.
     */
    public DomainWrapper.DescriptorImpl getDomainDescriptor() {
        // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
        return Jenkins.getActiveInstance().getDescriptorByType(DomainWrapper.DescriptorImpl.class);
    }

    /**
     * Gets all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     *
     * @return all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
     */
    @SuppressWarnings("unused") // used by stapler
    public DescriptorExtensionList<DomainSpecification, Descriptor<DomainSpecification>> getSpecificationDescriptors() {
        // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
        return Jenkins.getActiveInstance().getDescriptorList(DomainSpecification.class);
    }

    /**
     * Creates a domain.
     *
     * @param req the request.
     * @return the response.
     * @throws ServletException if something goes wrong.
     * @throws IOException      if something goes wrong.
     */
    @SuppressWarnings("unused") // stapler web method
    @Restricted(NoExternalUse.class)
    @RequirePOST
    public HttpResponse doCreateDomain(StaplerRequest req) throws ServletException, IOException {
        getStore().checkPermission(MANAGE_DOMAINS);
        if (!getStore().isDomainsModifiable()) {
            return HttpResponses.status(HttpServletResponse.SC_BAD_REQUEST);
        }
        String requestContentType = req.getContentType();
        if (requestContentType == null) {
            throw new Failure("No Content-Type header set");
        }

        if (requestContentType.startsWith("application/xml") || requestContentType.startsWith("text/xml")) {
            final StringWriter out = new StringWriter();
            try {
                XMLUtils.safeTransform(new StreamSource(req.getReader()), new StreamResult(out));
                out.close();
            } catch (TransformerException e) {
                throw new IOException("Failed to parse credential", e);
            } catch (SAXException e) {
                throw new IOException("Failed to parse credential", e);
            }

            Domain domain = (Domain)
                    Items.XSTREAM.unmarshal(new XppDriver().createReader(new StringReader(out.toString())));
            if (getStore().addDomain(domain)) {
                return HttpResponses.ok();
            } else {
                return HttpResponses.status(HttpServletResponse.SC_CONFLICT);
            }
        } else {
            JSONObject data = req.getSubmittedForm();
            Domain domain = req.bindJSON(Domain.class, data);
            String domainName = domain.getName();
            if (domainName != null && getStore().addDomain(domain)) {
                return HttpResponses.redirectTo("./domain/" + Util.rawEncode(domainName));

            }
            return HttpResponses.redirectToDot();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public ACL getACL() {
        return getStore().getACL();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {
        getACL().checkPermission(permission);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(@Nonnull Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * A wrapper object to bind and expose {@link Domain} instances into the web UI.
     */
    @ExportedBean
    public static class DomainWrapper extends AbstractDescribableImpl<DomainWrapper> implements
            ModelObjectWithContextMenu, ModelObjectWithChildren {

        /**
         * The {@link CredentialsStoreAction} that we belong to.
         */
        private final CredentialsStoreAction parent;
        /**
         * The {@link Domain} that we are exposing.
         */
        private final Domain domain;

        /**
         * Our constructor.
         *
         * @param parent our parent action.
         * @param domain the domain we are wrapping.
         */
        public DomainWrapper(CredentialsStoreAction parent, Domain domain) {
            this.parent = parent;
            this.domain = domain;
        }

        /**
         * Expose a Jenkins {@link Api}.
         *
         * @return the {@link Api}.
         */
        public Api getApi() {
            return new Api(this);
        }

        /**
         * Expose the backing {@link CredentialsStore}.
         *
         * @return the backing {@link CredentialsStore}.
         */
        public CredentialsStore getStore() {
            return getParent().getStore();
        }

        /**
         * Expose the backing {@link Domain}.
         *
         * @return the backing {@link Domain}.
         */
        public Domain getDomain() {
            return domain;
        }

        /**
         * Expose the parent {@link CredentialsStoreAction}.
         *
         * @return the parent {@link CredentialsStoreAction}.
         */
        public CredentialsStoreAction getParent() {
            return parent;
        }

        /**
         * Return the URL name.
         *
         * @return the URL name.
         */
        @Exported
        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
                            justification = "isGlobal() check implies that domain.getName() is null")
        public String getUrlName() {
            return isGlobal() ? "_" : Util.rawEncode(domain.getName());
        }

        /**
         * Return the display name.
         *
         * @return the display name.
         */
        @Exported
        public String getDisplayName() {
            return isGlobal() ? Messages.CredentialsStoreAction_GlobalDomainDisplayName() : domain.getName();
        }

        /**
         * Return the full name.
         *
         * @return the full name.
         */
        @Exported
        public final String getFullName() {
            String n = getParent().getFullName();
            if (n.length() == 0) {
                return getUrlName();
            } else {
                return n + '/' + getUrlName();
            }
        }

        /**
         * Return the full display name.
         *
         * @return the full display name.
         */
        @Exported
        public final String getFullDisplayName() {
            String n = getParent().getFullDisplayName();
            if (n.length() == 0) {
                return getDisplayName();
            } else {
                return n + " \u00BB " + getDisplayName();
            }
        }

        /**
         * Expose the {@link Domain#getDescription()}.
         *
         * @return the {@link Domain#getDescription()}.
         */
        @Exported
        public String getDescription() {
            return isGlobal() ? Messages.CredentialsStoreAction_GlobalDomainDescription() : domain.getDescription();
        }

        /**
         * Expose a flag to indicate that the wrapped domain is the global domain.
         *
         * @return {@code true} if and only if the wrapped domain is the global domain.
         */
        @Exported
        public boolean isGlobal() {
            return domain == Domain.global();
        }

        /**
         * Expose {@link CredentialsWrapper.DescriptorImpl} to Jelly.
         *
         * @return the {@link CredentialsWrapper.DescriptorImpl} singleton.
         */
        public CredentialsWrapper.DescriptorImpl getCredentialDescriptor() {
            // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
            return Jenkins.getActiveInstance().getDescriptorByType(CredentialsWrapper.DescriptorImpl.class);
        }

        /**
         * Exposes a map of the wrapped credentials.
         *
         * @return a map of the wrapped credentials.
         */
        @NonNull
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

        /**
         * Exposes the wrapped credentials for the XML API.
         *
         * @return the wrapped credentials for the XML API.
         * @since 2.1.0
         */
        @NonNull
        @Exported(name = "credentials", visibility = 1)
        public List<CredentialsWrapper> getCredentialsList() {
            return new ArrayList<CredentialsWrapper>(getCredentials().values());
        }

        /**
         * Get a credential by id.
         *
         * @param id the id.
         * @return the {@link CredentialsWrapper}.
         */
        @CheckForNull
        public CredentialsWrapper getCredential(String id) {
            return getCredentials().get(id);
        }

        /**
         * Creates a credential.
         *
         * @param req the request.
         * @return the response.
         * @throws ServletException if something goes wrong.
         * @throws IOException      if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public HttpResponse doCreateCredentials(StaplerRequest req) throws ServletException, IOException {
            getStore().checkPermission(CREATE);
            String requestContentType = req.getContentType();
            if (requestContentType == null) {
                throw new Failure("No Content-Type header set");
            }

            if (requestContentType.startsWith("application/xml") || requestContentType.startsWith("text/xml")) {
                final StringWriter out = new StringWriter();
                try {
                    XMLUtils.safeTransform(new StreamSource(req.getReader()), new StreamResult(out));
                    out.close();
                } catch (TransformerException e) {
                    throw new IOException("Failed to parse credential", e);
                } catch (SAXException e) {
                    throw new IOException("Failed to parse credential", e);
                }

                Credentials credentials = (Credentials)
                        Items.XSTREAM.unmarshal(new XppDriver().createReader(new StringReader(out.toString())));
                if (getStore().addCredentials(domain, credentials)) {
                    return HttpResponses.ok();
                } else {
                    return HttpResponses.status(HttpServletResponse.SC_CONFLICT);
                }
            } else {
                JSONObject data = req.getSubmittedForm();
                Credentials credentials = req.bindJSON(Credentials.class, data.getJSONObject("credentials"));
                getStore().addCredentials(domain, credentials);
                return HttpResponses.redirectTo("../../domain/" + getUrlName());
            }
        }

        /**
         * Updates the domain configuration.
         *
         * @param req the request.
         * @return the response.
         * @throws ServletException if something goes wrong.
         * @throws IOException      if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public HttpResponse doConfigSubmit(StaplerRequest req) throws ServletException, IOException {
            if (!getStore().isDomainsModifiable()) {
                return HttpResponses.status(400);
            }
            getStore().checkPermission(MANAGE_DOMAINS);
            JSONObject data = req.getSubmittedForm();
            Domain domain = req.bindJSON(Domain.class, data);
            String domainName = domain.getName();
            if (domainName != null && getStore().updateDomain(this.domain, domain)) {
                return HttpResponses.redirectTo("../../domain/" + Util.rawEncode(domainName));

            }
            return HttpResponses.redirectToDot();
        }

        /**
         * Deletes a domain.
         *
         * @param req the request.
         * @return the response.
         * @throws IOException if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
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

        /**
         * Creates the context menu with the supplied prefix to all URLs.
         *
         * @param prefix the prefix to prepend to relative urls.
         * @return the {@link ContextMenu} or {@code null}
         * @since 2.0
         */
        @CheckForNull
        public ContextMenu getContextMenu(String prefix) {
            if (getStore().hasPermission(CREATE) || (getStore().hasPermission(MANAGE_DOMAINS) && !domain.isGlobal())) {
                ContextMenu result = new ContextMenu();
                if (getStore().hasPermission(CREATE)) {
                    result.add(new MenuItem(
                            ContextMenuIconUtils.buildUrl(prefix, "newCredentials"),
                            getMenuItemIconUrlByClassSpec("icon-credentials-new-credential icon-md"),
                            Messages.CredentialsStoreAction_AddCredentialsAction()
                    ));
                }
                if (getStore().hasPermission(MANAGE_DOMAINS) && !domain.isGlobal()) {
                    result.add(new MenuItem(ContextMenuIconUtils.buildUrl(prefix, "configure"),
                            getMenuItemIconUrlByClassSpec("icon-setting icon-md"),
                            Messages.CredentialsStoreAction_ConfigureDomainAction()
                    ));
                    result.add(new MenuItem(ContextMenuIconUtils.buildUrl(prefix, "delete"),
                            getMenuItemIconUrlByClassSpec("icon-edit-delete icon-md"),
                            Messages.CredentialsStoreAction_DeleteDomainAction()
                    ));
                }
                return result.items.isEmpty() ? null : result;
            }
            return null;
        }

        /**
         * Creates the children context menu with the supplied prefix to all URLs.
         *
         * @param prefix the prefix to prepend to relative urls.
         * @return the {@link ContextMenu} or {@code null}
         * @since 2.0
         */
        @CheckForNull
        public ContextMenu getChildrenContextMenu(String prefix) {
            ContextMenu menu = new ContextMenu();
            for (Map.Entry<String, CredentialsWrapper> entry : getCredentials().entrySet()) {
                String p = ContextMenuIconUtils.buildUrl(prefix, "credential", entry.getKey());
                MenuItem item =
                        new MenuItem(p,
                                getMenuItemIconUrlByClassSpec(entry.getValue().getIconClassName() + " icon-md"),
                                entry.getValue().getDisplayName()
                        );
                item.subMenu = entry.getValue().getContextMenu(p);
                menu.add(item);
            }
            return menu.items.isEmpty() ? null : menu;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response)
                throws Exception {
            return getContextMenu("");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ContextMenu doChildrenContextMenu(StaplerRequest request,
                                                 StaplerResponse response) throws Exception {
            return getChildrenContextMenu("");
        }

        /**
         * Accepts {@literal config.xml} submission, as well as serve it.
         *
         * @param req the request
         * @param rsp the response
         * @throws IOException if things go wrong
         * @since 2.1.1
         */
        @WebMethod(name = "config.xml")
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
                throws IOException {
            getStore().checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            if (req.getMethod().equals("GET")) {
                // read
                rsp.setContentType("application/xml");
                Items.XSTREAM2.toXML(domain,
                        new OutputStreamWriter(rsp.getOutputStream(), rsp.getCharacterEncoding()));
                return;
            }
            if (req.getMethod().equals("POST") && getStore().isDomainsModifiable()) {
                // submission
                updateByXml(new StreamSource(req.getReader()));
                return;
            }
            if (req.getMethod().equals("DELETE") && getStore().isDomainsModifiable()) {
                if (getStore().removeDomain(domain)) {
                    return;
                } else {
                    rsp.sendError(HttpServletResponse.SC_CONFLICT);
                    return;
                }
            }

            // huh?
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }

        /**
         * Updates a {@link Credentials} by its XML definition.
         *
         * @param source source of the Item's new definition.
         *               The source should be either a <code>StreamSource</code> or a <code>SAXSource</code>, other
         *               sources may not be handled.
         * @throws IOException if things go wrong.
         * @since 2.1.1
         */
        @Restricted(NoExternalUse.class)
        public void updateByXml(Source source) throws IOException {
            getStore().checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            final StringWriter out = new StringWriter();
            try {
                XMLUtils.safeTransform(source, new StreamResult(out));
                out.close();
            } catch (TransformerException e) {
                throw new IOException("Failed to parse credential", e);
            } catch (SAXException e) {
                throw new IOException("Failed to parse credential", e);
            }

            Domain replacement = (Domain)
                    Items.XSTREAM.unmarshal(new XppDriver().createReader(new StringReader(out.toString())));
            getStore().updateDomain(domain, replacement);
        }

        /**
         * Our Descriptor.
         */
        @Extension
        public static class DescriptorImpl extends Descriptor<DomainWrapper> {

            /**
             * Default constructor.
             */
            public DescriptorImpl() {
                super(DomainWrapper.class);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Domain";
            }

            /**
             * Form validation for creating a new domain / renaming an existing domain.
             *
             * @param wrapper the existing domain or {@code null}
             * @param action  the {@link CredentialsStoreAction} in the request.
             * @param value   the proposed name.
             * @return the {@link FormValidation}
             */
            @SuppressWarnings("unused") // stapler form validation
            @Restricted(NoExternalUse.class)
            public FormValidation doCheckName(@AncestorInPath DomainWrapper wrapper,
                                              @AncestorInPath CredentialsStoreAction action,
                                              @QueryParameter String value) {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.CredentialsStoreAction_EmptyDomainNameMessage());
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

    /**
     * A wrapper object to bind and expose {@link Credentials} instances into the web UI.
     */
    @ExportedBean
    public static class CredentialsWrapper extends AbstractDescribableImpl<CredentialsWrapper>
            implements IconSpec, ModelObjectWithContextMenu {

        /**
         * Our {@link DomainWrapper}.
         */
        private final DomainWrapper domain;

        /**
         * The {@link Credentials} that we are wrapping.
         */
        private final Credentials credentials;

        /**
         * The {@link IdCredentials#getId()} of the {@link Credentials}.
         */
        private final String id;
        private Fingerprint fingerprint;

        /**
         * Constructor.
         *
         * @param domain      the wrapped domain.
         * @param credentials the credentials.
         * @param id          the id.
         */
        public CredentialsWrapper(DomainWrapper domain, Credentials credentials, String id) {
            this.domain = domain;
            this.credentials = credentials;
            this.id = id;
        }

        /**
         * Return the id for the XML API.
         *
         * @return the id.
         * @since 2.1.0
         */
        @Exported
        public String getId() {
            return id;
        }

        /**
         * Return the URL name.
         *
         * @return the URL name.
         */
        public String getUrlName() {
            return Util.rawEncode(id);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return credentials.getDescriptor().getIconClassName();
        }

        /**
         * Expose a Jenkins {@link Api}.
         *
         * @return the {@link Api}.
         */
        public Api getApi() {
            return new Api(this);
        }

        /**
         * Gets the display name of the {@link Credentials}.
         *
         * @return the display name of the {@link Credentials}.
         */
        @Exported
        public String getDisplayName() {
            return CredentialsNameProvider.name(credentials);
        }

        /**
         * Gets the display name of the {@link CredentialsDescriptor}.
         *
         * @return the display name of the {@link CredentialsDescriptor}.
         */
        @Exported
        public String getTypeName() {
            return credentials.getDescriptor().getDisplayName();
        }

        /**
         * Gets the description of the {@link Credentials}.
         *
         * @return the description of the {@link Credentials}.
         */
        @Exported
        public String getDescription() {
            return credentials instanceof StandardCredentials
                    ? ((StandardCredentials) credentials).getDescription()
                    : null;
        }

        /**
         * Gets the full name of the {@link Credentials}.
         *
         * @return the full name of the {@link Credentials}.
         */
        @Exported
        public final String getFullName() {
            String n = getDomain().getFullName();
            if (n.length() == 0) {
                return getUrlName();
            } else {
                return n + '/' + getUrlName();
            }
        }

        /**
         * Gets the full display name of the {@link Credentials}.
         *
         * @return the full display name of the {@link Credentials}.
         */
        public final String getFullDisplayName() {
            String n = getDomain().getFullDisplayName();
            if (n.length() == 0) {
                return getDisplayName();
            } else {
                return n + " \u00BB " + getDisplayName();
            }
        }

        /**
         * Exposes the backing {@link Credentials}.
         *
         * @return the backing {@link Credentials}.
         */
        public Credentials getCredentials() {
            return credentials;
        }

        /**
         * Exposes the backing {@link DomainWrapper}.
         *
         * @return the backing {@link DomainWrapper}.
         */
        public DomainWrapper getDomain() {
            return domain;
        }

        /**
         * Exposes the backing {@link DomainWrapper}.
         *
         * @return the backing {@link DomainWrapper}.
         */
        public DomainWrapper getParent() {
            return domain;
        }

        /**
         * Exposes the backing {@link CredentialsStore}.
         *
         * @return the backing {@link CredentialsStore}.
         */
        public CredentialsStore getStore() {
            return domain.getStore();
        }

        /**
         * Exposes the fingerprint for Jelly pages.
         *
         * @return the {@link Fingerprint}.
         * @throws IOException if the {@link Fingerprint} could not be retrieved.
         * @since 2.1.1
         */
        @Restricted(NoExternalUse.class)
        @Exported(visibility = 1)
        public Fingerprint getFingerprint() throws IOException {
            if (fingerprint == null) {
                // idempotent write
                fingerprint = CredentialsProvider.getFingerprintOf(credentials);
            }
            return fingerprint;
        }

        /**
         * Deletes the credentials.
         *
         * @param req the request.
         * @return the response.
         * @throws IOException if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public HttpResponse doDoDelete(StaplerRequest req) throws IOException {
            getStore().checkPermission(DELETE);
            if (getStore().removeCredentials(domain.getDomain(), credentials)) {
                return HttpResponses.redirectTo("../..");
            }
            return HttpResponses.redirectToDot();
        }

        /**
         * Moves the credential.
         *
         * @param req         the request.
         * @param destination the destination
         * @return the response.
         * @throws IOException if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public HttpResponse doDoMove(StaplerRequest req, @QueryParameter String destination) throws IOException {
            if (getStore().getDomains().size() <= 1) {
                return HttpResponses.status(400);
            }
            // TODO switch to Jenkins.getInstance() once 2.0+ is the baseline
            Jenkins jenkins = Jenkins.getActiveInstance();
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
                context = jenkins;
            } else {
                while (context == null && split > 0) {
                    context = contextName.startsWith("user:")
                            ? User
                            .get(contextName.substring("user:".length(), split - 1), false, Collections.emptyMap())
                            : jenkins.getItemByFullName(contextName);
                    if (context == null) {
                        split = destination.lastIndexOf(splitKey, split - 1);
                        if (split > 0) {
                            contextName = destination.substring(0, split);
                            domainName = destination.substring(split + splitKey.length());
                        }
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
                    if (destinationDomain != null) {
                        break;
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

        /**
         * Updates the credentials.
         *
         * @param req the request.
         * @return the response.
         * @throws ServletException if something goes wrong.
         * @throws IOException      if something goes wrong.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public HttpResponse doUpdateSubmit(StaplerRequest req) throws ServletException, IOException {
            getStore().checkPermission(UPDATE);
            JSONObject data = req.getSubmittedForm();
            Credentials credentials = req.bindJSON(Credentials.class, data);
            if (!getStore().updateCredentials(this.domain.domain, this.credentials, credentials)) {
                return HttpResponses.redirectTo("concurrentModification");
            }
            return HttpResponses.redirectToDot();
        }

        /**
         * Creates the context menu with the supplied prefix to all URLs.
         *
         * @param prefix the prefix to prepend to relative urls.
         * @return the {@link ContextMenu} or {@code null}
         * @since 2.0
         */
        @CheckForNull
        @Restricted(NoExternalUse.class)
        public ContextMenu getContextMenu(String prefix) {
            if (getStore().hasPermission(UPDATE) || getStore().hasPermission(DELETE)) {
                ContextMenu result = new ContextMenu();
                if (getStore().hasPermission(UPDATE)) {
                    result.add(new MenuItem(
                            ContextMenuIconUtils.buildUrl(prefix, "update"),
                            getMenuItemIconUrlByClassSpec("icon-setting icon-md"),
                            Messages.CredentialsStoreAction_UpdateCredentialAction()
                    ));
                }
                if (getStore().hasPermission(DELETE)) {
                    result.add(new MenuItem(ContextMenuIconUtils.buildUrl(prefix, "delete"),
                            getMenuItemIconUrlByClassSpec("icon-edit-delete icon-md"),
                            Messages.CredentialsStoreAction_DeleteCredentialAction()
                    ));
                    result.add(new MenuItem(ContextMenuIconUtils.buildUrl(prefix, "move"),
                            getMenuItemIconUrlByClassSpec("icon-credentials-move icon-md"),
                            Messages.CredentialsStoreAction_MoveCredentialAction()
                    ));
                }
                return result.items.isEmpty() ? null : result;
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response)
                throws Exception {
            return getContextMenu("");
        }

        /**
         * Accepts {@literal config.xml} submission, as well as serve it.
         *
         * @param req the request
         * @param rsp the response
         * @throws IOException if things go wrong
         * @since 2.1.1
         */
        @WebMethod(name = "config.xml")
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler web method
        public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
                throws IOException {
            if (req.getMethod().equals("GET")) {
                // read
                getStore().checkPermission(VIEW);
                rsp.setContentType("application/xml");
                SECRETS_REDACTED.toXML(credentials,
                        new OutputStreamWriter(rsp.getOutputStream(), rsp.getCharacterEncoding()));
                return;
            }
            if (req.getMethod().equals("POST")) {
                // submission
                updateByXml(new StreamSource(req.getReader()));
                return;
            }
            if (req.getMethod().equals("DELETE")) {
                getStore().checkPermission(DELETE);
                if (getStore().removeCredentials(domain.getDomain(), credentials)) {
                    return;
                } else {
                    rsp.sendError(HttpServletResponse.SC_CONFLICT);
                    return;
                }
            }

            // huh?
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }

        /**
         * Updates a {@link Credentials} by its XML definition.
         *
         * @param source source of the Item's new definition.
         *               The source should be either a <code>StreamSource</code> or a <code>SAXSource</code>, other
         *               sources may not be handled.
         * @throws IOException if things go wrong
         * @since 2.1.1
         */
        @Restricted(NoExternalUse.class)
        public void updateByXml(Source source) throws IOException {
            getStore().checkPermission(UPDATE);
            final StringWriter out = new StringWriter();
            try {
                XMLUtils.safeTransform(source, new StreamResult(out));
                out.close();
            } catch (TransformerException e) {
                throw new IOException("Failed to parse credential", e);
            } catch (SAXException e) {
                throw new IOException("Failed to parse credential", e);
            }

            Credentials credentials = (Credentials)
                    Items.XSTREAM.unmarshal(new XppDriver().createReader(new StringReader(out.toString())));
            getStore().updateCredentials(domain.getDomain(), this.credentials, credentials);
        }

        /**
         * Our {@link Descriptor}.
         */
        @Extension
        public static class DescriptorImpl extends Descriptor<CredentialsWrapper> {

            /**
             * Exposes {@link CredentialsProvider#allCredentialsDescriptors()} to Jelly
             *
             * @return {@link CredentialsProvider#allCredentialsDescriptors()}
             */
            @Restricted(NoExternalUse.class)
            public DescriptorExtensionList<Credentials, CredentialsDescriptor> getCredentialDescriptors() {
                // TODO delete me
                return CredentialsProvider.allCredentialsDescriptors();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Credential";
            }
        }
    }
}
