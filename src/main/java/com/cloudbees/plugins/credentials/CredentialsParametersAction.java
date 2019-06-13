package com.cloudbees.plugins.credentials;

import hudson.model.InvisibleAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CredentialsParametersAction extends InvisibleAction implements RunAction2, Iterable<CredentialsParameterValue> {

    private final Map<String, CredentialsParameterValue> values;

    public CredentialsParametersAction() {
        this(new HashMap<>());
    }

    private CredentialsParametersAction(Map<String, CredentialsParameterValue> values) {
        this.values = values;
    }

    static @CheckForNull CredentialsParametersAction forRun(@Nonnull Run<?, ?> run) {
        final CredentialsParametersAction existingAction = run.getAction(CredentialsParametersAction.class);
        if (existingAction != null) {
            return existingAction;
        }
        if (run.getAction(ParametersAction.class) == null) {
            return null;
        }
        run.addAction(new CredentialsParametersAction());
        // onAttached copies existing ParametersAction
        return run.getAction(CredentialsParametersAction.class);
    }

    public void add(@Nonnull CredentialsParameterValue value) {
        values.put(value.getName(), value);
    }

    public void addFromParameterValues(@Nonnull Collection<ParameterValue> values) {
        values.stream()
                .filter(CredentialsParameterValue.class::isInstance)
                .map(CredentialsParameterValue.class::cast)
                .forEach(this::add);
    }

    @CheckForNull CredentialsParameterValue findParameterByName(@Nonnull String name) {
        return values.get(name);
    }

    @CheckForNull CredentialsParameterValue findParameterByValue(@Nonnull String value) {
        return values.values().stream().filter(v -> value.equals(v.getValue())).findFirst().orElse(null);
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        final ParametersAction action = r.getAction(ParametersAction.class);
        if (action != null) {
            for (ParameterValue value : action.getParameters()) {
                if (value instanceof CredentialsParameterValue) {
                    add((CredentialsParameterValue) value);
                }
            }
        }
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        // this space intentionally left blank
    }

    @Override
    public @Nonnull Iterator<CredentialsParameterValue> iterator() {
        return values.values().iterator();
    }
}
