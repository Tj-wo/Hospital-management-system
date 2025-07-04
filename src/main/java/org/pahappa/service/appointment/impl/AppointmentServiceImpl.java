package org.pahappa.service.appointment.impl;

import org.hibernate.Session;
import org.pahappa.dao.AppointmentDao;
import org.pahappa.model.Appointment;
import org.pahappa.model.Staff;
import org.pahappa.service.appointment.AppointmentService;
import org.pahappa.service.HibernateUtil;
import org.pahappa.utils.AppointmentStatus;
import org.pahappa.utils.Role;
import org.pahappa.model.Patient;

import javax.enterprise.context.ApplicationScoped;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentDao appointmentDao = new AppointmentDao();

    @Override
    public List<Appointment> getAllAppointments() {
        return appointmentDao.getAll();
    }

    @Override
    public Appointment getAppointmentById(Long appointmentId) {
        return appointmentDao.getById(appointmentId);
    }

    @Override
    public void scheduleAppointment(Appointment appointment) {
        System.out.println(">>> Scheduling appointment:");
        System.out.println("Patient: " + appointment.getPatient());
        System.out.println("Doctor: " + appointment.getDoctor());
        System.out.println("Date: " + appointment.getAppointmentDate());
        System.out.println("Reason: " + appointment.getReason());

        validateAppointment(appointment, null);
        appointment.setDeleted(false);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointmentDao.save(appointment);
    }

    @Override
    public void updateAppointment(Appointment appointment) {
        if (appointment.getId() == null) {
            throw new IllegalArgumentException("Appointment ID is required for an update.");
        }
        validateAppointment(appointment, appointment.getId());
        appointmentDao.update(appointment);
    }

    @Override
    public void cancelAppointment(Long appointmentId, boolean cancelledByPatient) {
        Appointment appointment = appointmentDao.getById(appointmentId);
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment not found with ID: " + appointmentId);
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed appointment.");
        }
        appointment.setStatus(cancelledByPatient ? AppointmentStatus.CANCELLED_BY_PATIENT : AppointmentStatus.CANCELLED_BY_DOCTOR);
        appointmentDao.update(appointment);
    }

    @Override
    public List<Appointment> getAppointmentsForDoctor(long doctorId) {
        return appointmentDao.findByDoctorId(doctorId);
    }

    @Override
    public List<Appointment> getAppointmentsForPatient(long patientId) {
        return appointmentDao.findByPatientId(patientId);
    }

    @Override
    public void updateAppointmentStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = appointmentDao.getById(appointmentId);
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment not found with ID: " + appointmentId);
        }
        appointment.setStatus(newStatus);
        appointmentDao.update(appointment);
    }

    @Override
    public List<Appointment> getAppointmentsByPatient(Patient patient) {
        if (patient == null || patient.getId() == null) {
            throw new IllegalArgumentException("Patient must not be null and must have a valid ID.");
        }
        return appointmentDao.findByPatientId(patient.getId());
    }

    @Override
    public long countAppointments() {
        return appointmentDao.getAll().size();
    }

    private void validateAppointment(Appointment appointment, Long idToIgnore) {
        if (appointment == null) throw new IllegalArgumentException("Appointment object cannot be null.");
        if (appointment.getPatient() == null) throw new IllegalArgumentException("Patient is required for the appointment.");
        if (appointment.getDoctor() == null) throw new IllegalArgumentException("Doctor is required for the appointment.");
        if (appointment.getDoctor().getRole() != Role.DOCTOR) throw new IllegalArgumentException("Assigned staff must be a DOCTOR.");
        if (appointment.getAppointmentDate() == null) throw new IllegalArgumentException("Appointment date is required.");
        if (appointment.getReason() == null || appointment.getReason().trim().isEmpty()) throw new IllegalArgumentException("A reason for the appointment is required.");

        // Convert LocalDateTime.now().minusMinutes(5) to java.util.Date for comparison
        Date fiveMinutesAgo = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant());
        if (appointment.getAppointmentDate().before(fiveMinutesAgo)) {
            throw new IllegalArgumentException("Appointment date cannot be in the past.");
        }
        if (!isDoctorAvailable(appointment.getDoctor(), appointment.getAppointmentDate(), idToIgnore)) {
            throw new IllegalArgumentException("Doctor is already booked for a 'SCHEDULED' appointment at this exact time.");
        }
    }

    private boolean isDoctorAvailable(Staff doctor, Date appointmentTime, Long idToIgnore) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(a.id) FROM Appointment a WHERE a.doctor = :doctor AND a.appointmentDate = :appointmentTime AND a.status = 'SCHEDULED' AND a.deleted = false";
            if (idToIgnore != null) {
                hql += " AND a.id != :idToIgnore";
            }
            var query = session.createQuery(hql, Long.class)
                    .setParameter("doctor", doctor)
                    .setParameter("appointmentTime", appointmentTime);
            if (idToIgnore != null) {
                query.setParameter("idToIgnore", idToIgnore);
            }
            return query.getSingleResult() == 0;
        }
    }
}
