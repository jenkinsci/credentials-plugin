package com.cloudbees.plugins.credentials.cli;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.Extension;
import hudson.model.Items;
import org.kohsuke.args4j.Argument;

import java.util.List;

@Extension
public class ImportCredentialsAsXmlCommand extends BaseCredentialsCLICommand {

    @Argument(metaVar = "STORE", usage = "Store ID to import credentials to. For example the Jenkins credentials global store would " +
            "be identified by \"system::system::jenkins\", and folder scoped credentials as \"folder::item::/full/name/of/folder\"",
            required = true)
    private CredentialsStore store;

    @Override
    public String getShortDescription() {
        return "Import credentials as XML. The output of \"list-credentials-as-xml\" can be used as input here as is, the only needed change is to set the actual Secrets which are redacted in the output.";
    }

    @Override
    protected int run() throws Exception {
        store.checkPermission(CredentialsProvider.CREATE);
        store.checkPermission(CredentialsProvider.MANAGE_DOMAINS);
        List<DomainCredentials> replacement = (List<DomainCredentials>) Items.XSTREAM.unmarshal(safeXmlStreamReader(stdin));
        for (DomainCredentials domain : replacement) {
            for (Credentials credentials : domain.getCredentials()) {
                store.addDomain(domain.getDomain(), credentials);
            }
        }
        return 0;
    }

}
