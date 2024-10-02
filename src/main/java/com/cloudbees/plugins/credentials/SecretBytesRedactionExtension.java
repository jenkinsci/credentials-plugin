package com.cloudbees.plugins.credentials;

import hudson.ExtensionList;
import hudson.init.Initializer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.ExtendedReadRedaction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class SecretBytesRedactionExtension {

    public static final Logger LOGGER = Logger.getLogger(SecretBytesRedactionExtension.class.getName());

    // TODO Delete this and annotate `SecretBytesRedaction` with `@Extension` once the core dependency is >= 2.479
    @Initializer
    public static void create() {
        try {
            ExtensionList.lookup(ExtendedReadRedaction.class).add(new SecretBytesRedaction());
        } catch (NoClassDefFoundError unused) {
            LOGGER.log(Level.WARNING, "Failed to register SecretBytesRedaction. Update Jenkins to add support for redacting credentials in config.xml files from users with ExtendedRead permission. Learn more: https://www.jenkins.io/redirect/plugin/credentials/SecretBytesRedaction/");
        }
    }
}
