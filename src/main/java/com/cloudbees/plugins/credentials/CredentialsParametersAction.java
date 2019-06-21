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

/**
 * Contains a collection of {@link CredentialsParameterValue} values provided to a {@link Run}.
 * When attached to a run, this action will import any CredentialsParameterValues contained in the run's optionally
 * present {@link ParametersAction}.
 *
 * @since TODO
 */
public class CredentialsParametersAction extends InvisibleAction implements RunAction2, Iterable<CredentialsParameterValue> {

    private final Map<String, CredentialsParameterValue> values;

    public CredentialsParametersAction() {
        this(new HashMap<>());
    }

    private CredentialsParametersAction(Map<String, CredentialsParameterValue> values) {
        this.values = values;
    }

    /**
     * Looks up an existing CredentialsParameterAction for a Run or adapts and attaches an existing ParametersAction to
     * a new CredentialsParametersAction. Returns {@code null} if the run has no parameters.
     */
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

    /**
     * Adds a CredentialsParameterValue to this action if it does not already exist.
     */
    public void add(@Nonnull CredentialsParameterValue value) {
        values.put(value.getName(), value);
    }

    /**
     * Adds CredentialsParameterValues from a collection of ParameterValues. Shortcut for filtering and adding items
     * via {@link #add(CredentialsParameterValue)}.
     */
    public void addFromParameterValues(@Nonnull Collection<ParameterValue> values) {
        values.stream()
                .filter(CredentialsParameterValue.class::isInstance)
                .map(CredentialsParameterValue.class::cast)
                .forEach(this::add);
    }

    @CheckForNull CredentialsParameterValue findParameterByName(@Nonnull String name) {
        return values.get(name);
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        final ParametersAction action = r.getAction(ParametersAction.class);
        if (action != null) {
            addFromParameterValues(action.getParameters());
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
