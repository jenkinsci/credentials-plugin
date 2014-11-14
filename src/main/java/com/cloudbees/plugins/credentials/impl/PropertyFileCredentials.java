/*
 * Copyright 2014 Oleg Nenashev <o.v.nenashev@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudbees.plugins.credentials.impl;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.util.Secret;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Loads {@link Credentials} from a property file.
 * {@link #propertyPath} supports both local paths and URLs.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 * @since TODO: define a version
 */
public class PropertyFileCredentials extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {
    
    public final @CheckForNull String propertyPath;
    public final @CheckForNull String usernameKey;
    public final @CheckForNull String passwordKey;

    @DataBoundConstructor
    public PropertyFileCredentials(String propertyPath, String usernameKey, String passwordKey, CredentialsScope scope, String id, String description) {
        super(scope, id, description);
        this.propertyPath = propertyPath;
        this.usernameKey = usernameKey;
        this.passwordKey = passwordKey;
    }

    public @CheckForNull String getPasswordKey() {
        return passwordKey;
    }

    public @CheckForNull String getPropertyPath() {
        return propertyPath;
    }

    public @CheckForNull String getUsernameKey() {
        return usernameKey;
    }

    public String getUsername() {
        return getProperty(usernameKey);
    }

    public Secret getPassword() {
        final String passwordString = getProperty(passwordKey);
        return passwordString != null ? Secret.fromString(passwordString) : null;  
    }

    private @CheckForNull String getProperty(@CheckForNull String propertyKey) {
        if(StringUtils.isBlank(propertyKey)) {
            return null;
        }
        if(StringUtils.isBlank(propertyPath)) {
            return null;
        }

        final Properties props = new Properties();
        try {
            File propFilePath = new File(propertyPath);
            if (propFilePath.exists()) { // local file
                InputStream input = new FileInputStream(propFilePath);
                try {
                    props.load(input);
                } finally {
                    input.close();
                }
            } else { // remote file
                URL propertyFileUrl = new URL(propertyPath);
                InputStream input = propertyFileUrl.openStream();
                try {
                    props.load(input);
                } finally {
                    input.close();
                }
            }
        } catch (Throwable t) {
            return null;
        }
        
        return props.getProperty(propertyKey);
    }
    
  /*  @Override
    public <C extends Credentials> List<C> getCredentials(Class<C> type, ItemGroup itemGroup, Authentication authentication) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    */
    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Property file credentials";
        }
    }
}
