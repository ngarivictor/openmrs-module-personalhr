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
package org.openmrs.module.personalhr.web.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.personalhr.PersonalhrUtil;
import org.openmrs.web.user.UserProperties;

/**
 * This filter checks if an authenticated user has been flagged by the admin to change his password
 * on first/subsequent login. It will intercept any requests made to a *.html or a *.form to force
 * the user to change his password.
 */
public class PhrSecurityFilter implements Filter {
    
    protected final Log log = LogFactory.getLog(getClass());
    
    private String excludeURL;
    
    private String loginForm;
    
    private FilterConfig config;
    
    private String[] excludedURLs;
    
    /**
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
    }
    
    /**
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,                                                                                       ServletException {
        String requestURI = ((HttpServletRequest) request).getRequestURI();
        log.debug("Entering PhrSecurityFilter.doFilter: " + requestURI);
        
        if (Context.isAuthenticated() && shouldCheckAccessToUrl(requestURI)) {
            Integer patId = PersonalhrUtil.getParamAsInteger("patientId", requestURI); 
            
            Patient pat = patId==null? null : Context.getPatientService().getPatient(patId);
                        
            Integer perId = PersonalhrUtil.getParamAsInteger("personId", requestURI);
            Person per = perId==null? null : Context.getPersonService().getPerson(perId);           
            
            if(!PersonalhrUtil.getService().isUrlAllowed(requestURI, pat, per, Context.getAuthenticatedUser())) {
                config.getServletContext().getRequestDispatcher(loginForm).forward(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
    

    /**
     * Method to check if the request url is an excluded url.
     * 
     * @param requestURI
     * @param excludeURL
     * @return
     */
    private boolean shouldCheckAccessToUrl(String requestURI) {        
        for (String url : excludedURLs) {
            if (requestURI.contains(url)) {
                log.debug("shouldCheckAccessToUrl: " + false + " for " + requestURI + " due to " + url);
                return false;
            }
        }
        
        log.debug("shouldCheckAccessToUrl: " + true + " for " + requestURI);
        return true;
    }
    
    /**
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig config) throws ServletException {
        this.config = config;
        excludeURL = config.getInitParameter("excludeURL");
        excludedURLs = excludeURL.split(",");
        loginForm = config.getInitParameter("loginForm");
    }
    
}