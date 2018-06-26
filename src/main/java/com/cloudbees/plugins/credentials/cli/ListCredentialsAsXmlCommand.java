package com.cloudbees.plugins.credentials.cli;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.cli.BaseCredentialsCLICommand;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.Items;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

@Extension
public class ListCredentialsAsXmlCommand extends BaseCredentialsCLICommand {

    @Argument(metaVar = "STORE", usage = "Store ID. For example the Jenkins credentials global store would " +
            "be identified by \"system::system::jenkins\"",
            required = true)
    private CredentialsStore store;

    @Option(name = "--import", usage = "Read credentials as XML from the standard input and create them in the given store.")
    private boolean doImport;


    @Override
    public String getShortDescription() {
        return "Import and Export credentials as XML. The output of the command can be used as input (for --import) as is, the only needed change is to set the actual Secrets which are redacted in the output.";
    }

    @Override
    protected int run() throws Exception {
        if (doImport) {
            store.checkPermission(CredentialsProvider.CREATE);
            store.checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            List<DomainCredentials> replacement = (List<DomainCredentials>) Items.XSTREAM.unmarshal(safeXmlStreamReader(stdin));
            for (DomainCredentials domain : replacement) {
                for (Credentials credentials : domain.getCredentials()) {
                    store.addDomain(domain.getDomain(), credentials);
                }
            }
            return 0;
        } else {
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

}
