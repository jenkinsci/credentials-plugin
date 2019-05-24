package com.cloudbees.plugins.credentials.util;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.annotation.Nonnull;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * TODO This class is a clone of {@link jenkins.util.xml.XMLUtils} because the last is Restricted.
 * It's expected that the weekly release 2.179 unrestrict this class. More info: https://github.com/jenkinsci/jenkins/pull/4032
 */
@Restricted(NoExternalUse.class)
public class XMLUtils {
    private final static Logger LOGGER = LogManager.getLogManager().getLogger(XMLUtils.class.getName());

    private static final String FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private final static String DISABLED_PROPERTY_NAME = "jenkins.util.xml.XMLUtils.disableXXEPrevention";

    public static void safeTransform(@Nonnull Source source, @Nonnull Result out) throws TransformerException,
            SAXException {

        InputSource src = SAXSource.sourceToInputSource(source);
        if (src != null) {
            SAXTransformerFactory stFactory = (SAXTransformerFactory) TransformerFactory.newInstance();
            stFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
            setFeatureQuietly(xmlReader, FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_GENERAL_ENTITIES, false);
            setFeatureQuietly(xmlReader, FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_PARAMETER_ENTITIES, false);

            // defend against XXE
            // the above features should strip out entities - however the feature may not be supported depending
            // on the xml implementation used and this is out of our control.
            // So add a fallback plan if all else fails.
            xmlReader.setEntityResolver(RestrictiveEntityResolver.INSTANCE);
            SAXSource saxSource = new SAXSource(xmlReader, src);
            _transform(saxSource, out);
        }
        else {
            // for some reason we could not convert source
            // this applies to DOMSource and StAXSource - and possibly 3rd party implementations...
            // a DOMSource can already be compromised as it is parsed by the time it gets to us.
            if (Boolean.getBoolean(DISABLED_PROPERTY_NAME)) {
                LOGGER.log(Level.WARNING,  "XML external entity (XXE) prevention has been disabled by the system " +
                        "property {0}=true Your system may be vulnerable to XXE attacks.", DISABLED_PROPERTY_NAME);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Caller stack trace: ", new Exception("XXE Prevention caller history"));
                }
                _transform(source, out);
            }
            else {
                throw new TransformerException("Could not convert source of type " + source.getClass() + " and " +
                        "XXEPrevention is enabled.");
            }
        }
    }

    private static void _transform(Source source, Result out) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // this allows us to use UTF-8 for storing data,
        // plus it checks any well-formedness issue in the submitted data.
        Transformer t = factory.newTransformer();
        t.transform(source, out);
    }

    private static void setFeatureQuietly(XMLReader reader, String feature, boolean value) {
        try {
            reader.setFeature(feature, value);
        }
        catch (SAXException ignored) {
            // ignore and continue in case the feature cannot be changed
        }
    }
}
