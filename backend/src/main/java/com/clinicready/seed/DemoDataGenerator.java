package com.clinicready.seed;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.clinicready.seed.DemoData.Appointment;
import com.clinicready.seed.DemoData.AppointmentType;
import com.clinicready.seed.DemoData.Clinic;
import com.clinicready.seed.DemoData.Patient;
import com.clinicready.seed.DemoData.Practitioner;
import com.clinicready.seed.DemoData.User;

/**
 * Builds the fictional "Clinique Démo Nabeul" dataset.
 *
 * <p>Deterministic: the same {@code today} always yields the same data, and every id is derived
 * from a stable key, so re-seeding updates rows in place instead of duplicating them. Appointments
 * are anchored to {@code today} so that "tomorrow's appointments" is never empty.
 *
 * <p>No real person, phone number or identifier is used: names are random combinations of common
 * first names and surnames, e-mails use the reserved {@code .test} TLD, and phone numbers start with
 * {@code +216 00}, which is not a valid Tunisian prefix.
 */
public final class DemoDataGenerator {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Tunis");
    public static final int PATIENT_COUNT = 40;
    public static final String CLINIC_NAME = "Clinique Démo Nabeul";
    public static final String EMAIL_DOMAIN = "nabeul-demo.test";

    private static final long SEED = 20260101L;
    private static final LocalTime FIRST_SLOT = LocalTime.of(8, 30);
    private static final int SLOT_MINUTES = 30;

    /** Working-day offset from the anchor day -> number of appointments that day. */
    private static final int[][] APPOINTMENTS_PER_DAY = {
        {-5, 3}, {-4, 3}, {-3, 2}, {-2, 3}, {-1, 3}, {0, 4}, {1, 14}, {2, 6}, {3, 4}
    };

    private static final String[] FR_FEMALE = {
        "Amel", "Sarra", "Ines", "Nour", "Rim", "Mariem", "Salma", "Leila", "Hela", "Emna", "Ons",
        "Sonia", "Rania", "Yosra", "Khadija"
    };
    private static final String[] AR_FEMALE = {
        "أمل", "سارة", "إيناس", "نور", "ريم", "مريم", "سلمى", "ليلى", "هالة", "آمنة", "أسماء",
        "سناء", "رانيا", "يسرى", "خديجة"
    };
    private static final String[] FR_MALE = {
        "Mehdi", "Walid", "Slim", "Anis", "Karim", "Hichem", "Sami", "Nizar", "Ahmed", "Mohamed",
        "Youssef", "Rached", "Fares", "Bilel", "Hatem"
    };
    private static final String[] AR_MALE = {
        "مهدي", "وليد", "سليم", "أنيس", "كريم", "هشام", "سامي", "نزار", "أحمد", "محمد", "يوسف",
        "رشاد", "فارس", "بلال", "حاتم"
    };
    private static final String[] FR_SURNAMES = {
        "Ben Salem", "Gharbi", "Trabelsi", "Jebali", "Mansouri", "Bouzid", "Haddad", "Chaabane",
        "Khelifi", "Dridi", "Mejri", "Ayari", "Sassi", "Zouari", "Ben Amor", "Hamdi", "Belhaj",
        "Ferchichi", "Mrad", "Karray"
    };
    /** Same order as {@link #FR_SURNAMES}. */
    private static final String[] AR_SURNAMES = {
        "بن سالم", "الغربي", "الطرابلسي", "الجبالي", "المنصوري", "بوزيد", "حداد", "الشعباني",
        "الخليفي", "الدريدي", "المجري", "العياري", "الساسي", "الزواري", "بن عمر", "حمدي", "بالحاج",
        "الفرشيشي", "مراد", "كراي"
    };

    private static final String[][] APPOINTMENT_TYPES = {
        {"CONSULTATION", "Consultation"},
        {"FOLLOW_UP", "Consultation de contrôle"},
        {"ULTRASOUND", "Échographie"},
        {"XRAY", "Radiographie"},
        {"MRI", "IRM"},
        {"CT_SCAN", "Scanner"},
    };

    private DemoDataGenerator() {}

    public static DemoData generate(LocalDate today) {
        Random rng = new Random(SEED);

        Clinic clinic = new Clinic(id("clinic"), CLINIC_NAME, "Nabeul");

        Practitioner benSalem =
                new Practitioner(id("practitioner/ben-salem"), "Dr Amel Ben Salem", "Radiologie");
        Practitioner gharbi =
                new Practitioner(id("practitioner/gharbi"), "Dr Walid Gharbi", "Cardiologie");
        List<Practitioner> practitioners = List.of(benSalem, gharbi);

        // One login per role (two practitioners => two PRACTITIONER users).
        List<User> users = List.of(
                new User(id("user/reception"), "reception@" + EMAIL_DOMAIN, "Rim Bouzid",
                        "RECEPTIONIST", null),
                new User(id("user/manager"), "manager@" + EMAIL_DOMAIN, "Leila Mrad",
                        "CLINIC_MANAGER", null),
                new User(id("user/dr-ben-salem"), "dr.bensalem@" + EMAIL_DOMAIN, benSalem.fullName(),
                        "PRACTITIONER", benSalem.id()),
                new User(id("user/dr-gharbi"), "dr.gharbi@" + EMAIL_DOMAIN, gharbi.fullName(),
                        "PRACTITIONER", gharbi.id()));

        List<AppointmentType> types = new ArrayList<>();
        for (String[] t : APPOINTMENT_TYPES) {
            types.add(new AppointmentType(id("type/" + t[0]), t[0], t[1]));
        }

        List<Patient> patients = generatePatients(rng, practitioners);
        List<Appointment> appointments =
                generateAppointments(rng, today, practitioners, types, patients);

        return new DemoData(clinic, practitioners, users, types, patients, appointments);
    }

    private static List<Patient> generatePatients(Random rng, List<Practitioner> practitioners) {
        Set<String> usedNames = new HashSet<>();
        for (Practitioner p : practitioners) {
            usedNames.add(p.fullName());
        }
        List<Patient> patients = new ArrayList<>();
        for (int i = 1; i <= PATIENT_COUNT; i++) {
            // 16 of 40 patients prefer Arabic (indexes 1 and 3 of every 5).
            boolean arabic = i % 5 == 1 || i % 5 == 3;
            String name;
            do {
                boolean female = rng.nextBoolean();
                int first = rng.nextInt(15);
                int last = rng.nextInt(FR_SURNAMES.length);
                name = arabic
                        ? (female ? AR_FEMALE : AR_MALE)[first] + " " + AR_SURNAMES[last]
                        : (female ? FR_FEMALE : FR_MALE)[first] + " " + FR_SURNAMES[last];
            } while (!usedNames.add(name));
            String ref = String.format("DEMO-%03d", i);
            // "+216 00 ..." is not a valid Tunisian number prefix: it can never reach a real person.
            String phone = String.format("+216 00 000 %03d", i);
            patients.add(new Patient(id("patient/" + ref), ref, name, phone, arabic ? "AR" : "FR"));
        }
        return patients;
    }

    private static List<Appointment> generateAppointments(
            Random rng, LocalDate today, List<Practitioner> practitioners,
            List<AppointmentType> types, List<Patient> patients) {
        // Clinic is closed on Sundays; if today is Sunday, anchor on Saturday so days never collide.
        LocalDate anchor = today.getDayOfWeek() == DayOfWeek.SUNDAY ? today.minusDays(1) : today;

        List<Appointment> result = new ArrayList<>();
        for (int[] day : APPOINTMENTS_PER_DAY) {
            LocalDate date = shiftWorkdays(anchor, day[0]);
            boolean past = date.isBefore(today);
            Set<Integer> patientsToday = new HashSet<>();
            int[] nextSlot = new int[practitioners.size()];

            for (int n = 0; n < day[1]; n++) {
                int practitionerIdx = n % practitioners.size();
                int patientIdx;
                do {
                    patientIdx = rng.nextInt(patients.size());
                } while (!patientsToday.add(patientIdx));
                AppointmentType type = types.get(rng.nextInt(types.size()));

                LocalTime time =
                        FIRST_SLOT.plusMinutes((long) SLOT_MINUTES * nextSlot[practitionerIdx]++);
                String status = statusFor(rng, past);
                // Keep one cancelled appointment tomorrow so the UI/agent must handle it.
                if (day[0] == 1 && n == 7) {
                    status = "CANCELLED";
                }
                String key = "appointment/" + day[0] + "/" + n;
                result.add(new Appointment(
                        id(key),
                        patients.get(patientIdx).id(),
                        practitioners.get(practitionerIdx).id(),
                        type.id(),
                        date.atTime(time).atZone(CLINIC_ZONE).toOffsetDateTime(),
                        status));
            }
        }
        return result;
    }

    private static String statusFor(Random rng, boolean past) {
        int roll = rng.nextInt(10); // always consume one value so the stream does not depend on `past`
        if (!past) {
            return "SCHEDULED";
        }
        return roll == 0 ? "NO_SHOW" : roll == 1 ? "CANCELLED" : "COMPLETED";
    }

    /** Moves by {@code n} working days (Sundays skipped), forward or backward. */
    static LocalDate shiftWorkdays(LocalDate from, int n) {
        LocalDate d = from;
        int step = n < 0 ? -1 : 1;
        for (int i = 0; i < Math.abs(n); i++) {
            do {
                d = d.plusDays(step);
            } while (d.getDayOfWeek() == DayOfWeek.SUNDAY);
        }
        return d;
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("clinicready-demo/" + key).getBytes(StandardCharsets.UTF_8));
    }
}
