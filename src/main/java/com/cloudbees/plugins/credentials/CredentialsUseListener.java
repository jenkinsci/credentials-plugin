package com.cloudbees.plugins.credentials;

import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import jenkins.util.Listeners;


/**
 * A Listener to track {@link Credentials } usage.
 */
public abstract class CredentialsUseListener implements ExtensionPoint {

    /**
     * Called when {@link Credentials} is read by an object.
     *
     * @param c   The used Credentials.
     * @param obj The object using the credentials.
     */
    public abstract void onUse(Credentials c, ModelObject obj);

    /**
     * Fires the {@link #onUse} event to track the object that uses credentials.
     */
    public static void fireUse(Credentials c, ModelObject obj) {
        Listeners.notify(CredentialsUseListener.class, true, listener -> {
            listener.onUse(c, obj);
        });
    }
}
