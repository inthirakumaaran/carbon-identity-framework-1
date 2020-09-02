/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.mgt.listener;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.AuthenticationStep;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.LocalAndOutboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.OutboundProvisioningConfig;
import org.wso2.carbon.identity.application.common.model.ProvisioningConnectorConfig;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationConstants;
import org.wso2.carbon.identity.application.mgt.ApplicationMgtSystemConfig;
import org.wso2.carbon.identity.application.mgt.cache.IdentityServiceProviderCache;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementException;
import org.wso2.carbon.idp.mgt.IdentityProviderManager;
import org.wso2.carbon.idp.mgt.listener.AbstractIdentityProviderMgtListener;

import java.util.ArrayList;
import java.util.List;

public class ApplicationIdentityProviderMgtListener extends AbstractIdentityProviderMgtListener {

    private static final Log log = LogFactory.getLog(ApplicationIdentityProviderMgtListener.class);

    @Override
    public boolean doPreUpdateIdP(String oldIdPName, IdentityProvider identityProvider, String tenantDomain) throws
            IdentityProviderManagementException {

        try {

            /* Retrieving the applications that has configured with federated authenticators and provisioning with
               the IDP we are going to update.
             */
            IdentityServiceProviderCache.getInstance().clear();
            IdentityProviderManager identityProviderManager = IdentityProviderManager.getInstance();
            List<String> connectedApplications =
                    identityProviderManager.getConnectedApplications(oldIdPName, tenantDomain);

            List<ServiceProvider> serviceProvidersList = new ArrayList<>();
            for (String appName : connectedApplications) {
                ServiceProvider serviceProvider = ApplicationMgtSystemConfig.getInstance().getApplicationDAO()
                        .getApplication(appName, tenantDomain);
                serviceProvidersList.add(serviceProvider);
            }

            // Adding Local Service Provider to the list of service providers
            ServiceProvider localSp = ApplicationMgtSystemConfig.getInstance()
                    .getApplicationDAO().getApplication(ApplicationConstants.LOCAL_SP, tenantDomain);
            serviceProvidersList.add(localSp);

            for (ServiceProvider serviceProvider : serviceProvidersList) {

                LocalAndOutboundAuthenticationConfig localAndOutboundAuthConfig = serviceProvider
                        .getLocalAndOutBoundAuthenticationConfig();
                AuthenticationStep[] authSteps = localAndOutboundAuthConfig.getAuthenticationSteps();

                OutboundProvisioningConfig outboundProvisioningConfig = serviceProvider
                        .getOutboundProvisioningConfig();
                IdentityProvider[] associatedProvisioningIdps =
                        outboundProvisioningConfig.getProvisioningIdentityProviders();

                // Check whether the identity provider is going to be disabled.
                if (!identityProvider.isEnable()) {
                    for (AuthenticationStep authenticationStep : authSteps) {
                        for (IdentityProvider associatedIdentityProvider : authenticationStep
                                .getFederatedIdentityProviders()) {
                            if (StringUtils.equals(identityProvider.getIdentityProviderName(),
                                    associatedIdentityProvider.getIdentityProviderName())) {
                                throw new IdentityProviderManagementException(
                                        "Error in disabling identity provider as it is referred by service providers.");
                            }
                        }
                    }

                    for (IdentityProvider associatedProvisioningIdp : associatedProvisioningIdps) {
                        if (StringUtils.equals(identityProvider.getIdentityProviderName(),
                                associatedProvisioningIdp.getIdentityProviderName())) {
                            throw new IdentityProviderManagementException(
                                    "Error in disabling identity provider as it is referred by service providers.");
                        }
                    }
                }

                /**
                 *  Checking federated authenticators configured in the application is going to be changed with the
                 *  update.
                 */
                if (authSteps != null && authSteps.length != 0) {
                    if (ApplicationConstants.AUTH_TYPE_FEDERATED
                            .equalsIgnoreCase(localAndOutboundAuthConfig.getAuthenticationType())) {
                        // Update the service providers, with the default authenticator of the identity provider
                        IdentityProvider fedIdp = authSteps[0].getFederatedIdentityProviders()[0];
                        if (StringUtils.equals(fedIdp.getIdentityProviderName(), identityProvider
                                .getIdentityProviderName())) {

                            String defaultAuthName = fedIdp
                                    .getDefaultAuthenticatorConfig().getName();
                            if (identityProvider.getDefaultAuthenticatorConfig() != null) {
                                String currentDefaultAuthName = identityProvider.getDefaultAuthenticatorConfig().getName();
                                boolean isCurrentDefaultAuthEnabled = identityProvider.getDefaultAuthenticatorConfig().isEnabled();

                                if (!StringUtils.equals(currentDefaultAuthName, defaultAuthName)) {
                                    FederatedAuthenticatorConfig currentDefaultAuthenticatorConfig = identityProvider
                                            .getDefaultAuthenticatorConfig();
                                    fedIdp.setDefaultAuthenticatorConfig(currentDefaultAuthenticatorConfig);
                                    fedIdp.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]
                                            {currentDefaultAuthenticatorConfig});
                                    ApplicationMgtSystemConfig.getInstance().getApplicationDAO()
                                            .updateApplication(serviceProvider, tenantDomain);
                                } else if (!isCurrentDefaultAuthEnabled && StringUtils.equals(currentDefaultAuthName, defaultAuthName)) {
                                    throw new IdentityProviderManagementException(
                                            "Error in disabling default federated authenticator as it is referred by service providers.");
                                }
                            }
                        }
                    } else {
                        //Check whether the selected authenticator in multi step authentication, is enabled in the updated identity provider
                        FederatedAuthenticatorConfig[] idpFederatedConfig =
                                identityProvider.getFederatedAuthenticatorConfigs();
                        for (AuthenticationStep authStep : authSteps) {

                            IdentityProvider[] associatedIdpsInStep = authStep.getFederatedIdentityProviders();

                            for (IdentityProvider associatedIdpInStep : associatedIdpsInStep) {

                                if (StringUtils.equals(associatedIdpInStep.getIdentityProviderName(),
                                        identityProvider.getIdentityProviderName())) {
                                    FederatedAuthenticatorConfig[] federatedAuthenticatorConfigs =
                                            associatedIdpInStep.getFederatedAuthenticatorConfigs();
                                    String federatedConfigOption = federatedAuthenticatorConfigs[0].getName();

                                    for (FederatedAuthenticatorConfig config : idpFederatedConfig) {
                                        if (StringUtils.equals(config.getName(), federatedConfigOption) &&
                                                !config.isEnabled()) {
                                            throw new IdentityProviderManagementException(config.getName()
                                                    + " is referred by service providers.");
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                /**
                 * Checking outbound provisioning connectors configured in the application is going to be changed
                 * with the update.
                 */
                if (associatedProvisioningIdps != null && associatedProvisioningIdps.length != 0) {

                    ProvisioningConnectorConfig[] idpProvisioningConnectorConfigs =
                            identityProvider.getProvisioningConnectorConfigs();
                    for (IdentityProvider associatedProvisioningIdp : associatedProvisioningIdps) {

                        if (StringUtils.equals(associatedProvisioningIdp.getIdentityProviderName(),
                                identityProvider.getIdentityProviderName())) {
                            ProvisioningConnectorConfig defaultProvisioningConnectorConfig =
                                    associatedProvisioningIdp.getDefaultProvisioningConnectorConfig();

                            for (ProvisioningConnectorConfig config : idpProvisioningConnectorConfigs) {
                                if (StringUtils
                                        .equals(config.getName(), defaultProvisioningConnectorConfig.getName()) &&
                                        !config.isEnabled()) {
                                    throw new IdentityProviderManagementException(config.getName()
                                            + " outbound provisioning connector is referred by service providers.");
                                }
                            }
                        }

                    }
                }
            }
        } catch (IdentityApplicationManagementException e) {
            throw new IdentityProviderManagementException("Error when updating default authenticator of service providers", e);
        }
        return true;
    }

    @Override
    public boolean doPostDeleteIdP(String idPName, String tenantDomain) throws IdentityProviderManagementException {
        if (log.isDebugEnabled()) {
            log.debug("doPostDeleteIdp executed for idp: " + idPName + " of tenantDomain: " + tenantDomain);
        }

        // Clear the SP cache since deleted IDP might have contained association with SPs.
        IdentityServiceProviderCache.getInstance().clear();
        if (log.isDebugEnabled()) {
            log.debug("IdentityServiceProvider Cache is cleared on post delete event of idp: " + idPName + " of " +
                    "tenantDomain: " + tenantDomain);
        }

        return super.doPostDeleteIdP(idPName, tenantDomain);
    }

    public int getDefaultOrderId() {
        return 10;
    }
}

