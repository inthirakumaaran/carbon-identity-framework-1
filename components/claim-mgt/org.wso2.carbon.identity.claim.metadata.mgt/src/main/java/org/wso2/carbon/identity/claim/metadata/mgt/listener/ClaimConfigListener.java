/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.identity.claim.metadata.mgt.listener;

import org.wso2.carbon.identity.claim.metadata.mgt.cache.LocalClaimCache;
import org.wso2.carbon.identity.claim.metadata.mgt.util.ClaimConstants;
import org.wso2.carbon.identity.claim.metadata.mgt.util.SQLConstants;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.user.store.configuration.listener.UserStoreConfigListener;
import org.wso2.carbon.user.api.UserStoreException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class which contains the implementation of a listener for claim configurations. This listener is responsible for
 * actions related to claim configurations when the user store change happens.
 */
public class ClaimConfigListener implements UserStoreConfigListener {

    @Override
    public void onUserStoreNamePreUpdate(int tenantId, String currentUserStoreName, String newUserStoreName) throws
            UserStoreException {

        // Not Implemented.
    }

    @Override
    public void onUserStoreNamePostUpdate(int tenantId, String currentUserStoreName, String newUserStoreName) throws
            UserStoreException {

        // Not Implemented.
    }

    @Override
    public void onUserStorePreDelete(int tenantId, String domainName) throws UserStoreException {

        Connection dbConnection = IdentityDatabaseUtil.getDBConnection(true);
        PreparedStatement preparedStatement = null;

        try {
            preparedStatement = dbConnection.prepareStatement(SQLConstants.DELETE_IDN_CLAIM_MAPPED_ATTRIBUTE);
            preparedStatement.setString(1, domainName);
            preparedStatement.setInt(2, tenantId);
            preparedStatement.executeUpdate();
            IdentityDatabaseUtil.commitTransaction(dbConnection);
            LocalClaimCache.getInstance().clearCacheEntry(tenantId);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(dbConnection);
            String message =
                    String.format(ClaimConstants.ErrorMessage.ERROR_CODE_DELETE_IDN_CLAIM_MAPPED_ATTRIBUTE.getMessage(),
                            domainName, tenantId);
            throw new UserStoreException(message, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(preparedStatement);
            IdentityDatabaseUtil.closeConnection(dbConnection);
        }
    }

    @Override
    public void onUserStorePostDelete(int tenantId, String userStoreName) throws UserStoreException {

        // Not implemented.
    }
}
