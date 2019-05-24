package com.cloudbees.plugins.credentials.util;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * TODO This class is a clone of {@link jenkins.util.xml.RestrictiveEntityResolver} because the last is Restricted.
 * It's expected that the weekly release 2.179 unrestrict this class. More info: https://github.com/jenkinsci/jenkins/pull/4032
 */

@Restricted(NoExternalUse.class)
public final class RestrictiveEntityResolver implements EntityResolver {

    public final static RestrictiveEntityResolver INSTANCE = new RestrictiveEntityResolver();

    private RestrictiveEntityResolver() {
        // prevent multiple instantiation.
        super();
    }

    /**
     * Throws a SAXException if this tried to resolve any entity.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        throw new SAXException("Refusing to resolve entity with publicId(" + publicId + ") and systemId (" + systemId + ")");
    }
}
