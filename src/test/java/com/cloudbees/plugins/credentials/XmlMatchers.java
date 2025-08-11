package com.cloudbees.plugins.credentials;

import org.xmlunit.matchers.CompareMatcher;

public class XmlMatchers {

    public static CompareMatcher isSimilarToIgnoringPrivateAttrs(String control) {
        return CompareMatcher.isSimilarTo(control)
                .normalizeWhitespace()
                .ignoreComments()
                .withAttributeFilter(attr -> !attr.getName().startsWith("_"));
    }
}
