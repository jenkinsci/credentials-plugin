package com.cloudbees.plugins.credentials;

import hudson.model.ModelObject;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.servlet.ServletException;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AnnotationHandler;
import org.kohsuke.stapler.InjectedParameter;
import org.kohsuke.stapler.StaplerRequest2;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that this parameter is injected by evaluating
 * {@link StaplerRequest2#getAncestors()} and searching for a credentials context with the parameter type.
 * You can enhance the lookup by ensuring that there are query parameters of {@code $provider} and {@code $token}
 * that correspond to the context's {@link CredentialsSelectHelper.ContextResolver} FQCN and
 * {@link CredentialsSelectHelper.ContextResolver#getToken(ModelObject)} respectively.
 *
 * @see CredentialsDescriptor#getCheckMethod(String)
 * @since 2.1.5
 */
@Retention(RUNTIME)
@Target(PARAMETER)
@Documented
@InjectedParameter(ContextInPath.HandlerImpl.class)
public @interface ContextInPath {
    class HandlerImpl extends AnnotationHandler<ContextInPath> {
        @Override
        public Object parse(StaplerRequest2 request, ContextInPath contextInPath, Class type, String parameterName)
                throws
                ServletException {
            String $provider = request.getParameter("$provider");
            String $token = request.getParameter("$token");
            if (StringUtils.isNotBlank($provider) && StringUtils.isNotBlank($token)) {
                ModelObject context = CredentialsDescriptor.lookupContext($provider, $token);
                if (type.isInstance(context)) {
                    return type.cast(context);
                }
            }
            return CredentialsDescriptor.findContextInPath(request, type);
        }
    }
}
