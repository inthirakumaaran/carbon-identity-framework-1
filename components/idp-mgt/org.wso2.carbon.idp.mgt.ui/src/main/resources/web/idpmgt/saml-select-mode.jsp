<%--
  ~ Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
  ~
  ~ WSO2 Inc. licenses this file to you under the Apache License,
  ~ Version 2.0 (the "License"); you may not use this file except
  ~ in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  ~
  --%>

<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="carbon" uri="http://wso2.org/projects/carbon/taglibs/carbontags.jar" %>
<link href="css/idpmgt.css" rel="stylesheet" type="text/css" media="all"/>
<jsp:include page="../dialog/display_messages.jsp"/>

<fmt:bundle basename="org.wso2.carbon.idp.mgt.ui.i18n.Resources">
    <table class="carbonFormTable" width="100%">
        <tbody>
            <tr>
                <td class="leftCol-med labelField">
                    <fmt:message key='saml.sso.select.mode'/><span></span>
                </td>
                <td>
                    <input type="radio" checked="checked" name="saml_ui_mode"  value="manual"
                            onclick="$('#manual_section').show(); $('#metadata_section').hide();">
                    <fmt:message key='saml.mode.manual'/>
                    <input type="radio" name="saml_ui_mode" value="file"
                            onclick="$('#manual_section').hide(); $('#metadata_section').show();">
                    <fmt:message key='saml.mode.file'/>
                    <div class="sectionHelp">
                        <fmt:message key='help.metadata.select.mode'/>
                    </div>
                </td>
            </tr>
        </tbody>
    </table>
</fmt:bundle>
