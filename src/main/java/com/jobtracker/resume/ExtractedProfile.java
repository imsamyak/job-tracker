package com.jobtracker.resume;

import java.util.List;

/**
 * Everything read out of a resume, in the shape of the profile page. Fields the resume does not
 * contain are empty strings or empty lists, never made up.
 */
public record ExtractedProfile(Personal personal, List<Experience> experience, List<Project> projects, List<SkillGroup> skills, Contact contact) {

    /** Skills under one heading, as a resume groups them (Languages, Cloud, Databases...). */
    public record SkillGroup(String name, List<String> items) {}

    public record Personal(String name, String headline, String experience, String location, String about) {}

    public record Experience(String company, String role, String start, String end, boolean current, String details) {}

    /** A project with all of its links (source code, a live demo, an architecture page...). */
    public record Project(String name, List<Item> links, String stack, String details) {}

    /** One email, phone number or link, with a short label such as "Main", "Mobile" or "LinkedIn". */
    public record Item(String label, String value) {}

    public record Contact(List<Item> emails, List<Item> phones, List<Item> links) {}
}
