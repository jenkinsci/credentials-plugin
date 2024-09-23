package com.cloudbees.plugins.credentials;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.security.ExtendedReadRedaction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
// @Extension
// See SecretBytesReactionExtension
public class SecretBytesRedaction implements ExtendedReadRedaction {
    private static final Pattern SECRET_BYTES_PATTERN = Pattern.compile(">(" + SecretBytes.ENCRYPTED_VALUE_PATTERN + ")<");

    @Override
    public String apply(String configDotXml) {
        Matcher matcher = SECRET_BYTES_PATTERN.matcher(configDotXml);
        StringBuilder cleanXml = new StringBuilder();
        while (matcher.find()) {
            if (SecretBytes.isSecretBytes(matcher.group(1))) {
                matcher.appendReplacement(cleanXml, ">********<");
            }
        }
        matcher.appendTail(cleanXml);
        return cleanXml.toString();
    }
}
