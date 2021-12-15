package com.cloudbees.plugins.credentials;

import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CredentialsProviderTypeRestrictionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void migrateDescriptorClassNameToID() {
        CredentialsProviderManager instance = CredentialsProviderManager.getInstance();
        assertThat(instance, notNullValue());

        List<CredentialsProviderTypeRestriction> restrictions = instance.getRestrictions();
        assertThat(restrictions, hasSize(2));

        CredentialsProviderTypeRestriction excludesRestriction = restrictions.get(0);
        assertThat(excludesRestriction, instanceOf(CredentialsProviderTypeRestriction.Excludes.class));

        CredentialsProviderTypeRestriction.Excludes excludes = (CredentialsProviderTypeRestriction.Excludes) excludesRestriction;
        assertThat(excludes.getType(), is("com.cloudbees.plugins.credentials.impl.DummyCredentials"));

        CredentialsProviderTypeRestriction includesRestriction = restrictions.get(1);
        assertThat(includesRestriction, instanceOf(CredentialsProviderTypeRestriction.Includes.class));

        CredentialsProviderTypeRestriction.Includes includes = (CredentialsProviderTypeRestriction.Includes) includesRestriction;
        assertThat(includes.getType(), is("com.cloudbees.plugins.credentials.impl.DummyIdCredentials"));

        CredentialsTypeFilter typeFilter = instance.getTypeFilter();
        assertTrue(typeFilter.filter(ExtensionList.lookupSingleton(UsernamePasswordCredentialsImpl.DescriptorImpl.class)));
        assertFalse(typeFilter.filter(ExtensionList.lookupSingleton(CertificateCredentialsImpl.DescriptorImpl.class)));
    }

}