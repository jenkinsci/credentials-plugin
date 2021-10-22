package com.cloudbees.plugins.credentials.casc;

import com.cloudbees.plugins.credentials.GlobalCredentialsConfiguration;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.impl.configurators.GlobalConfigurationCategoryConfigurator;
import io.jenkins.plugins.casc.model.Mapping;

import java.util.Objects;

public class ConfigurationAsCodeCategoryRoot {

    public static Mapping getConfiguration(ConfigurationContext context) throws Exception {
        GlobalCredentialsConfiguration.Category category = ExtensionList.lookupSingleton(GlobalCredentialsConfiguration.Category.class);
        GlobalConfigurationCategoryConfigurator configurator = new GlobalConfigurationCategoryConfigurator(category);
        return Objects.requireNonNull(configurator.describe(configurator.getTargetComponent(context), context)).asMapping();
    }
}
