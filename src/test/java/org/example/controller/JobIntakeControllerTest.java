package org.example.controller;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.service.intake.JobIntakeException;
import org.example.service.intake.JobIntakeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Checks only the controller's own job - mapping what {@link JobIntakeService} returns to the right HTTP
 * status and response shape. The duplicate rules and the actual (slow) reading are
 * {@link JobIntakeService}'s job and are covered there, with a fake extractor, so those tests never shell
 * out or cost anything - and never run here either, since the service itself is mocked.
 */
class JobIntakeControllerTest {

    private static Job job(String company, String role) {
        return Job.builder()
                .id(UUID.randomUUID())
                .company(company)
                .role(role)
                .folderName(company + "_" + role)
                .type(JobType.FullTime)
                .mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java"))
                .stacks(List.of("java"))
                .contacts(List.of())
                .description("desc")
                .status(JobStatus.NEW)
                .build();
    }

    private static PendingIntake pending(PendingIntakeStatus status) {
        return PendingIntake.builder().id(UUID.randomUUID()).pdfPath("x.pdf").status(status).build();
    }

    private static MockMultipartFile aPdf() {
        return new MockMultipartFile("jdPdf", "job.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    @Test
    void intakeAcceptsImmediatelyWithoutWaitingOnExtraction() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        PendingIntake accepted = pending(PendingIntakeStatus.PENDING);
        when(service.accept(any(), isNull())).thenReturn(new JobIntakeService.AcceptOutcome.New(accepted));
        var controller = new JobIntakeController(service);

        var response = controller.intake(aPdf(), null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(accepted.getId().toString(), response.getBody().id());
        assertEquals("PENDING", response.getBody().status());
        assertFalse(response.getBody().alreadyTracked());
        Mockito.verify(service).accept(any(), isNull());
        Mockito.verifyNoMoreInteractions(service); // in particular: nothing here ever reads the posting
    }

    @Test
    void aUrlAlreadyQueuedMapsTo202TooSinceNothingNewNeedsDoing() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        PendingIntake existing = pending(PendingIntakeStatus.NEEDS_CONFIRMATION);
        when(service.accept(any(), eq("https://example.com/job/1")))
                .thenReturn(new JobIntakeService.AcceptOutcome.AlreadyQueued(existing));
        var controller = new JobIntakeController(service);

        var response = controller.intake(aPdf(), "https://example.com/job/1");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("NEEDS_CONFIRMATION", response.getBody().status());
        assertFalse(response.getBody().alreadyTracked());
    }

    @Test
    void aUrlAlreadyTrackedAsAJobMapsTo200WithoutReReading() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        Job existing = job("Acme", "Engineer");
        when(service.accept(any(), eq("https://example.com/job/1")))
                .thenReturn(new JobIntakeService.AcceptOutcome.AlreadyTracked(existing));
        var controller = new JobIntakeController(service);

        var response = controller.intake(aPdf(), "https://example.com/job/1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(existing.getId().toString(), response.getBody().id());
        assertTrue(response.getBody().alreadyTracked());
    }

    @Test
    void rejectsAnEmptyPdfWithoutCallingTheService() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        var controller = new JobIntakeController(service);
        var empty = new MockMultipartFile("jdPdf", "job.pdf", "application/pdf", new byte[0]);

        assertThrows(JobIntakeException.class, () -> controller.intake(empty, null));
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void confirmDelegatesToTheServiceAndMapsTo201() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        UUID id = UUID.randomUUID();
        when(service.confirmPending(id)).thenReturn(job("Acme", "Engineer"));
        var controller = new JobIntakeController(service);

        var response = controller.confirm(id);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Acme", response.getBody().company());
        Mockito.verify(service).confirmPending(id);
    }

    @Test
    void rejectDelegatesToTheServiceAndMapsTo204() {
        JobIntakeService service = Mockito.mock(JobIntakeService.class);
        UUID id = UUID.randomUUID();
        var controller = new JobIntakeController(service);

        var response = controller.reject(id);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        Mockito.verify(service).rejectPending(id);
    }

    @Test
    void invalidJobExceptionMapsTo422() {
        var controller = new JobIntakeController(Mockito.mock(JobIntakeService.class));

        var response = controller.onInvalidJob(new JobIntakeException("no company"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("no company", response.getBody().error());
    }
}
