package com.github.thomasdarimont.keycloak.custom.support;

import org.keycloak.common.ClientConnection;
import org.keycloak.common.constants.ServiceAccountConstants;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.function.Consumer;

public class TokenUtils {

    /**
     * Generates a service account access token for the given clientId.
     *
     * @param session
     * @param clientId
     * @param scope
     * @param tokenAdjuster
     * @return
     */
    public static String generateServiceAccountAccessToken(KeycloakSession session, String clientId, String scope, Consumer<AccessToken> tokenAdjuster) {

        KeycloakContext context = session.getContext();
        RealmModel realm = context.getRealm();
        ClientModel client = session.clients().getClientByClientId(realm, clientId);

        if (client == null) {
            throw new IllegalStateException("client not found");
        }

        if (!client.isServiceAccountsEnabled()) {
            throw new IllegalStateException("service account not enabled");
        }

        UserModel clientUser = session.users().getServiceAccount(client);
        String clientUsername = clientUser.getUsername();

        RootAuthenticationSessionModel rootAuthSession = new AuthenticationSessionManager(session).createAuthenticationSession(realm, false);
        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);

        authSession.setAuthenticatedUser(clientUser);
        authSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        authSession.setClientNote(OIDCLoginProtocol.ISSUER, Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));
        authSession.setClientNote(OIDCLoginProtocol.SCOPE_PARAM, scope);

        ClientConnection clientConnection = context.getConnection();
        UserSessionModel userSession = session.sessions().createUserSession(authSession.getParentSession().getId(), realm, clientUser, clientUsername, clientConnection.getRemoteAddr(), ServiceAccountConstants.CLIENT_AUTH, false, null, null, UserSessionModel.SessionPersistenceState.TRANSIENT);

        AuthenticationManager.setClientScopesInSession(authSession);
        ClientSessionContext clientSessionCtx = TokenManager.attachAuthenticationSession(session, userSession, authSession);

        // Notes about client details
        userSession.setNote(ServiceAccountConstants.CLIENT_ID, client.getClientId());
        userSession.setNote(ServiceAccountConstants.CLIENT_HOST, clientConnection.getRemoteHost());
        userSession.setNote(ServiceAccountConstants.CLIENT_ADDRESS, clientConnection.getRemoteAddr());

        TokenManager tokenManager = new TokenManager();
        EventBuilder event = new EventBuilder(realm, session, clientConnection);
        TokenManager.AccessTokenResponseBuilder responseBuilder = tokenManager.responseBuilder(realm, client, event, session, userSession, clientSessionCtx).generateAccessToken();

        if (tokenAdjuster != null) {
            tokenAdjuster.accept(responseBuilder.getAccessToken());
        }

        AccessTokenResponse res = responseBuilder.build();

        return res.getToken();
    }

    public static String generateAccessToken(KeycloakSession session, UserSessionModel userSession, String clientId, String scope, Consumer<AccessToken> tokenAdjuster) {

        KeycloakContext context = session.getContext();
        RealmModel realm = userSession.getRealm();
        ClientModel client = session.clients().getClientByClientId(realm, clientId);
        String issuer = Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName());

        RootAuthenticationSessionModel rootAuthSession = new AuthenticationSessionManager(session).createAuthenticationSession(realm, false);
        AuthenticationSessionModel iamAuthSession = rootAuthSession.createAuthenticationSession(client);

        iamAuthSession.setAuthenticatedUser(userSession.getUser());
        iamAuthSession.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        iamAuthSession.setClientNote(OIDCLoginProtocol.ISSUER, issuer);
        iamAuthSession.setClientNote(OIDCLoginProtocol.SCOPE_PARAM, scope);

        ClientConnection connection = context.getConnection();
        UserSessionModel iamUserSession = session.sessions().createUserSession(KeycloakModelUtils.generateId(), realm, userSession.getUser(), userSession.getUser().getUsername(), connection.getRemoteAddr(), ServiceAccountConstants.CLIENT_AUTH, false, null, null, UserSessionModel.SessionPersistenceState.TRANSIENT);

        AuthenticationManager.setClientScopesInSession(iamAuthSession);
        ClientSessionContext clientSessionCtx = TokenManager.attachAuthenticationSession(session, iamUserSession, iamAuthSession);

        // Notes about client details
        userSession.setNote(ServiceAccountConstants.CLIENT_ID, client.getClientId());
        userSession.setNote(ServiceAccountConstants.CLIENT_HOST, connection.getRemoteHost());
        userSession.setNote(ServiceAccountConstants.CLIENT_ADDRESS, connection.getRemoteAddr());

        TokenManager tokenManager = new TokenManager();

        EventBuilder eventBuilder = new EventBuilder(realm, session, connection);
        TokenManager.AccessTokenResponseBuilder tokenResponseBuilder = tokenManager.responseBuilder(realm, client, eventBuilder, session, iamUserSession, clientSessionCtx);
        AccessToken accessToken = tokenResponseBuilder.generateAccessToken().getAccessToken();

        tokenAdjuster.accept(accessToken);

        AccessTokenResponse tokenResponse = tokenResponseBuilder.build();

        return tokenResponse.getToken();
    }
}
