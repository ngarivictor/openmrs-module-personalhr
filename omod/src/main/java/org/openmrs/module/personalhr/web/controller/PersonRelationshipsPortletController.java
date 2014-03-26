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
package org.openmrs.module.personalhr.web.controller;

import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.personalhr.PersonalhrUtil;
import org.openmrs.module.personalhr.model.PhrSharingToken;
import org.openmrs.module.personalhr.service.PhrSharingTokenService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public class PersonRelationshipsPortletController extends PortletController {
    
    /**
     * @see org.openmrs.web.controller.PortletController#populateModel(javax.servlet.http.HttpServletRequest,
     *      java.util.Map)
     */
    @Override
    protected void populateModel(final HttpServletRequest request, final Map<String, Object> model) {
        this.log.debug("Entering PersonRelationshipsPortletController:populateModel");
        Integer personId = null;
        Person per = null;
        if (!PersonalhrUtil.isNullOrEmpty(request.getParameter("personId"))) {
            personId = PersonalhrUtil.getParamAsInteger(request.getParameter("personId"));
            per = (personId == null ? null : Context.getPersonService().getPerson(personId));
            
        } else if (Context.isAuthenticated()) {
            per = Context.getAuthenticatedUser().getPerson();
        }
        final List<PhrSharingToken> sharingTokens = Context.getService(PhrSharingTokenService.class).getSharingTokenByPerson(per);
        model.put("phrSharingTokens", sharingTokens);
        this.log.debug("Exiting PersonRelationshipsPortletController:populateModel -> personId|sharingTokens.size = "
                + personId + "|" + sharingTokens == null ? null : sharingTokens.size());
    }
    
}
