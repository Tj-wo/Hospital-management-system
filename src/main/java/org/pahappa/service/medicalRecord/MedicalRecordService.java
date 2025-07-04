package org.pahappa.service.medicalRecord;

import org.pahappa.model.MedicalRecord;
import java.util.List;

public interface MedicalRecordService {
    void saveMedicalRecord(MedicalRecord record);
    void updateMedicalRecord(MedicalRecord record);
    MedicalRecord getMedicalRecordById(Long id);
    List<MedicalRecord> getAllMedicalRecords();
    List<MedicalRecord> getRecordsForPatient(Long patientId);
}