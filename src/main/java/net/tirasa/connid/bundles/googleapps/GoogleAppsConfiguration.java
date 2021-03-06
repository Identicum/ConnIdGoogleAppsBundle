/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2016 ConnId.
 */
package net.tirasa.connid.bundles.googleapps;

import java.security.GeneralSecurityException;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.licensing.Licensing;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the GoogleApps Connector.
 */
public class GoogleAppsConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private String domain = null;

    /**
     * Client identifier issued to the client during the registration process.
     */
    private String clientId;

    /**
     * Client secret or {@code null} for none.
     */
    private GuardedString clientSecret = null;

    private GuardedString refreshToken = null;

    @ConfigurationProperty(order = 1, displayMessageKey = "domain.display",
            groupMessageKey = "basic.group", helpMessageKey = "domain.help", required = true,
            confidential = false)
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "clientid.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientid.help", required = true,
            confidential = false)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "clientsecret.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientsecret.help", required = true,
            confidential = true)
    public GuardedString getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(GuardedString clientSecret) {
        this.clientSecret = clientSecret;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "refreshtoken.display",
            groupMessageKey = "basic.group", helpMessageKey = "refreshtoken.help", required = true,
            confidential = true)
    public GuardedString getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(GuardedString refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public void validate() {
        if (StringUtil.isBlank(domain)) {
            throw new IllegalArgumentException("Domain cannot be null or empty.");
        }
        if (StringUtil.isBlank(clientId)) {
            throw new IllegalArgumentException("Client Id cannot be null or empty.");
        }
        if (null == clientSecret) {
            throw new IllegalArgumentException("Client Secret cannot be null or empty.");
        }
        if (null == refreshToken) {
            throw new IllegalArgumentException("Refresh Token cannot be null or empty.");
        }
    }

    private Credential credential = null;

    public Credential getGoogleCredential() {
        synchronized (this) {
            if (null == credential) {
                credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).
                        setTransport(HTTP_TRANSPORT).
                        setJsonFactory(JSON_FACTORY).
                        setTokenServerEncodedUrl(GoogleOAuthConstants.TOKEN_SERVER_URL).
                        setClientAuthentication(
                                new ClientParametersAuthentication(getClientId(),
                                        SecurityUtil.decrypt(getClientSecret())))
                        .build();

                getRefreshToken().access(new GuardedString.Accessor() {

                    @Override
                    public void access(char[] chars) {
                        credential.setRefreshToken(new String(chars));
                    }
                });

                directory = new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).
                        setApplicationName("ConnId").
                        build();
                licensing = new Licensing.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).
                        setApplicationName("ConnId").
                        build();
            }
        }
        return credential;
    }

    @Override
    public void release() {

    }

    /** Global instance of the HTTP transport. */
    private static final HttpTransport HTTP_TRANSPORT;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    public Directory getDirectory() {
        getGoogleCredential();
        return directory;
    }

    public Licensing getLicensing() {
        getGoogleCredential();
        if (null == licensing) {
            throw new ConnectorException("Licensing is not enabled");
        }
        return licensing;
    }

    private Directory directory;

    private Licensing licensing;

    static {
        HttpTransport t = null;
        try {
            t = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            try {
                t = new NetHttpTransport.Builder().doNotValidateCertificate().build();
            } catch (GeneralSecurityException e1) {
            }
        }
        HTTP_TRANSPORT = t;
    }

}
