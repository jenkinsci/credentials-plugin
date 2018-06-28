package com.cloudbees.plugins.credentials.cli;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.Extension;
import org.kohsuke.args4j.Argument;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

@Extension
public class ListCredentialsAsXmlCommand extends BaseCredentialsCLICommand {

    @Argument(metaVar = "STORE", usage = "Store ID to export credentials from. For example the Jenkins credentials global store would " +
            "be identified by \"system::system::jenkins\", and folder scoped credentials as \"folder::item::/full/name/of/folder\"",
            required = true)
    private CredentialsStore store;


    @Override
    public String getShortDescription() {
        return "Export credentials as XML. The output of this command can be used as input for \"import-credentials-as-xml\" as is, the only needed change is to set the actual Secrets which are redacted in the output.";
    }

    @Override
    protected int run() throws Exception {
        store.checkPermission(CredentialsProvider.UPDATE);

        List<DomainCredentials> existing = new ArrayList<>();
        List<Domain> domains = store.getDomains();
        for (Domain domain : domains) {
            existing.add(new DomainCredentials(domain, store.getCredentials(domain)));
        }
        CredentialsStoreAction.SECRETS_REDACTED.toXML(existing, new OutputStreamWriter(stdout, "UTF-8"));
        return 0;
    }

}
