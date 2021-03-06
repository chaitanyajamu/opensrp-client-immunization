package org.smartregister.immunization.service.intent;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.immunization.ImmunizationLibrary;
import org.smartregister.immunization.domain.Vaccine;
import org.smartregister.immunization.domain.jsonmapping.VaccineGroup;
import org.smartregister.immunization.repository.VaccineRepository;
import org.smartregister.immunization.util.IMConstants;
import org.smartregister.immunization.util.JsonFormUtils;
import org.smartregister.immunization.util.VaccinatorUtils;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Created by keyman on 3/01/2017.
 */
public class VaccineIntentService extends IntentService {
    private static final String TAG = VaccineIntentService.class.getCanonicalName();
    public static final String EVENT_TYPE = "Vaccination";
    public static final String EVENT_TYPE_OUT_OF_CATCHMENT = "Out of Area Service - Vaccination";
    public static final String ENTITY_TYPE = "vaccination";
    private VaccineRepository vaccineRepository;
    private List<VaccineGroup> availableVaccines;

    public VaccineIntentService() {
        super("VaccineService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (availableVaccines == null) {
            availableVaccines = VaccinatorUtils.getSupportedVaccines(getBaseContext());
        }
        final String entityId = "1410AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        final String calId = "1418AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        final String dateDataType = "date";
        final String calculationDataType = "calculate";
        final String concept = "concept";

        try {
            List<Vaccine> vaccines = vaccineRepository.findUnSyncedBeforeTime(IMConstants.VACCINE_SYNC_TIME);
            if (!vaccines.isEmpty()) {
                for (Vaccine vaccine : vaccines) {

                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    String formattedDate = simpleDateFormat.format(vaccine.getDate());

                    JSONArray jsonArray = new JSONArray();

                    String vaccineName = vaccine.getName().replace(" ", "_");

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(JsonFormUtils.KEY, vaccineName);
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY, concept);
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY_ID, entityId);
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY_PARENT, getParentId(vaccine.getName()));
                    jsonObject.put(JsonFormUtils.OPENMRS_DATA_TYPE, dateDataType);
                    jsonObject.put(JsonFormUtils.VALUE, formattedDate);
                    jsonArray.put(jsonObject);

                    if (vaccine.getCalculation() == null || vaccine.getCalculation() < 0) {
                        vaccine.setCalculation(1);
                    }

                    jsonObject = new JSONObject();
                    jsonObject.put(JsonFormUtils.KEY, vaccineName + "_dose");
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY, concept);
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY_ID, calId);
                    jsonObject.put(JsonFormUtils.OPENMRS_ENTITY_PARENT, getParentId(vaccine.getName()));
                    jsonObject.put(JsonFormUtils.OPENMRS_DATA_TYPE, calculationDataType);
                    jsonObject.put(JsonFormUtils.VALUE, vaccine.getCalculation());
                    jsonArray.put(jsonObject);

                    JsonFormUtils.createVaccineEvent(getApplicationContext(), vaccine, EVENT_TYPE, ENTITY_TYPE, jsonArray);
                    //log out of catchment service since this is required in some of the hia2 report indicators
                    if (vaccine.getBaseEntityId() == null || vaccine.getBaseEntityId().isEmpty()) {
                        JsonFormUtils.createVaccineEvent(getApplicationContext(), vaccine, EVENT_TYPE_OUT_OF_CATCHMENT, ENTITY_TYPE, jsonArray);

                    }
                    vaccineRepository.close(vaccine.getId());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private String getParentId(String name) {
        String parentEntityId = "";
        for (VaccineGroup vaccineGroup : availableVaccines) {
            for (org.smartregister.immunization.domain.jsonmapping.Vaccine vaccine : vaccineGroup.vaccines) {
                if (StringUtils.containsIgnoreCase(vaccine.name, name)) {
                    parentEntityId = vaccine.openmrs_date.parent_entity;
                    if (parentEntityId.contains("/")) {
                        String[] parentEntityArray = parentEntityId.split("/");
                        if (StringUtils.containsIgnoreCase(name, "measles")) {
                            parentEntityId = parentEntityArray[0];
                        } else if (StringUtils.containsIgnoreCase(name, "mr")) {
                            parentEntityId = parentEntityArray[1];
                        }
                        return parentEntityId;
                    }
                }
            }
        }
        return parentEntityId;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        vaccineRepository = ImmunizationLibrary.getInstance().vaccineRepository();
        return super.onStartCommand(intent, flags, startId);
    }
}
