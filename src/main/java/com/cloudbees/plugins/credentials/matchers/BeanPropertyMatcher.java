package com.cloudbees.plugins.credentials.matchers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * Matches credentials that have a Java Bean property with an expected value.
 *
 * @since 2.0.8
 */
public class BeanPropertyMatcher<T extends Serializable> implements CredentialsMatcher, CredentialsMatcher.CQL {
    /**
     * Standardize serialization.
     */
    private static final long serialVersionUID = 1L;
    /**
     * The property name.
     */
    @NonNull
    private final String name;
    /**
     * The expected value.
     */
    @CheckForNull
    private final T expected;

    /**
     * Constructs an instance that matches the specified java bean property against the supplied value.
     *
     * @param name     the property name.
     * @param expected the expected value.
     */
    public BeanPropertyMatcher(@NonNull String name, @CheckForNull T expected) {
        this.name = name;
        this.expected = expected;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String describe() {
        if (expected == null) {
            return String.format("(c.%s == null)", name);
        }
        if (expected instanceof String) {
            return String.format("(c.%s == \"%s\")", name, StringEscapeUtils.escapeJava((String) expected));
        }
        if (expected instanceof Number) {
            return String.format("(c.%s == %s)", name, expected);
        }
        if (expected instanceof Boolean) {
            return (Boolean) expected ? String.format("c.%s") : String.format("!c.%s");
        }
        if (expected instanceof Enum) {
            return String.format("(c.%s == %s.%s", name, expected.getClass().getName(), ((Enum) expected).name());
        }
        return null; // we cannot describe the expected value in CQL
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull Credentials item) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(item.getClass());
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                if (name.equals(pd.getName())) {
                    Method readMethod = pd.getReadMethod();
                    if (readMethod == null) {
                        return false; // we cannot read it therefore it cannot be a match
                    }
                    try {
                        Object actual = readMethod.invoke(item);
                        return expected == null ? actual == null : expected.equals(actual);
                    } catch (IllegalAccessException e) {
                        return false; // if we cannot access it then it's not a match
                    } catch (InvocationTargetException e) {
                        return false; // if we cannot access it then it's not a match
                    }
                }
            }
            return false; // if there is no corresponding property then it cannot be a match
        } catch (IntrospectionException e) {
            return false; // if we cannot introspect it then it cannot be a match
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (expected != null ? expected.hashCode() : 0);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BeanPropertyMatcher<?> that = (BeanPropertyMatcher<?>) o;

        if (!name.equals(that.name)) {
            return false;
        }
        return expected != null ? expected.equals(that.expected) : that.expected == null;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BeanPropertyMatcher{");
        sb.append("name='").append(name).append('\'');
        sb.append(", expected=").append(expected);
        sb.append('}');
        return sb.toString();
    }
}
