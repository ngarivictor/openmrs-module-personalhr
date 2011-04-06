/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.personalhr.web;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openmrs.api.context.Context;
import org.openmrs.module.personalhr.PersonalhrUtil;
import org.openmrs.module.personalhr.PhrAllowedUrl;
import org.openmrs.module.personalhr.PhrSecurityRule;

/**
 *
 */
public class DWRPersonalhrService {
    protected final Log log = LogFactory.getLog(getClass());
    
	public void addAllowedUrl(String allowedUrl, String privilege, String description) {
		log.debug("Calling DWRPersonalhrService.addAllowedUrl...allowedUrl=" + allowedUrl + ", privilege=" + privilege);
		PhrAllowedUrl url = new PhrAllowedUrl();
		url.setAllowedUrl(allowedUrl);
		url.setPrivilege(privilege);
		url.setDescription(description);
		url.setCreator(Context.getAuthenticatedUser());
		url.setDateCreated(new Date());
		PersonalhrUtil.getService().getAllowedUrlDao().savePhrAllowedUrl(url);
	}
	
    public void addPhrPrivilege(String privilege, String requiredRole, String description) {
        log.debug("Calling DWRPersonalhrService.addPhrPrivilege...requiredRole=" + requiredRole + ", privilege=" + privilege);
        PhrSecurityRule rule = new PhrSecurityRule();
        rule.setPrivilege(privilege);
        rule.setRequiredRole(requiredRole);
        rule.setDescription(description);
        rule.setCreator(Context.getAuthenticatedUser());
        rule.setDateCreated(new Date());
        PersonalhrUtil.getService().getSecurityRuleDao().savePhrSecurityRule(rule);
    }	
}
