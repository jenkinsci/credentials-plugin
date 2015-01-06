package com.cloudbees.plugins.credentials.matchers;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.logging.Logger;

/**
 * Matches credentials based on class and interface names. Type names
 * will match a credential if:
 *
 * <ul>
 *     <li>The type name is exactly equal to the fully qualified name of the Credential class,
 *     any implemented interfaces or superclasses</li>
 *     <li>The type name is equal to the {@code simpleName} of the Credential's class, any of its
 *     implemented interfaces or any of its super classes</li>
 * </ul>
 *
 * For example, the type name is 'SshUserPrivateKey' will match {@code BasicSSHUserPrivateKey}
 * because {@code BasicSSHUserPrivateKey} implements {@code SSHUserPrivateKey}
 */
public class TypeNameMatcher implements CredentialsMatcher {

    private static final Logger LOGGER = Logger.getLogger(TypeNameMatcher.class.getName());

    private String typeName;


    public TypeNameMatcher(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public boolean matches(@NonNull Credentials item) {

        if(matches(item.getClass())) {
            return true;
        }

        for(Class i : item.getClass().getInterfaces()) {
            if(matches(i)) {
                return true;
            }
        }

        Class superClass = item.getClass().getSuperclass();
        while(!superClass.equals(Object.class)) {
            if(matches(superClass)) {
                return true;
            }
            superClass = superClass.getSuperclass();
        }

        return false;
    }

    private boolean matches(Class clazz) {

        LOGGER.info(String.format("Matching '%s' against '%s'", typeName, clazz.getName()));

        return clazz.getName().equals(typeName) ||
                clazz.getSimpleName().equalsIgnoreCase(typeName) ||
                clazz.getSimpleName().equalsIgnoreCase(typeName + "Credentials");
    }

    @Override
    public String toString() {
        return "TypeMatcher{typeName=" + typeName + "}";
    }
}
