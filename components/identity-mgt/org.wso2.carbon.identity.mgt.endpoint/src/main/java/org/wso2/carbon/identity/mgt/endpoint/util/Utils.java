/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.mgt.endpoint.util;

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HttpTransportProperties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.mgt.endpoint.IdentityManagementEndpointConstants;
import org.wso2.carbon.identity.mgt.endpoint.IdentityManagementServiceUtil;

import java.io.InputStream;
import java.util.Properties;

/**
 * Utils class for the authentication endpoint.
 */
public class Utils {

    private static final Log log = LogFactory.getLog(Utils.class);

    public static void authenticate(ServiceClient client) throws Exception {

        Properties properties = new Properties();
        InputStream inputStream = IdentityManagementServiceUtil.class.getClassLoader().getResourceAsStream
                    (IdentityManagementEndpointConstants.SERVICE_CONFIG_FILE_NAME);
        properties.load(inputStream);

        String accessUsername = properties.getProperty(IdentityManagementEndpointConstants.ServiceConfigConstants
                .SERVICE_ACCESS_USERNAME);
        String accessPassword = properties.getProperty(IdentityManagementEndpointConstants.ServiceConfigConstants
                .SERVICE_ACCESS_PASSWORD);

        if (accessUsername != null && accessPassword != null) {
            setOptions(client, accessUsername, accessPassword);
        } else {
            throw new Exception("Authentication username or password not set");
        }
    }

    public static void setOptions(ServiceClient client, String accessUsername, String accessPassword) {
        Options option = client.getOptions();
        HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();
        auth.setUsername(accessUsername);
        auth.setPassword(accessPassword);
        auth.setPreemptiveAuthentication(true);
        option.setProperty(org.apache.axis2.transport.http.HTTPConstants.AUTHENTICATE, auth);
        option.setManageSession(true);
    }
}
