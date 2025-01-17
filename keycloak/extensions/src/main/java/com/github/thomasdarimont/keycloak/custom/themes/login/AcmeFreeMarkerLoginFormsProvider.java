package com.github.thomasdarimont.keycloak.custom.themes.login;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.AuthenticationContextBean;
import org.keycloak.forms.login.freemarker.model.ClientBean;
import org.keycloak.models.KeycloakSession;
import org.keycloak.theme.FreeMarkerUtil;
import org.keycloak.theme.Theme;

import javax.ws.rs.core.Response;
import java.util.Locale;

/**
 * Custom {@link FreeMarkerLoginFormsProvider} to adjust the login form rendering context.
 */
@JBossLog
public class AcmeFreeMarkerLoginFormsProvider extends FreeMarkerLoginFormsProvider {

    public AcmeFreeMarkerLoginFormsProvider(KeycloakSession session, FreeMarkerUtil freeMarker) {
        super(session, freeMarker);
    }

    @Override
    protected Response processTemplate(Theme theme, String templateName, Locale locale) {
        // expose custom objects in the template rendering via super.attributes

        var authBean = (AuthenticationContextBean) attributes.get("auth");
        attributes.put("acmeLogin", new AcmeLoginBean(session, authBean));

        var clientBean = (ClientBean) attributes.get("client");
        attributes.put("acmeUrl", new AcmeUrlBean(clientBean));

        // TODO remove hack for custom profile fields
        if (attributes.containsKey("customProfile")) {
            attributes.put("profile", attributes.get("customProfile"));
        }

        return super.processTemplate(theme, templateName, locale);
    }
}
