# Docket – Digital Campus Administration & Workflow Management System

UCS503 Software Engineering project, group 3C35 (Ridhi Aggarwal, Tanishq Goyal, Saanjal Jain).

Students submit applications and documents online; officers verify and approve them stage by stage;
the Administrator configures each service's approval route and required documents. One reusable
workflow engine runs every service ("Configure Once, Manage Multiple Processes").

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 17, Spring Boot 4 (Web MVC, Data JPA, Security), H2 in-memory database |
| Frontend | React 18 + React Router, built with Vite |
| Tests    | JUnit 5, Spring Boot Test, MockMvc |

## Run it

You only need Java 17+. Maven downloads its own Node.js to build the React app.

```
./mvnw spring-boot:run          (Windows: mvnw.cmd spring-boot:run)
```

Open http://localhost:8080. All test accounts use the password `password123`:

| Role | Email |
|------|-------|
| Student | student@docket.com |
| Administrative Officer | adminofficer@docket.com |
| Head of Department | hod@docket.com |
| Scholarship Officer | scholarship@docket.com |
| Finance Officer | finance@docket.com |
| Accounts Officer | accounts@docket.com |
| Administrator | admin@docket.com |

Data is kept in an H2 database file (`data/docketdb.mv.db`, open source, no installation needed).
Applications **and uploaded files** are stored inside it, so they survive restarts. To start again from
the seed data, stop the app and delete the `data/` folder. Tests always use a separate in-memory database.
Students can also create their own account from the login page ("Register here").

### Working on the frontend

Run the backend (above), then in another terminal:

```
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 – pages reload instantly and API calls go to the backend on port 8080.
To skip the frontend build when only Java changed: `./mvnw spring-boot:run -Dskip.npm -Dskip.installnodenpm`.

### Tests

```
./mvnw test
```

## Services

| Service | Approval route | Outcome |
|---------|----------------|---------|
| ID Card | Pay ₹200 online → Admin Officer checks documents, Finance checks the fee in parallel → Admin Officer final approval → visit booking (FIR copy mandatory for a lost card) | Approved |
| Reimbursement | Supervising teacher approves by email → HOD → Finance (bills) → Accounts (payment record) | Approved (processed) |
| Details Correction | Admin Officer → Admin Officer (record updated automatically) | Closed |
| Document Verification | Admin Officer | Verified |
| Scholarship Verification | Scholarship Officer → Finance (only if forwarded) | Verified |

At every stage an officer can approve, reject (reason required) or return the application to the
student for correction (Finance records this as "Issue found"). The student resubmits and review
resumes at the same stage. New services can be created by the Administrator with no code changes.

## Reimbursement: teacher approval by email

Teachers do not need a Docket account. The student enters the supervising teacher's name, department and
email ID; Docket emails the teacher a one-time link (valid 7 days). The teacher opens it without logging in,
checks the claim and documents, and approves or rejects with a remark. Only an approved claim reaches the HOD,
who sees the teacher's approval before deciding. The student can resend the email from the claim page.

Without a mail server the email is saved to **Administrator > Email Outbox**, where the link can be opened
for a demo. To send real email, set before starting (Gmail example, using an App Password):

```
set SPRING_MAIL_HOST=smtp.gmail.com
set SPRING_MAIL_PORT=587
set SPRING_MAIL_USERNAME=you@gmail.com
set SPRING_MAIL_PASSWORD=your-app-password
set SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
set SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
set DOCKET_PUBLIC_URL=http://<address teachers can reach>:8080
set DOCKET_TEACHER_EMAIL_DOMAIN=thapar.edu
```

`DOCKET_TEACHER_EMAIL_DOMAIN` stops students from entering a non-college address they control.

## Application fee and payment gateway

Services with a fee (set by the Administrator per service) are paid online while applying:

1. The student fills the form, uploads documents and clicks **Save and pay**. The application is saved as
   *Payment pending* and is **not submitted** yet.
2. The gateway checkout opens. After payment the server checks the gateway's signature
   (`HMAC-SHA256(orderId|paymentId, secret)`), generates a **fee receipt PDF** (stored in the database) and
   submits the application.
3. The **Administrative Officer** reviews the documents (the fee receipt is hidden from them). In parallel the
   **Finance Officer** opens *Fee Verification*, matches each payment record with the settlement / bank
   statement and marks it *Confirmed* or *Not found* (not found rejects the application).
4. The final approval is blocked until Finance has confirmed the fee.

**Gateways.** Razorpay and Eduqfix are commercial (not open source). By default Docket uses its own
**sandbox gateway** (Java code in `payment/`, test mode, no real money) that follows Razorpay's flow. To use
**Razorpay test mode**, create a Razorpay account and set its test keys before starting the app:

```
set RAZORPAY_KEY_ID=rzp_test_xxxxxxxx
set RAZORPAY_KEY_SECRET=xxxxxxxx
mvnw.cmd spring-boot:run
```

Never commit real keys. Live (real-money) mode needs Razorpay KYC and is outside the project scope.

## Project layout

```
src/main/java/com/tiet/docket/
  model/        JPA entities (Application, Stage, Service, DecisionHistory, ...)
  repository/   Spring Data repositories
  workflow/     WorkflowService (the engine), notifications, appointments, audit log
  payment/      PaymentGateway (interface), SandboxGateway, RazorpayGateway, PaymentService, ReceiptPdf
  student/      SubmissionService (validation + uploads), FileStorageService
  admin/        AdminService (users, services, stages, documents)
  api/          REST controllers and JSON DTOs used by the React app
  config/       Security, seed data
frontend/src/
  pages/        student/, staff/, admin/ screens, shared application page
  components/   layout, tables, file input, small UI helpers
```

## REST API (summary)

| Area | Endpoints |
|------|-----------|
| Auth | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf` |
| Shared | `GET /api/applications/{id}`, `GET /api/documents/{id}`, `/api/notifications/**` |
| Student | `GET /api/student/services`, `GET/POST /api/student/applications`, `POST .../{id}/resubmit`, `GET /api/student/slots`, `POST .../{id}/appointment` |
| Staff | `GET /api/staff/queue`, `GET /api/staff/history`, `POST /api/staff/applications/{id}/approve|reject|correction|payment`, `GET /api/staff/appointments` |
| Admin | `/api/admin/stats`, `/api/admin/users/**`, `/api/admin/services/**`, `/api/admin/stages/{id}`, `/api/admin/documents/{id}/toggle`, `/api/admin/applications`, `/api/admin/audit` |
