package com.cloudbees.plugins.credentials;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractModelObject;
import hudson.model.Item;
import hudson.model.Run;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Listener to track {@link Credentials } usage.
 */
public abstract class CredentialsUseListener implements ExtensionPoint {

    /**
     * Stop listening to {@link CredentialsUseListener}.
     */
    public void unregister() {
        all().remove(this);
    }

    /**
     * Called when {@link Credentials} is used by an object, usually either {@link Run} or {@link hudson.model.Node}
     *
     * @param c   The used Credentials.
     * @param obj The object using the credentials. Object is usually either {@link Run} or {@link hudson.model.Node}
     */
    public abstract void onUse(Credentials c, AbstractModelObject obj);

    /**
     * Called when {@link Credentials} is used by an item .
     *
     * @param c    The used Credentials.
     * @param item The object using the credentials.
     */
    public abstract void onUse(Credentials c, Item item);

    /**
     * Fires the {@link #onUse} event to track the object that uses credentials.
     */
    public static void fireUse(Credentials c, AbstractModelObject obj) {
        for (CredentialsUseListener listener : all()) {
            try {
                listener.onUse(c, obj);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                Logger.getLogger(CredentialsUseListener.class.getName()).log(Level.WARNING, null, t);
            }
        }
    }

    /**
     * Fires the {@link #onUse} event to track the item that uses credentials.
     */
    public static void fireUse(Credentials c, Item item) {
        for (CredentialsUseListener listener : all()) {
            try {
                listener.onUse(c, item);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                Logger.getLogger(CredentialsUseListener.class.getName()).log(Level.WARNING, null, t);
            }
        }
    }

    /**
     * Returns all the registered {@link CredentialsUseListener} descriptors.
     */
    public static ExtensionList<CredentialsUseListener> all() {
        return ExtensionList.lookup(CredentialsUseListener.class);
    }

}
