/**
 * Copyright 2007 Regenstrief Institute, Inc. All Rights Reserved.
 */
package org.openmrs.module.exportccd.api.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.common.util.EList;
import org.hibernate.FlushMode;
import org.hibernate.Transaction;
import org.openhealthtools.mdht.uml.cda.AssignedEntity;
import org.openhealthtools.mdht.uml.cda.CDAFactory;
import org.openhealthtools.mdht.uml.cda.ClinicalDocument;
import org.openhealthtools.mdht.uml.cda.Entry;
import org.openhealthtools.mdht.uml.cda.Observation;
import org.openhealthtools.mdht.uml.cda.Participant2;
import org.openhealthtools.mdht.uml.cda.ParticipantRole;
import org.openhealthtools.mdht.uml.cda.Performer2;
import org.openhealthtools.mdht.uml.cda.PlayingEntity;
import org.openhealthtools.mdht.uml.cda.Procedure;
import org.openhealthtools.mdht.uml.cda.StrucDocText;
import org.openhealthtools.mdht.uml.cda.ccd.CCDFactory;
import org.openhealthtools.mdht.uml.cda.ccd.CCDPackage;
import org.openhealthtools.mdht.uml.cda.ccd.ContinuityOfCareDocument;
import org.openhealthtools.mdht.uml.cda.ccd.EncountersSection;
import org.openhealthtools.mdht.uml.cda.ccd.MedicationActivity;
import org.openhealthtools.mdht.uml.cda.ccd.ProblemAct;
import org.openhealthtools.mdht.uml.cda.ccd.ResultOrganizer;
import org.openhealthtools.mdht.uml.cda.util.CDAUtil;
import org.openhealthtools.mdht.uml.hl7.datatypes.CD;
import org.openhealthtools.mdht.uml.hl7.datatypes.CE;
import org.openhealthtools.mdht.uml.hl7.datatypes.DatatypesFactory;
import org.openhealthtools.mdht.uml.hl7.datatypes.ED;
import org.openhealthtools.mdht.uml.hl7.datatypes.II;
import org.openhealthtools.mdht.uml.hl7.datatypes.IVL_TS;
import org.openhealthtools.mdht.uml.hl7.datatypes.PN;
import org.openhealthtools.mdht.uml.hl7.datatypes.ST;
import org.openhealthtools.mdht.uml.hl7.vocab.ActClass;
import org.openhealthtools.mdht.uml.hl7.vocab.EntityClassRoot;
import org.openhealthtools.mdht.uml.hl7.vocab.NullFlavor;
import org.openhealthtools.mdht.uml.hl7.vocab.ParticipationType;
import org.openhealthtools.mdht.uml.hl7.vocab.RoleClassRoot;
import org.openhealthtools.mdht.uml.hl7.vocab.x_ActRelationshipEntry;
import org.openhealthtools.mdht.uml.hl7.vocab.x_DocumentEncounterMood;
import org.openhealthtools.mdht.uml.cda.PatientRole;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptName;
import org.openmrs.ConceptSource;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.exportccd.ImportedCCD;
import org.openmrs.module.exportccd.api.PatientSummaryImportService;
import org.openmrs.module.exportccd.api.db.ImportedCCDDAO;
import org.openmrs.module.exportccd.api.db.hibernate.*;

/**
 * Class to implement processing CCD and updating patient information in OpenMRS database
 * Need to be called by OpenMRS administrator only with concept creation privilege 
 * 
 * @author hxiao
 */

public class PatientSummaryImportServiceImpl extends BaseOpenmrsService implements PatientSummaryImportService 
{   
	static final String ROOT_SSN = "2.16.840.1.113883.4.1";
	static final String ATTRIBUTE_NAME_SSN = "Social Security Number";
	static final String ATTRIBUTE_NAME_RACE = "Race Code";
	static final String ATTRIBUTE_NAME_TELEPHONE = "Telephone";
	static final String ATTRIBUTE_NAME_MARRIED = "Marrital Status";

	static final String CANCER_TREATMENT_SUMMARY_ENCOUNTER = "CANCER TREATMENT SUMMARY";
	static final String CANCER_TREATMENT_CHEMOTHERAPY_ENCOUNTER = "CANCER TREATMENT - CHEMOTHERAPY";
	static final String CANCER_TREATMENT_RADIATION_ENCOUNTER = "CANCER TREATMENT - RADIATION";
	static final String CANCER_TREATMENT_SURGERY_ENCOUNTER = "CANCER TREATMENT - SURGERY";
	static final String CANCER_TREATMENT_SUMMARY_FORM = "Cancer Treatment Summary";
	static final String CANCER_TREATMENT_CHEMOTHERAPY_FORM = "CANCER TREATMENT - CHEMOTHERAPY";
	static final String CANCER_TREATMENT_RADIATION_FORM = "CANCER TREATMENT - RADIATION";
	static final String CANCER_TREATMENT_SURGERY_FORM = "CANCER TREATMENT - SURGERY";

    private static Log log = LogFactory.getLog(PatientSummaryImportServiceImpl.class);
    final SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd");
	private ImportedCCDDAO dao;
	private Encounter ccdEncounter = null;
    
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss.SSSZZZZZ");
    static final String  diagnosisConcept = "DIAGNOSIS ADDED";
    static final String  problemConcept = "PROBLEM ADDED";
    static final String  procedureConcept = "PROCEDURE ADDED" ;
    static final String  procedurePerformer = "PROCEDURE PERFORMER ADDED";
    static final String  resultsConcept = "RESULTS ADDED";
    static final String  drugOrderConcept = "DRUG ORDER ADDED";  

	public Patient consumeCCD(InputStream is) throws Exception {
		CCDPackage.eINSTANCE.eClass();
		//ContinuityOfCareDocument ccdDocument = (ContinuityOfCareDocument) CDAUtil.load(is);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		org.apache.commons.io.IOUtils.copy(is, baos);
		byte[] bytes = baos.toByteArray();
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		
		ClinicalDocument ccdDocument = CDAUtil.load(bais);
		
		//create OpenMRS Patient
		org.openhealthtools.mdht.uml.cda.Patient ohtPatient = ccdDocument.getPatients().get(0);
		Patient omrsPatient = createOrUpdateOmrsPatient(ohtPatient);
		
		User usr = Context.getAuthenticatedUser();

		//save CCD document to database
		bais.reset();		
		saveCCD(bais, omrsPatient);

		//create OpenMRS vital signs Obs
		//createOrUpdateVitals((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);
		
		//create an encounter to hold all observations imported from a given CCD file
		ccdEncounter =createEncounterForCCD(omrsPatient, usr);
		
		//create OpenMRS Encounter
		createOrUpdateEncounters((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);

		//create OpenMRS procedure Obs
		createOrUpdateProcedures((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);

		//create OpenMRS results Obs
		createOrUpdateResults((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);
		
		//create OpenMRS drug and drug_order 
		createOrUpdateMedications((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);

		//create OpenMRS problem Obs
		createOrUpdateProblems((ContinuityOfCareDocument) ccdDocument, omrsPatient, usr);
					
		return omrsPatient;
	}
	       
	private Encounter  createEncounterForCCD(Patient patient, User usr) throws Exception
	{
		List<Encounter> encList = Context.getEncounterService().getEncountersByPatient(patient);
		EncounterType et = Context.getEncounterService().getEncounterType("SUMMARIZATION OF EPISODE NOTE");
		if(et==null) {
			et = new EncounterType();
			et.setCreator(usr);
			et.setDateCreated(new Date());
			et.setName("SUMMARIZATION OF EPISODE NOTE");
			et.setDescription("An encounter to hold all observations imported from a patient's CCD document");
			Context.getEncounterService().saveEncounterType(et);			
		}
		
		for(Encounter enc : encList) {
			if(enc.getEncounterType().getId().equals(et.getId())) {
				return enc;
			}
		}
		
		Encounter enc = new Encounter();
		enc.setPatient(patient);
		enc.setCreator(Context.getAuthenticatedUser());
		enc.setDateCreated(new Date());
		enc.setEncounterDatetime(sdf2.parse("19000101")); //fake date		
		enc.setEncounterType(et);
		//encounter location		
		enc.setLocation(getUnknownLocation());
		//encounter provider
		if(Context.getPersonService().getPeople("Unknown Unknown Unknown", false).isEmpty()) {
				Person pers = new Person();
				PersonName pname = new PersonName();
				pname.setFamilyName("Unknown");
				pname.setGivenName("Unknown");
				pname.setMiddleName("Unknown");
				pers.addName(pname);
				pers.setGender("Unknown");			
				Context.getPersonService().savePerson(pers);				
		}			
		enc.setProvider(Context.getPersonService().getPeople("Unknown Unknown Unknown", false).get(0));
				
		//save encounter
		Context.getEncounterService().saveEncounter(enc);
		
		return enc;
	}	
	
	private void  createOrUpdateEncounters(ContinuityOfCareDocument ccd, Patient patient, User usr)
	{
		if(ccd.getEncountersSection()==null) return;
		EList<org.openhealthtools.mdht.uml.cda.Encounter> eList = ccd.getEncountersSection().getEncounters();
		if(eList.isEmpty()) return;
		for(org.openhealthtools.mdht.uml.cda.Encounter e: eList) {
			try {
					createOrUpdateEncounters(ccd, patient, usr, e);				
			} catch (Exception ex) {
				log.error("Exception detected in createOrUpdateEncounters: " + ex.getMessage(), ex);
			}
		}
	}
	
	private void  createOrUpdateEncounters(ContinuityOfCareDocument ccd, Patient patient, User usr, org.openhealthtools.mdht.uml.cda.Encounter e) throws Exception
	{
		//org.openhealthtools.mdht.uml.cda.Encounter e = ccd.getEncountersSection().getEncounters().get(0);
		if(e==null) {
			return;
		}
		
		String uuid = e.getIds().get(0).getExtension();
		if(uuid == null) {
			return;
		}
		Encounter enc = (Encounter) Context.getEncounterService().getEncounterByUuid(uuid);
		
		if(enc == null) {
			enc = new Encounter();
			enc.setUuid(uuid);
		} else {
			return;
		}
		enc.setPatient(patient);
		enc.setCreator(Context.getAuthenticatedUser());
		enc.setDateCreated(new Date());
		if(e.getEffectiveTime() != null && e.getEffectiveTime().getLow() != null && e.getEffectiveTime().getLow().getValue() != null) {
			enc.setEncounterDatetime(sdf.parse(e.getEffectiveTime().getLow().getValue()));
		} else {
			enc.setEncounterDatetime(sdf2.parse("19000101")); //fake date
		}
		
        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);		
		
		//encounter type
		EncounterType et = Context.getEncounterService().getEncounterType(e.getCode().getDisplayName());
		if(et == null) {
			et = new EncounterType();
			et.setCreator(usr);
			et.setDateCreated(new Date());
			et.setName(e.getCode().getDisplayName());
			et.setDescription(e.getCode().getCodeSystemName() + ":" + e.getCode().getCode());
			Context.getEncounterService().saveEncounterType(et);
		}  
		enc.setEncounterType(et);

		//encounter location		
		if(!e.getParticipants().isEmpty()) {
			String locationName = e.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? e.getParticipants().get(0).getParticipantRole().getPlayingEntity().getNames().get(0).getText() : null;
			if(locationName != null) {
				Location location = Context.getLocationService().getLocation(locationName);
				if(location == null) {
					location = new Location();
					location.setName(locationName);
					location.setDescription(e.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? e.getParticipants().get(0).getParticipantRole().getScopingEntity().getDesc().getText() : null);
					Context.getLocationService().saveLocation(location);
				}
				enc.setLocation(location);
			} else {
				enc.setLocation(getUnknownLocation());
			}
		} else {
			enc.setLocation(getUnknownLocation());			
		}
				
		//encounter participants
		if(e.getPerformers()!=null && !e.getPerformers().isEmpty() && e.getPerformers().get(0).getAssignedEntity() != null) {
			Person pers = new Person();
			PersonName pname = new PersonName();
			PN pn = e.getPerformers().get(0).getAssignedEntity().getAssignedPerson().getNames().get(0);
			pname.setFamilyName(pn.getFamilies().isEmpty() ? "Unknown" :pn.getFamilies().get(0).getText());
			pname.setGivenName(pn.getGivens().isEmpty() ? "Unknown" : pn.getGivens().get(0).getText());
			pname.setMiddleName(pn.getGivens().size()>1 ? pn.getGivens().get(1).getText() : "Unknown");
			pers.addName(pname);
			pers.setGender("Unknown");			
			if(!Context.getPersonService().getPeople(pname.getFullName(), false).isEmpty()) {
				pers = Context.getPersonService().getPeople(pname.getFullName(), false).get(0);
			} else {
				Context.getPersonService().savePerson(pers);				
			}
			enc.setProvider(pers);
		} else {
			if(Context.getPersonService().getPeople("Unknown Unknown Unknown", false).isEmpty()) {
				Person pers = new Person();
				PersonName pname = new PersonName();
				pname.setFamilyName("Unknown");
				pname.setGivenName("Unknown");
				pname.setMiddleName("Unknown");
				pers.addName(pname);
				pers.setGender("Unknown");			
				Context.getPersonService().savePerson(pers);				
			}			
			enc.setProvider(Context.getPersonService().getPeople("Unknown Unknown Unknown", false).get(0));
		}
		
		//save encounter
		Context.getEncounterService().saveEncounter(enc);
		
		//tx.commit();
	}	
	
	private Location getUnknownLocation() {
		Location location = Context.getLocationService().getLocation("Unknown Location");
		if(location == null) {
			location = new Location();
			location.setName("Unknown Location");
			location.setDescription("Unknown Location is used to create encounter from CCD with location data missing");
			Context.getLocationService().saveLocation(location);
		}
		return location;
	}

	private void createOrUpdateMedications(ContinuityOfCareDocument ccd, Patient patient, User usr) {
		if(ccd.getMedicationsSection()==null) return;
		EList<MedicationActivity> maList = ccd.getMedicationsSection().getMedicationActivities();
		if(maList.isEmpty()) return;
		for(MedicationActivity ma : maList) {
			try{
					createOrUpdateMedications(ccd, patient, usr, ma);
			
			} catch (Exception e) {
				log.error("Exception detected in createOrUpdateMedications: " + e.getMessage(), e);
			}
		}
		
	}
	
	private void createOrUpdateMedications(ContinuityOfCareDocument ccd, Patient patient, User usr, MedicationActivity ma) throws Exception {
		//MedicationActivity ma = ccd.getMedicationsSection().getMedicationActivities().get(0);
		if(ma==null) {
			return;
		}
		
		String uuid = ma.getIds().get(0).getExtension();
		if(uuid == null) {
			return;
		}
		DrugOrder order = (DrugOrder) Context.getOrderService().getOrderByUuid(uuid);
		
		if(order == null) {
			order = new DrugOrder(); 
			order.setUuid(uuid);
		}
		
		//set basic info
		Date dt = new Date();
		if(!ma.getEncounters().isEmpty()) {
			order.setEncounter(getOpenmrsEncounter(ma.getEncounters().get(0)));
		} else {
			order.setEncounter(ccdEncounter);
		}
		order.setCreator(usr);
		order.setDateCreated(dt);
		if(ma.getEffectiveTimes() != null && !ma.getEffectiveTimes().isEmpty() && ma.getEffectiveTimes().get(0).getValue() != null) {
			try {
				order.setStartDate(sdf.parse(ma.getEffectiveTimes().get(0).getValue()));
			} catch (Exception e) {
				log.error("Unable to parse date: " + ma.getEffectiveTimes().get(0).getValue(), e);
			}
		}
		order.setPatient(patient);
		//set concept
		order.setConcept(getOpenmrsCodedConceptByName(drugOrderConcept));
		
		//set Drug value	
		String name = ma.getConsumable().getManufacturedProduct().getManufacturedMaterial().getName().getText();
		
		CE codedValue = ma.getConsumable().getManufacturedProduct().getManufacturedMaterial().getCode();
		Concept valueCoded = getOpenmrsConceptByName(codedValue);
		Drug drug = new Drug();
		drug.setConcept(valueCoded);
		drug.setDescription(codedValue.getDisplayName());
		drug.setCreator(usr);
		drug.setDateCreated(dt);
		//drug.setDoseStrength(ma.getDoseQuantity().getValue().doubleValue());
		drug.setName(name);		
		Context.getConceptService().saveDrug(drug);
		order.setDrug(drug);
		order.setOrderType(Context.getOrderService().getOrderTypeByUuid("623e95ff-69b7-11df-b02c-79b20f35dbac"));//"Drug Order"
				
        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);
        Context.getOrderService().saveOrder(order);
        //tx.commit();
	}

	private void createOrUpdateProcedures(ContinuityOfCareDocument ccd, Patient patient, User usr) {
		if(ccd.getProceduresSection()==null) {
			return;
		}
		EList<Procedure> paList = ccd.getProceduresSection().getProcedures();
		if(paList.isEmpty()) return;
		for(Procedure pa : paList) {
			try{
					createOrUpdateProcedures(ccd, patient, usr, pa);				
			} catch (Exception e) {
				log.error("Exception detected in createOrUpdateProcedures: " + e.getMessage(), e);
			}
		}
	}
	
	private void createOrUpdateProcedures(ContinuityOfCareDocument ccd, Patient patient, User usr, Procedure pa)  throws Exception{
		//Procedure pa = ccd.getProceduresSection().getProcedures().get(0);
		if(pa==null) {
			return;
		}
		
		String uuid = pa.getIds().get(0).getExtension();
		if(uuid == null) {
			return;
		}
		Obs obs = Context.getObsService().getObsByUuid(uuid);
		
		if(obs == null) {
		  obs = new Obs();
		  obs.setUuid(uuid);
		} else {
			return;
		}
		
		//set basic info
		Date dt = new Date();
		if(!pa.getEncounters().isEmpty()) {
			obs.setEncounter(getOpenmrsEncounter(pa.getEncounters().get(0)));
		} else {
			obs.setEncounter(ccdEncounter);
		}
		obs.setCreator(usr);
		obs.setPerson(patient);
		obs.setDateCreated(dt);
		obs.setObsDatetime(sdf.parse(pa.getEffectiveTime().getLow().getValue()));
		
		//set concept
		obs.setConcept(getOpenmrsCodedConceptByName(procedureConcept));
		
		//set value
		CD codedValue = pa.getCode();
		//pa.getEntryRelationships().get(0).getObservation().getValues().get(0);
		Concept valueCoded = getOpenmrsConceptByName(codedValue);
		if(valueCoded == null) return;
		obs.setValueCoded(valueCoded);
		
		//procedure location
		String locationName = pa.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? pa.getParticipants().get(0).getParticipantRole().getPlayingEntity().getNames().get(0).getText() : null;
		if(locationName != null) {
			Location location = Context.getLocationService().getLocation(locationName);
			if(location == null) {
				location = new Location();
				location.setName(locationName);
				location.setDescription(pa.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? pa.getParticipants().get(0).getParticipantRole().getScopingEntity().getDesc().getText() : null);
		        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
		        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);		
				Context.getLocationService().saveLocation(location);
				//tx.commit();
			}
			obs.setLocation(location);
		}

		saveOpenmrsObs(obs);		
		
		//Add procedure participants as a separate Obs
		if(pa.getPerformers() != null && !pa.getPerformers().isEmpty()) {
			Obs obsPerformer = new Obs();
			obsPerformer.setCreator(usr);
			obsPerformer.setDateCreated(dt);
			obsPerformer.setObsDatetime(sdf.parse(pa.getEffectiveTime().getLow().getValue()));
			obs.setConcept(getOpenmrsCodedConceptByName(procedurePerformer));			
			PersonName pname = new PersonName();
			PN pn = pa.getPerformers().get(0).getAssignedEntity().getAssignedPerson().getNames().get(0);
			pname.setFamilyName(pn.getFamilies().isEmpty() ? null :pn.getFamilies().get(0).getText());
			pname.setGivenName(pn.getGivens().isEmpty() ? null : pn.getGivens().get(0).getText());
			//pname.setMiddleName(pn.getGivens().get(1).getText())
			obs.setValueAsString(pname.getFullName());
			
			saveOpenmrsObs(obsPerformer);		
		}
	}

	private void createOrUpdateProblems(ContinuityOfCareDocument ccd, Patient patient, User usr) {
		if(ccd.getProblemSection()==null) return;
		EList<ProblemAct> paList = ccd.getProblemSection().getProblemActs();
		if(paList.isEmpty()) return;
		for(ProblemAct pa : paList) {
			try {
					createOrUpdateProblems(ccd, patient, usr, pa);
				
			} catch (Exception e) {
				log.error("Exception detected in createOrUpdateProblems: " + e.getMessage(), e);
			}
		}
	}
	
	private void createOrUpdateProblems(ContinuityOfCareDocument ccd, Patient patient, User usr, ProblemAct pa) throws Exception {
		//ProblemAct pa = ccd.getProblemSection().getProblemActs().get(0);
		if(pa==null) {
			return;
		}
		
		String uuid = pa.getObservations().get(0).getIds().get(0).getExtension();
		if(uuid == null) {
			return;
		}
		Obs obs = Context.getObsService().getObsByUuid(uuid);
		
		if(obs == null) {
		  obs = new Obs();
          //set uuid
		  obs.setUuid(uuid);
		} else {
			return;
		}
		
		//set basic info
		if(!pa.getEncounters().isEmpty()) {
			obs.setEncounter(getOpenmrsEncounter(pa.getEncounters().get(0)));
		} else {
			obs.setEncounter(ccdEncounter);
		}
		obs.setCreator(usr);
		obs.setDateCreated(new Date());
		obs.setPerson(patient);
		if(pa.getEffectiveTime() != null && pa.getEffectiveTime().getLow().getValue() != null) {
			obs.setObsDatetime(sdf.parse(pa.getEffectiveTime().getLow().getValue()));
		}
		
		//set concept
		obs.setConcept(getOpenmrsCodedConceptByName(problemConcept));
		
		//set value
		Observation obsv = pa.getObservations().get(0);
		
		CD codedValue = (CD) obsv.getValues().get(0);
		//pa.getEntryRelationships().get(0).getObservation().getValues().get(0);
		Concept valueCoded = getOpenmrsConceptByName(codedValue);
		obs.setValueCoded(valueCoded);		
		obs.setObsDatetime(sdf.parse(obsv.getEffectiveTime().getLow().getValue()));
		
		
		saveOpenmrsObs(obs);		
	}
	
	private void createOrUpdateResults(ContinuityOfCareDocument ccd, Patient patient, User usr) {
		if(ccd.getResultsSection()==null) return;
		EList<ResultOrganizer> roList = ccd.getResultsSection().getResultOrganizers();
		if(roList.isEmpty()) return;
		try {
			for(ResultOrganizer ro : roList) {
				createOrUpdateResults(ccd, patient, usr, ro);
			}
		} catch (Exception e) {
			log.error("Exception detected in createOrUpdateResults: " + e.getMessage(), e);
		}
	}
	
	private void createOrUpdateResults(ContinuityOfCareDocument ccd, Patient patient, User usr, ResultOrganizer ro) throws Exception {
		//ProblemAct pa = ccd.getProblemSection().getProblemActs().get(0);
		if(ro==null) {
			return;
		}
		
		Observation obsv = ro.getObservations().get(0);
		if(obsv == null) return;
		
		String uuid = obsv.getIds().get(0).getExtension();
		if(uuid == null) {
			return;
		}
		Obs obs = Context.getObsService().getObsByUuid(uuid);
		
		if(obs == null) {
		  obs = new Obs();
          //set uuid
		  obs.setUuid(uuid);
		}
		
		//set basic info
		if(!ro.getEncounters().isEmpty()) {
			obs.setEncounter(getOpenmrsEncounter(ro.getEncounters().get(0)));
		} else {
			obs.setEncounter(ccdEncounter);
		}
		obs.setCreator(usr);
		obs.setDateCreated(new Date());
		obs.setPerson(patient);
		obs.setObsDatetime(sdf.parse(obsv.getEffectiveTime().getValue()));
		
		//set concept
		obs.setConcept(getOpenmrsCodedConceptByName(resultsConcept)); //obs.setConcept(getOpenmrsConceptByName(ro.getCode()));
		
		//set value
		CD codedValue = obsv.getCode();
		//pa.getEntryRelationships().get(0).getObservation().getValues().get(0);
		Concept valueCoded = getOpenmrsConceptByName(codedValue);
		obs.setValueCoded(valueCoded);
				
		saveOpenmrsObs(obs);		
	}	

	private void saveOpenmrsObs(Obs obs) {
        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);		
		Context.getObsService().saveObs(obs, "Imported from CCD");		
		//tx.commit();
	}

	private Encounter  getOpenmrsEncounter(org.openhealthtools.mdht.uml.cda.Encounter e) throws Exception
	{
		Encounter enc = new Encounter();
		//enc.setPatient(patient);
		//enc.setCreator(usr);
		enc.setDateCreated(new Date());
		enc.setEncounterDatetime(sdf.parse(e.getEffectiveTime().getLow().getValue()));
		enc.setUuid(e.getIds().get(0).getRoot()+"."+e.getIds().get(0).getExtension());
		
		//encounter type
		EncounterType et = new EncounterType();
		//et.setCreator(usr);
		et.setDateCreated(new Date());
		et.setName(e.getCode().getDisplayName());
		et.setDescription(e.getCode().getCodeSystemName() + " code: " + e.getCode().getCode());		
		enc.setEncounterType(et);

		//encounter location
		Location location = new Location();
		location.setName(e.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? e.getParticipants().get(0).getParticipantRole().getPlayingEntity().getNames().get(0).getText() : null);
		location.setDescription(e.getParticipants().get(0).getTypeCode().getName().equals("LOC") ? e.getParticipants().get(0).getParticipantRole().getScopingEntity().getDesc().getText() : null);		
		enc.setLocation(location);
		
		//encounter participants
		Person pers = new Person();
		PersonName pname = new PersonName();
		PN pn = e.getPerformers().get(0).getAssignedEntity().getAssignedPerson().getNames().get(0);
		pname.setFamilyName(pn.getFamilies().isEmpty() ? null :pn.getFamilies().get(0).getText());
		pname.setGivenName(pn.getGivens().isEmpty() ? null : pn.getGivens().get(0).getText());
		//pname.setMiddleName(pn.getGivens().get(1).getText())
		pers.addName(pname);
		enc.setProvider(pers);		
		
		return enc;
	}
	
	private Concept getOpenmrsCodedConceptByName(String displayName) {
		ConceptService cs = Context.getConceptService();
		Concept c = cs.getConceptByName(displayName);
		
		if(c==null) {
			c = new Concept();				
			c.setCreator(Context.getAuthenticatedUser());
			ConceptName cn = new ConceptName();
			cn.setName(displayName);
			cn.setLocale(Context.getLocale());
			cn.setCreator(Context.getAuthenticatedUser());
			cn.setConcept(c);
			//cn.setId(cs.get)
			c.addName(cn);
			c.setFullySpecifiedName(cn);
			//c.setShortName(cn);
			//c.setPreferredName(cn);
			c.setDateCreated(new Date());
			c.setDatatype(cs.getConceptDatatypeByName("Coded"));
			c.setUuid(UUID.randomUUID().toString());
			//c.setId(cs.getMaxConceptId() + 1);
			c.setConceptClass(cs.getConceptClassByName(getOpenmrsConceptClassName(displayName, null)));
	        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
	        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);
	        
			cs.saveConcept(c);
			//tx.commit();
			
		}
		
		return c;
	}	
	
	private Concept getOpenmrsConceptByName(CD codedValue) {
		String displayName = codedValue.getDisplayName();
		if(displayName == null) return null;
		
		String code = codedValue.getCode();
		String codeSystem = codedValue.getCodeSystem();
		String codeSystemName = codedValue.getCodeSystemName();
		ConceptService cs = Context.getConceptService();
		Concept c = cs.getConceptByMapping(codeSystem, code);
        //Transaction tx = ((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().beginTransaction();
        //((org.openmrs.module.exportccd.api.db.hibernate.HibernateImportedCCDDAO) dao).getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT);		
		
		if(c==null) {
			ConceptSource csr = cs.getConceptSourceByName(codeSystemName); 
			if(csr==null) {
				csr = new ConceptSource();
				csr.setCreator(Context.getAuthenticatedUser());
				csr.setHl7Code(codeSystem);
				csr.setName(codeSystemName);
				csr.setDateCreated(new Date());
				csr.setDescription(codeSystemName);
				cs.saveConceptSource(csr);
				//csr.setUuid()
				//log.error("Concept source is not defined: " + codeSystemName);
				//return null;
			}
			c=cs.getConcept(displayName);
			if(c==null) {
				c = new Concept();				
				c.setConceptClass(cs.getConceptClassByName(getOpenmrsConceptClassName(displayName, codeSystemName)));
				c.setCreator(Context.getAuthenticatedUser());
				ConceptName cn = new ConceptName();
				cn.setName(displayName);
				cn.setLocale(Context.getLocale());
				cn.setCreator(Context.getAuthenticatedUser());
				c.addName(cn);
				c.setFullySpecifiedName(cn);
				//c.setPreferredName(cn);
				c.setDateCreated(new Date());
				c.setDatatype(cs.getConceptDatatypeByName("N/A"));
				c.setUuid(UUID.randomUUID().toString());
				ConceptMap cm = new ConceptMap();
				cm.setSource(csr);
				cm.setConcept(c);
				cm.setSourceCode(code);
				cm.setCreator(Context.getAuthenticatedUser());
				//ConceptReferenceTerm crt = new ConceptReferenceTerm();
				//crt.setConceptSource(csr);
				//crt.setCode(code);			
				//cm.setConceptReferenceTerm(crt);			
				c.addConceptMapping(cm);		
				cs.saveConcept(c);
			}
		}
		//tx.commit();
		return c;
	}

	private static String getOpenmrsConceptClassName(String displayName,
			String codeSystemName) {
		if( codeSystemName!= null && codeSystemName.toLowerCase().contains("snomed")) {
			if(displayName.toLowerCase().contains("finding")) {
				return "Finding";
			} else {
				return "Diagnosis";
			}
		} else if( codeSystemName!= null && (codeSystemName.toLowerCase().contains("rxnorm") || codeSystemName.toLowerCase().contains("ndc"))) {
				return "Drug";
		} else if(displayName.toLowerCase().contains("procedure")) {
			return "Procedure";
		} else if(displayName.toLowerCase().contains("Diagnosis")) {
			return "Diagnosis";
		} else if(displayName.toLowerCase().contains("problem")) {
			return "Problem";
		} else if(displayName.toLowerCase().contains("result")) {
			return "Finding";
		} else if(displayName.toLowerCase().contains("drug")) {
			return "Drug";
		} else {
			return "Misc";
		}
	}
	
    private void saveCCD(InputStream is, Patient omrsPatient) throws Exception {
    	StringBuilder sb = new StringBuilder();
    	try{
        	//read it with BufferedReader
        	BufferedReader br
            	= new BufferedReader(
            		new InputStreamReader(is));
     
     
        	String line;
        	while ((line = br.readLine()) != null) {
        		sb.append(line);
        	}   
    	} catch(IOException e) {
    		log.error("Failed to save CCD to database due to " + e.getMessage(), e);
    		throw e;
    	}
    	ImportedCCD ccd = new ImportedCCD();
    	ccd.setImportedFor(omrsPatient);
    	ccd.setCcdImported(sb.toString());
    	ccd.setImportedBy(Context.getAuthenticatedUser());
    	ccd.setDateImported(new Date());
		dao.saveImportedCCD(ccd);		
	}

	protected Patient createOrUpdateOmrsPatient(org.openhealthtools.mdht.uml.cda.Patient ohtPatient) throws Exception {		    	
		Patient inputPatient = convertToOmrsPatient(ohtPatient);		
		Patient exsitingPatient = findPatientFromDatabase(inputPatient);		
		if (exsitingPatient == null) {
			exsitingPatient = savePatient(inputPatient);
		} else {
			//updatePatient(exsitingPatient, inputPatient);
		}
					
		return exsitingPatient;
    }
    

	
	private Patient convertToOmrsPatient(org.openhealthtools.mdht.uml.cda.Patient ohtPatient) {
		Patient pat = new Patient();
	    PersonService personService = Context.getPersonService();
	    //PatientService patientService = Context.getPatientService();
		
		//set patient names		
		String familyName = (ohtPatient.getNames().isEmpty() || ohtPatient.getNames().get(0).getFamilies().isEmpty())? null : ohtPatient.getNames().get(0).getFamilies().get(0).getText();		
		String givenName = (ohtPatient.getNames().isEmpty() || ohtPatient.getNames().get(0).getGivens().isEmpty())? null : ohtPatient.getNames().get(0).getGivens().get(0).getText();
		String middleName = (ohtPatient.getNames().isEmpty() || ohtPatient.getNames().get(0).getGivens().size() <= 1) ? null : ohtPatient.getNames().get(0).getGivens().get(1).getText();
		PersonName pn = new PersonName();
		pn.setFamilyName(familyName);
		pn.setGivenName(givenName);
		pn.setMiddleName(middleName);
		pat.addName(pn);
		
		//set patient sex
		String sex = ohtPatient.getAdministrativeGenderCode().getDisplayName();
		pat.setGender(sex);
		
		PatientRole patRole = (PatientRole) ohtPatient.eContainer();

		//set patient SSN
		PersonAttributeType ssnType = personService.getPersonAttributeTypeByName(ATTRIBUTE_NAME_SSN);
        if(ssnType != null && !ssnType.isRetired()) {		
			EList<II> ids = patRole.getIds();
			String ssn = null;
			for (II id : ids) {
				if(ROOT_SSN.equals(id.getRoot())) {
					ssn = id.getExtension();
				}
			}
			PersonAttribute ssnAttr = new PersonAttribute();
			ssnAttr.setAttributeType(ssnType);
			ssnAttr.setValue(ssn);
			pat.addAttribute(ssnAttr);
        }
		//set patient DOB
		String dobStr = ohtPatient.getBirthTime().getValue();	
		try{
			Date birthdate = sdf2.parse(dobStr);
			pat.setBirthdate(birthdate);
		} catch(ParseException e) {
			log.error("Unable to parse date of birth string: " + dobStr, e);
		}
		
		//set patient Home Address
		String street1 = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getStreetAddressLines().size()<1 )? null : patRole.getAddrs().get(0).getStreetAddressLines().get(0).getText();
		String street2 = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getStreetAddressLines().size() <= 1)? null : patRole.getAddrs().get(0).getStreetAddressLines().get(1).getText();
		String city = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getCities()==null || patRole.getAddrs().get(0).getCities().isEmpty())? null : patRole.getAddrs().get(0).getCities().get(0).getText();
		String state = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getStates()==null || patRole.getAddrs().get(0).getStates().isEmpty())? null : patRole.getAddrs().get(0).getStates().get(0).getText();
		String country = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getCounties() == null || patRole.getAddrs().get(0).getCounties().isEmpty())? null : patRole.getAddrs().get(0).getCounties().get(0).getText();
		String zip = (patRole.getAddrs()==null || patRole.getAddrs().isEmpty() || patRole.getAddrs().get(0).getPostalCodes()==null || patRole.getAddrs().get(0).getPostalCodes().isEmpty())? null : patRole.getAddrs().get(0).getPostalCodes().get(0).getText();
        PersonAddress address = new PersonAddress();
        User ncdUser = Context.getUserContext().getAuthenticatedUser();
        Date now = new Date();
        address.setCreator(ncdUser);
        address.setDateCreated(now);
        address.setAddress1(street1);
        address.setAddress2(street2);
        address.setCityVillage(city);
        address.setCountry(country);
        address.setPostalCode(zip);
        address.setStateProvince(state);        
        address.setPerson(pat);
        pat.addAddress(address);	
        
		//get patient race
        PersonAttributeType raceType = personService.getPersonAttributeTypeByName(ATTRIBUTE_NAME_RACE);
        if(raceType != null && !raceType.isRetired()) {
			String race = ohtPatient.getRaceCode() == null? null : ohtPatient.getRaceCode().getDisplayName();
			PersonAttribute raceAttr = new PersonAttribute();
			raceAttr.setAttributeType(raceType);
			raceAttr.setValue(race);
			pat.addAttribute(raceAttr);
        }
		
		//set patient telephone numbers
        PersonAttributeType phoneType = personService.getPersonAttributeTypeByName(ATTRIBUTE_NAME_TELEPHONE);
        if(phoneType != null && !phoneType.isRetired()) {
			String telephone = (patRole.getTelecoms()==null || patRole.getTelecoms().isEmpty()) ? null : patRole.getTelecoms().get(0).getValue();
			PersonAttribute phoneAttr = new PersonAttribute();
			phoneAttr.setAttributeType(phoneType);
			phoneAttr.setValue(telephone);
			pat.addAttribute(phoneAttr);
        }
        
		//set patient marital status
        PersonAttributeType marryType = personService.getPersonAttributeTypeByName(ATTRIBUTE_NAME_MARRIED);
        if(marryType != null && !marryType.isRetired()) {
			String marrital = ohtPatient.getMaritalStatusCode().getDisplayName();
			PersonAttribute marriedAttr = new PersonAttribute();
			marriedAttr.setAttributeType(marryType);
			marriedAttr.setValue(marrital);
			pat.addAttribute(marriedAttr);
		}
			
		return pat;
	}
    
	private Patient findPatientFromDatabase(Patient inputPatient) {
		PatientService patientService = Context.getPatientService();
		
		//Patient pat = patientService.getPatientByExample(inputPtient); 
		List<Patient> pats = patientService.getPatients(inputPatient.getGivenName()+ " " + inputPatient.getFamilyName());
		
		Patient patFound = null;
		String inputPatString = inputPatient.getGivenName()+ " " + inputPatient.getFamilyName();// + " " + inputPatient.getMiddleName() + " " + inputPatient.getGender(); // +  " " + inputPatient.getBirthdate().getTime(); 
		for(Patient pat : pats) {
			String patString = pat.getGivenName()+ " " + pat.getFamilyName();// + " " + inputPatient.getMiddleName() +  " " + pat.getGender(); // +  " " + pat.getBirthdate().getTime();
			if(inputPatString.equalsIgnoreCase(patString)) {
				patFound = pat;
				break;
			}
		}
		
	    log.debug("found patient=" + patFound);
		return patFound;
	}
	
    private Patient updatePatient(Patient existingPatient, Patient inputPatient) {
		existingPatient.setAddresses(inputPatient.getAddresses());
		existingPatient.setAttributes(inputPatient.getAttributes());
		existingPatient.setChangedBy(Context.getAuthenticatedUser());
		existingPatient.setDateChanged(new Date());
		//Patient pat = patientService.savePatient(existingPatient);
	    log.debug("updated patient=" + existingPatient);
		return existingPatient;
	}


   private Patient savePatient(Patient inputPatient) {

       //PersonService personService = Context.getPersonService();
       PatientService patientService = Context.getPatientService();

       // Find the "OpenMRS Identification Number" type
       PatientIdentifierType patientIdType = 
           patientService.getPatientIdentifierTypeByName("OpenMRS Identification Number");

       // Find the "unknown location" location
       Location unknownLocation = Context.getLocationService().getLocation(1);
       
       // Add it. The mrngen module wraps PatientService.savePatient
       // and replaces the identifier value.
       PatientIdentifier patientId = new PatientIdentifier();
       User ncdUser = Context.getUserContext().getAuthenticatedUser();
       Date now = new Date();
       patientId.setCreator(ncdUser);
       patientId.setDateCreated(now);
       patientId.setIdentifierType(patientIdType);
       patientId.setIdentifier("TEMPID_WILL_BE_REPLACED");
       patientId.setLocation(unknownLocation);
       inputPatient.addIdentifier(patientId);
       
       Patient pat = patientService.savePatient(inputPatient);
       
       log.debug("saved patient=" + pat);
       return pat;
   }

	public ImportedCCDDAO getDao() {
		return dao;
	}
	
	public void setDao(ImportedCCDDAO dao) {
		this.dao = dao;
	}

	@Override
	public ImportedCCD getCCD(Patient pat) throws Exception {
		// TODO Auto-generated method stub
		return dao.getImportedCCD(pat);
	}

	@Override
	public String importCancerTreatmentSummary(Patient pat) throws Exception {
		String status = "";
		
		// find latest cancer treatment summary encounter
		List<Encounter> encList = Context.getEncounterService().getEncountersByPatient(pat);
		Encounter encSummary = null;
		Date encSummaryDate = null;
		for(Encounter enc : encList) {
			if (this.CANCER_TREATMENT_SUMMARY_ENCOUNTER.equals(enc.getEncounterType())) {
			  if(encSummaryDate==null || encSummaryDate.before(enc.getEncounterDatetime())) {
				encSummary = enc;
				encSummaryDate = enc.getEncounterDatetime();
			  }
			}
		}
		
		if(encSummary != null) {
			status = "Previously entered cancer treatment summary is found: " + sdf.format(encSummaryDate);
		}
		
		//import cancer type, cancer stage and cancer diagnosis date
		status = status + "\n" + importTreatmentHistory(pat, CANCER_TREATMENT_SUMMARY_ENCOUNTER);
		
		//import surgery type and surgery date
		status = status  + "\n" +  importTreatmentHistory(pat, CANCER_TREATMENT_SURGERY_ENCOUNTER);
		
		//import chemotherapy medication used and dates
		status = status  + "\n" +  importTreatmentHistory(pat, CANCER_TREATMENT_CHEMOTHERAPY_ENCOUNTER);
		
		//import radiation type and dates
		status = status  + "\n" +  importTreatmentHistory(pat, CANCER_TREATMENT_RADIATION_ENCOUNTER);
		
		return status;
	}

	private String importTreatmentHistory(Patient pat, String historyType) throws Exception {
		// create cancer treatment summary encounter
		Encounter enc = createOpenmrsEncounter(pat, historyType, null);
		
		String status = "Importing " + historyType + ": ";
		
		// find cancer type, cancer stage and cancer diagnosis date
		// and add them to cancer treatment summary encounter
		if(CANCER_TREATMENT_SUMMARY_ENCOUNTER.equals(historyType)) {
			status = status + importObs(pat, enc, "CANCER TYPE");
			status = status + importObs(pat, enc, "CANCER STAGE");
			status = status + importObs(pat, enc, "CANCER DIAGNOSIS DATE");
		} else if(CANCER_TREATMENT_SURGERY_ENCOUNTER.equals(historyType)) {
			status = status + importObs(pat, enc, "SURGERY TYPE");
			status = status + importObs(pat, enc, "SURGERY DATE");			
		} else if(CANCER_TREATMENT_CHEMOTHERAPY_ENCOUNTER.equals(historyType)) {
			status = status + importObs(pat, enc, "CHEMOTHERAPY MEDICATIONS USED");
			status = status + importObs(pat, enc, "CHEMOTHERAPY START DATE");			
			status = status + importObs(pat, enc, "CHEMOTHERAPY FINISH DATE");			
		} else if(CANCER_TREATMENT_RADIATION_ENCOUNTER.equals(historyType)) {
			status = status + importObs(pat, enc, "RADIATION TYPE");
			status = status + importObs(pat, enc, "RADIATION DATE");						
		}
		return status + ";    ";
	}  
	
	private String importObs(Patient pat, Encounter enc, String conceptName) {
		String status = "";
		
		Concept concept = Context.getConceptService().getConcept(conceptName);
		
		if(concept != null) {
			List<Obs> obsList = Context.getObsService().getObservationsByPersonAndConcept(pat, concept);
			if(obsList != null && !obsList.isEmpty()) {
				enc.addObs(obsList.get(0));
				status = conceptName + "; ";
			}			
		}
		
		return status;
	}

	private Encounter  createOpenmrsEncounter(Patient pat, String encounterTypeName, Date encounterDate) throws Exception
	{
		Encounter enc = new Encounter();
		enc.setPatient(pat);
		enc.setCreator(Context.getAuthenticatedUser());
		enc.setDateCreated(new Date());
		enc.setEncounterDatetime(encounterDate);
		//enc.setUuid(uuid);
		
		//encounter type
		EncounterType et = Context.getEncounterService().getEncounterType(encounterTypeName);
		enc.setEncounterType(et);

		//encounter location
		Location location = Context.getLocationService().getDefaultLocation();
		enc.setLocation(location);
		
		return enc;
	}	
}
