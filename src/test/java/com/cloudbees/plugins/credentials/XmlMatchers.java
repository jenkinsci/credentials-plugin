package com.cloudbees.plugins.credentials;

import org.w3c.dom.Attr;
import org.xmlunit.matchers.CompareMatcher;
import org.xmlunit.util.Predicate;

public class XmlMatchers {
    public static CompareMatcher isSimilarToIgnoringPrivateAttrs(String control) {
        return CompareMatcher.isSimilarTo(control)
                .normalizeWhitespace()
                .ignoreComments()
                .withAttributeFilter(new Predicate<Attr>() {
                    @Override
                    public boolean test(Attr attr) {
                        if (attr.getName().startsWith("_")) {
                            return false;
                        }
                        return true;
                    }
                });
    }
}
