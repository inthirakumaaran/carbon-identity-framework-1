/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.mgt.endpoint.client;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.ServiceClient;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.identity.application.common.model.idp.xsd.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.idp.xsd.IdentityProviderProperty;
import org.wso2.carbon.identity.mgt.endpoint.IdentityManagementEndpointConstants;
import org.wso2.carbon.identity.mgt.endpoint.util.Utils;
import org.wso2.carbon.idp.mgt.stub.IdentityProviderMgtServiceStub;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

/**
 * Validates the call back URL passed in the recovery request.
 */
public class CallBackValidator {

    private static final Log log = LogFactory.getLog(CallBackValidator.class);
    private IdentityProviderMgtServiceStub idPMgtStub;

    /**
     * This method is to validate the callback URL in the request with the configured one.
     *
     * @param callbackURL  passed in the request
     * @param tenantDomain of the user
     * @return the status of the validation
     * @throws IdentityRecoveryException
     */
    public boolean isValidCallbackURL(String callbackURL, String tenantDomain, boolean isUserPortalURL)
            throws IdentityRecoveryException {

        if (isUserPortalURL) {
            if (log.isDebugEnabled()) {
                log.debug("Callback URL is equal to the user portal URL: " + isUserPortalURL);
            }
            return true;
        }

        if (StringUtils.isBlank(tenantDomain)) {
            tenantDomain = MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;
            if (log.isDebugEnabled()) {
                log.debug("Tenant domain is considered as super tenant domain: " + tenantDomain);
            }
        }

        IdentityProvider residentIdp;
        try {
            idPMgtStub = new IdentityProviderMgtServiceStub();
            ServiceClient idpClient = idPMgtStub._getServiceClient();
            Utils.authenticate(idpClient);
            residentIdp = idPMgtStub.getResidentIdP();
        } catch (AxisFault axisFault) {
            throw new IdentityRecoveryException("Error while instantiating IdentityProviderMgtServiceStub", axisFault);
        } catch (Exception e) {
            throw new IdentityRecoveryException("Error occurred when getting residentIDP configurations.", e);
        }

        IdentityProviderProperty[] idpProperties = null;
        if (residentIdp != null) {
            idpProperties = residentIdp.getIdpProperties();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Resident identity provider is not found for the tenant domain: " + tenantDomain);
            }
        }

        String callbackRegex = null;
        if (idpProperties != null) {
            for (IdentityProviderProperty property : idpProperties) {
                if (IdentityManagementEndpointConstants.UserInfoRecovery.RECOVERY_CALLBACK_REGEX.equals(property
                        .getValue())) {
                    callbackRegex = property.getValue();
                    if (log.isDebugEnabled()) {
                        log.debug("Configured recovery callback URL regex: " + callbackRegex);
                    }
                    break;
                }
            }
        }

        if (StringUtils.isNotBlank(callbackURL)) {
            try {
                String encodeURL = URLEncoder.encode(callbackURL, IdentityManagementEndpointConstants.UTF_8);
                URI uri = new URI(encodeURL);
                callbackURL = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null)
                        .toString();
                if (log.isDebugEnabled()) {
                    log.debug("Callbck URL in the username recovery request: " + callbackURL);
                }
            } catch (URISyntaxException e) {
                throw new IdentityRecoveryException("Error occurred while formatting the provided callback URL. ", e);
            } catch (UnsupportedEncodingException e) {
                throw new IdentityRecoveryException("Error occurred while encoding the provided callback URL.", e);
            }
        }

        return callbackRegex == null || callbackURL.matches(callbackRegex);
    }
}
