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
package org.openmrs.module.exportccd.api;

import org.openhealthtools.mdht.uml.cda.ccd.ContinuityOfCareDocument;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.springframework.transaction.annotation.Transactional;
import org.openmrs.api.db.DAOException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.openmrs.module.exportccd.CCDSectionEntity;
import org.openmrs.module.exportccd.ImportedCCD;
/**
 * This service exposes module's core functionality. It is a Spring managed bean which is configured in moduleApplicationContext.xml.
 * <p>
 * It can be accessed only via Context:<br>
 * <code>
 * Context.getService(PatientSummaryImportService.class).someMethod();
 * </code>
 * 
 * @see org.openmrs.api.context.Context
 */
@Transactional
public interface PatientSummaryImportService extends OpenmrsService {

	public Patient consumeCCD(InputStream is) throws Exception;
	public ImportedCCD getCCD(Patient pat) throws Exception;
	public String importCancerTreatmentSummary(Patient pat) throws Exception;
}