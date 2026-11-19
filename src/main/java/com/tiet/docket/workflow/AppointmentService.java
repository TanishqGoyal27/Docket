package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.Appointment;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Books in-person visit slots for approved applications that need one (UC-01.6, FR13)
@Service
public class AppointmentService {

    // Office hours: one student per slot, lunch break at 13:00
    private static final List<LocalTime> SLOT_TIMES = List.of(
            LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
            LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));
    private static final int WORKING_DAYS_AHEAD = 5;

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }

    // A visit is needed once a service that requires one has been approved
    public boolean needsVisit(Application app) {
        return app.getService().isRequiresVisit() && app.getStatus() == ApplicationStatus.APPROVED;
    }

    // Free slots on the next working days, starting tomorrow
    @Transactional(readOnly = true)
    public List<LocalDateTime> availableSlots() {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDate day = LocalDate.now();
        int days = 0;
        while (days < WORKING_DAYS_AHEAD) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            days++;
            for (LocalTime time : SLOT_TIMES) {
                LocalDateTime slot = day.atTime(time);
                if (!appointmentRepository.existsBySlot(slot)) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    @Transactional
    public Appointment book(Application app, User student, LocalDateTime slot) {
        if (!app.getStudent().getId().equals(student.getId())) {
            throw new IllegalStateException("You can only book a visit for your own application");
        }
        if (!needsVisit(app)) {
            throw new IllegalStateException("This application does not need an in-person visit");
        }
        if (appointmentRepository.findByApplication(app).isPresent()) {
            throw new IllegalStateException("A visit is already booked for this application");
        }
        if (slot == null || !availableSlots().contains(slot)) {
            throw new IllegalArgumentException("This slot is not available. Please choose another one");
        }

        Appointment appointment = new Appointment();
        appointment.setApplication(app);
        appointment.setSlot(slot);
        Appointment saved = appointmentRepository.save(appointment);

        String when = slot.toLocalDate() + " at " + slot.toLocalTime();
        notificationService.notifyUser(student, app,
                "Visit booked for " + app.getReferenceNumber() + " on " + when + ".");
        notificationService.notifyRole(Role.ADMIN_OFFICER, null, app,
                "Student " + student.getName() + " booked a visit for " + app.getReferenceNumber()
                        + " on " + when + ".");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Appointment> upcoming() {
        return appointmentRepository.findBySlotAfterOrderBySlotAsc(LocalDate.now().atStartOfDay());
    }
}
