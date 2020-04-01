/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.identity.application.authentication.framework.handler.request.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.authentication.framework.ApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.config.model.ApplicationConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.AuthenticatorConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.SequenceConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.exception.PostAuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.AbstractPostAuthnHandler;
import org.wso2.carbon.identity.application.authentication.framework.handler.request.PostAuthnHandlerFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.handler.sequence.impl.DefaultSequenceHandlerUtils;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkErrorConstants;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.claim.metadata.mgt.ClaimMetadataHandler;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataException;
import org.wso2.carbon.identity.user.profile.mgt.UserProfileAdmin;
import org.wso2.carbon.identity.user.profile.mgt.UserProfileException;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.wso2.carbon.identity.application.authentication.framework.handler.request.PostAuthnHandlerFlowStatus.SUCCESS_COMPLETED;

/**
 * This PostAuthentication Handler is responsible for handling the association of user accounts with local users.
 */
public class PostAuthAssociationHandler extends AbstractPostAuthnHandler {

    private static final Log log = LogFactory.getLog(PostAuthAssociationHandler.class);
    private static PostAuthAssociationHandler instance = new PostAuthAssociationHandler();
    private static final String USER_TENANT_DOMAIN = "user-tenant-domain";

    /**
     * To get an instance of {@link PostAuthAssociationHandler}.
     *
     * @return instance of PostAuthAssociationHandler
     */
    public static PostAuthAssociationHandler getInstance() {
        return instance;
    }

    /**
     * To avoid creation of multiple instances of this handler.
     */
    protected PostAuthAssociationHandler() { }

    @Override
    public int getPriority() {

        int priority = super.getPriority();
        if (priority == -1) {
            /* Priority should be greater than PostJitProvisioningHandler, so that JIT provisioned users local claims would
         passed to the service provider given the assert local mapped user option is selected */
            priority = 25;
        }
        return priority;
    }

    @Override
    public String getName() {

        return "PostAuthAssociationHandler";
    }

    @Override
    @SuppressWarnings("unchecked")
    public PostAuthnHandlerFlowStatus handle(HttpServletRequest request, HttpServletResponse response,
            AuthenticationContext context) throws PostAuthenticationFailedException {

        if (!FrameworkUtils.isStepBasedSequenceHandlerExecuted(context)) {
            return SUCCESS_COMPLETED;
        }
        SequenceConfig sequenceConfig = context.getSequenceConfig();
        for (Map.Entry<Integer, StepConfig> entry : sequenceConfig.getStepMap().entrySet()) {
            StepConfig stepConfig = entry.getValue();
            AuthenticatorConfig authenticatorConfig = stepConfig.getAuthenticatedAutenticator();
            if (authenticatorConfig == null) {
                //May have skipped from the script
                //ex: Different authentication sequences evaluated by the script
                continue;
            }
            ApplicationAuthenticator authenticator = authenticatorConfig.getApplicationAuthenticator();

            if (authenticator instanceof FederatedApplicationAuthenticator) {
                if (stepConfig.isSubjectIdentifierStep()) {
                    if (log.isDebugEnabled()) {
                        log.debug(authenticator.getName() + " has been set up for subject identifier step.");
                    }
                     /*
                    If AlwaysSendMappedLocalSubjectId is selected, need to get the local user associated with the
                    federated idp.
                     */
                    String associatedLocalUserName = null;
                    if (sequenceConfig.getApplicationConfig().isAlwaysSendMappedLocalSubjectId()) {
                        associatedLocalUserName = getUserNameAssociatedWith(context, stepConfig);
                    }
                    if (StringUtils.isNotEmpty(associatedLocalUserName)) {
                        if (log.isDebugEnabled()) {
                            log.debug("AlwaysSendMappedLocalSubjectID is selected in service provider level, "
                                    + "equavlent local user : " + associatedLocalUserName);
                        }
                        setAssociatedLocalUserToContext(associatedLocalUserName, context, stepConfig);
                    }
                }
            }
        }
        return SUCCESS_COMPLETED;
    }

    /**
     * To set the associated local user in automation context and to add the relevant claims.
     *
     * @param associatedLocalUserName Associated Local username.
     * @param context                 Authentication context.
     * @param stepConfig              Configuration related with current authentication step.
     * @throws PostAuthenticationFailedException Post Authentication failed exception.
     */
    private void setAssociatedLocalUserToContext(String associatedLocalUserName, AuthenticationContext context,
            StepConfig stepConfig) throws PostAuthenticationFailedException {

        SequenceConfig sequenceConfig = context.getSequenceConfig();
        String fullQualifiedAssociatedUserId = FrameworkUtils.prependUserStoreDomainToName(
                associatedLocalUserName + UserCoreConstants.TENANT_DOMAIN_COMBINER + context.getTenantDomain());
        UserCoreUtil.setDomainInThreadLocal(UserCoreUtil.extractDomainFromName(associatedLocalUserName));
        sequenceConfig.setAuthenticatedUser(
                AuthenticatedUser.createLocalAuthenticatedUserFromSubjectIdentifier(fullQualifiedAssociatedUserId));
        sequenceConfig.getApplicationConfig().setMappedSubjectIDSelected(true);

        Map<String, String> mappedAttrs = handleClaimMappings(stepConfig, context);
        handleRoleMapping(context, sequenceConfig, mappedAttrs);
        Map<ClaimMapping, String> authenticatedUserAttributes = getClaimMapping(context, mappedAttrs);
        if (MapUtils.isNotEmpty(authenticatedUserAttributes)) {
            sequenceConfig.getAuthenticatedUser().setUserAttributes(authenticatedUserAttributes);
            if (log.isDebugEnabled()) {
                log.debug("Local claims from the local user: " + associatedLocalUserName + ", set as "
                        + "user attributed for the federated scenario");
            }
        }
        // in this case associatedID is a local user name - belongs to a tenant in IS.
        String tenantDomain = MultitenantUtils.getTenantDomain(associatedLocalUserName);
        Map<String, Object> authProperties = context.getProperties();

        if (authProperties == null) {
            authProperties = new HashMap<>();
            context.setProperties(authProperties);
        }
        authProperties.put(USER_TENANT_DOMAIN, tenantDomain);
        if (log.isDebugEnabled()) {
            log.debug(
                    "Authenticated User: " + sequenceConfig.getAuthenticatedUser().getAuthenticatedSubjectIdentifier());
            log.debug("Authenticated User Tenant Domain: " + tenantDomain);
        }
    }
    /**
     * To get the local user name associated with the given federated IDP and the subject identifier.
     *
     * @param context    Authentication context.
     * @param stepConfig Step config.
     * @return user name associated with.
     * @throws PostAuthenticationFailedException Post Authentication Failed Exception.
     */
    private String getUserNameAssociatedWith(AuthenticationContext context, StepConfig stepConfig)
            throws PostAuthenticationFailedException {

        String associatesUserName;
        UserProfileAdmin userProfileAdmin = UserProfileAdmin.getInstance();
        String originalExternalIdpSubjectValueForThisStep = stepConfig.getAuthenticatedUser()
                .getAuthenticatedSubjectIdentifier();
        try {
            FrameworkUtils.startTenantFlow(context.getTenantDomain());
            associatesUserName = userProfileAdmin.getNameAssociatedWith(stepConfig.getAuthenticatedIdP(),
                    originalExternalIdpSubjectValueForThisStep);
            if (StringUtils.isNotBlank(associatesUserName)) {
                if (log.isDebugEnabled()) {
                    log.debug("User : " + stepConfig.getAuthenticatedUser() + " has an associated account as "
                            + associatesUserName + ". Hence continuing as " + associatesUserName);
                }
                stepConfig.getAuthenticatedUser().setUserName(associatesUserName);
                stepConfig.getAuthenticatedUser().setTenantDomain(context.getTenantDomain());
                stepConfig.setAuthenticatedUser(stepConfig.getAuthenticatedUser());
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("User " + stepConfig.getAuthenticatedUser() + " doesn't have an associated"
                            + " account. Hence continuing as the same user.");
                }
            }
        } catch (UserProfileException e) {
            throw new PostAuthenticationFailedException(
                    FrameworkErrorConstants.ErrorMessages.ERROR_WHILE_GETTING_LOCAL_USER_ID.getCode(),
                    String.format(FrameworkErrorConstants.ErrorMessages.ERROR_WHILE_GETTING_IDP_BY_NAME.getMessage(),
                            originalExternalIdpSubjectValueForThisStep), e);
        } finally {
            FrameworkUtils.endTenantFlow();
        }
        return associatesUserName;
    }

    /**
     * To get the claim mapping based on user local.
     *
     * @param context    Authentication Context.
     * @param mappedAttrs    Mapped user attributes.
     * @return claim mapping.
     * @throws PostAuthenticationFailedException Post Authentication Failed Exception.
     */
    @SuppressWarnings("unchecked")
    private Map<ClaimMapping, String> getClaimMapping(AuthenticationContext context, Map<String, String> mappedAttrs) {

        Map<ClaimMapping, String> mappedClaims = null;
        Map<String, String> localClaimValues = (Map<String, String>) context
                .getProperty(FrameworkConstants.UNFILTERED_LOCAL_CLAIM_VALUES);
        Map<String, String> idpClaimValues = (Map<String, String>) context
                .getProperty(FrameworkConstants.UNFILTERED_IDP_CLAIM_VALUES);
        // if no requested claims are selected, send all local mapped claim values or idp claim values
        if (context.getSequenceConfig().getApplicationConfig().getRequestedClaimMappings() == null || context
                .getSequenceConfig().getApplicationConfig().getRequestedClaimMappings().isEmpty()) {
            if (MapUtils.isNotEmpty(localClaimValues)) {
                mappedAttrs = localClaimValues;
            } else if (MapUtils.isNotEmpty(idpClaimValues)) {
                mappedAttrs = idpClaimValues;
            }
        }
        if (MapUtils.isNotEmpty(mappedAttrs)) {
            mappedClaims = FrameworkUtils.buildClaimMappings(mappedAttrs);
        }
        return mappedClaims;
    }

    private void handleRoleMapping(AuthenticationContext context, SequenceConfig sequenceConfig, Map<String, String>
            mappedAttrs) throws PostAuthenticationFailedException {

        String spRoleUri = getSpRoleClaimUri(sequenceConfig.getApplicationConfig());
        String roleAttr = null;
        String spStandardDialect = getSPStandardDialect(context);
        if (spStandardDialect != null && isLocalClaimDialect(context)) {
            spRoleUri = getStandardRoleClaimURI(spStandardDialect, context.getTenantDomain());
            roleAttr = mappedAttrs.get(spRoleUri);
        } else if (spStandardDialect != null && !isLocalClaimDialect(context)) {
            String localClaim = getSPMappedLocalRoleClaimURI(sequenceConfig.getApplicationConfig());
            spRoleUri = getStandardClaimURIFromLocal(spStandardDialect, context.getTenantDomain(),
                    localClaim);
            roleAttr = mappedAttrs.get(spRoleUri);
        } else if (spStandardDialect == null && isLocalClaimDialect(context)) {
            roleAttr = mappedAttrs.get(spRoleUri);
        } else if (spStandardDialect == null && !isLocalClaimDialect(context)) {
            roleAttr = mappedAttrs.get(spRoleUri);
        }

        if (StringUtils.isNotBlank(roleAttr)) {
            //Need to convert multiAttributeSeparator value into a regex literal before calling
            // split function. Otherwise split can produce misleading results in case
            // multiAttributeSeparator contains regex special meaning characters like .*
            String[] roles = roleAttr.split(Pattern.quote(FrameworkUtils.getMultiAttributeSeparator()));
            String spMappedUserRoles = DefaultSequenceHandlerUtils.getServiceProviderMappedUserRoles(sequenceConfig,
                    Arrays.asList(roles));
            mappedAttrs.put(spRoleUri, spMappedUserRoles);
        }
    }

    private String getSpRoleClaimUri(ApplicationConfig appConfig) {

        // get external identity provider role claim uri.
        String spRoleClaimUri = appConfig.getRoleClaim();

        if (StringUtils.isEmpty(spRoleClaimUri)) {
            // no role claim uri defined
            // we can still try to find it out - lets have a look at the claim
            // mapping.
            Map<String, String> spToLocalClaimMapping = appConfig.getClaimMappings();

            if (MapUtils.isNotEmpty(spToLocalClaimMapping)) {

                for (Map.Entry<String, String> entry : spToLocalClaimMapping.entrySet()) {
                    if (FrameworkConstants.LOCAL_ROLE_CLAIM_URI.equals(entry.getValue())) {
                        spRoleClaimUri = entry.getKey();
                        break;
                    }
                }
            }
        }

        if (StringUtils.isEmpty(spRoleClaimUri)) {
            spRoleClaimUri = FrameworkConstants.LOCAL_ROLE_CLAIM_URI;
            if (log.isDebugEnabled()) {
                String serviceProvider = appConfig.getApplicationName();
                log.debug("Service Provider Role Claim URI not configured for SP: " + serviceProvider +
                        ". Defaulting to " + spRoleClaimUri);
            }
        }

        return spRoleClaimUri;
    }

    private String getSPStandardDialect(AuthenticationContext context) {

        ApplicationConfig appConfig = context.getSequenceConfig().getApplicationConfig();
        String spStandardDialect;
        if (context.getProperties().containsKey(FrameworkConstants.SP_STANDARD_DIALECT)) {
            spStandardDialect = (String) context.getProperty(FrameworkConstants.SP_STANDARD_DIALECT);
        } else {
            spStandardDialect = FrameworkUtils.getStandardDialect(context.getRequestType(), appConfig);
        }
        return spStandardDialect;
    }

    private boolean isLocalClaimDialect(AuthenticationContext context) {

        ApplicationConfig appConfig = context.getSequenceConfig().getApplicationConfig();
        ClaimConfig claimConfig = appConfig.getServiceProvider().getClaimConfig();
        return claimConfig.isLocalClaimDialect();
    }

    private String getStandardRoleClaimURI(String standardDialect, String tenantDomain)
            throws PostAuthenticationFailedException {

        String roleClaim = getStandardClaimURIFromLocal(standardDialect, tenantDomain, FrameworkConstants
                .LOCAL_ROLE_CLAIM_URI);
        if (StringUtils.isBlank(roleClaim)) {
            return FrameworkConstants.LOCAL_ROLE_CLAIM_URI;
        }
        return roleClaim;
    }

    private String getStandardClaimURIFromLocal(String standardDialect, String tenantDomain, String claimURI)
            throws PostAuthenticationFailedException {

        try {
            Map<String, String> claimMapping = ClaimMetadataHandler.getInstance()
                    .getMappingsMapFromOtherDialectToCarbon(standardDialect, null, tenantDomain, true);
            if (claimMapping.containsKey(claimURI)) {
                return claimMapping.get(claimURI);
            }
        } catch (ClaimMetadataException e) {
            throw new PostAuthenticationFailedException(FrameworkErrorConstants.ErrorMessages.
                    ERROR_WHILE_HANDLING_CLAIM_MAPPINGS.getCode(), FrameworkErrorConstants.ErrorMessages.
                    ERROR_WHILE_HANDLING_CLAIM_MAPPINGS.getMessage(), e);
        }
        return null;
    }

    private String getSPMappedLocalRoleClaimURI(ApplicationConfig appConfig) {

        String spRoleClaimUri = appConfig.getRoleClaim();
        if (StringUtils.isNotBlank(spRoleClaimUri)) {

            Map<String, String> spToLocalClaimMapping = appConfig.getClaimMappings();
            if (spToLocalClaimMapping != null && !spToLocalClaimMapping.isEmpty()) {

                for (Map.Entry<String, String> entry : spToLocalClaimMapping.entrySet()) {
                    if (spRoleClaimUri.equals(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
        }
        return FrameworkConstants.LOCAL_ROLE_CLAIM_URI;
    }

    private Map<String, String> handleClaimMappings(StepConfig stepConfig, AuthenticationContext context)
            throws PostAuthenticationFailedException {

        Map<String, String> mappedAttrs;
        try {
            mappedAttrs = FrameworkUtils.getClaimHandler().handleClaimMappings(stepConfig, context, null, false);
            return mappedAttrs;
        } catch (FrameworkException e) {
            throw new PostAuthenticationFailedException(FrameworkErrorConstants.ErrorMessages.
                    ERROR_WHILE_GETTING_CLAIM_MAPPINGS.getCode(), String.format(FrameworkErrorConstants.ErrorMessages.
                            ERROR_WHILE_GETTING_CLAIM_MAPPINGS.getMessage(),
                    context.getSequenceConfig().getAuthenticatedUser().getUserName()), e);
        }
    }
}
