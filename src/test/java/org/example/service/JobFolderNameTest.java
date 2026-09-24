package org.example.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobFolderNameTest {

    @Test
    void joinsCompanyRoleAndJobId() {
        assertEquals("Acme-Corp_Senior-Backend-Engineer_12345",
                JobFolderName.of("Acme Corp", "Senior Backend Engineer", "12345"));
    }

    @Test
    void leavesOutMissingJobId() {
        assertEquals("Acme_Engineer", JobFolderName.of("Acme", "Engineer", null));
        assertEquals("Acme_Engineer", JobFolderName.of("Acme", "Engineer", "  "));
    }

    @Test
    void removesCharactersWindowsDoesNotAllow() {
        assertEquals("A-B_Dev-Ops-Engineer", JobFolderName.of("A/B:", "Dev/Ops \"Engineer\"?", ""));
    }

    @Test
    void keepsTrailingDotsOutOfFolderNames() {
        assertEquals("Acme-Inc_Engineer", JobFolderName.of("Acme Inc.", "Engineer", null));
    }
}
