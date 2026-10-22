package com.jobtracker.mail;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where an application was made, read from who sent the email about it: a job site, a hiring system, or the company itself. */
final class MailPlatform {

    private static final Map<String, String> KNOWN = new LinkedHashMap<>();
    static {
        KNOWN.put("linkedin", "LinkedIn");
        KNOWN.put("naukri", "Naukri");
        KNOWN.put("indeed", "Indeed");
        KNOWN.put("workday", "Workday");
        KNOWN.put("greenhouse", "Greenhouse");
        KNOWN.put("lever.co", "Lever");
        KNOWN.put("foundit", "foundit");
        KNOWN.put("monster", "foundit");
        KNOWN.put("instahyre", "Instahyre");
        KNOWN.put("wellfound", "Wellfound");
        KNOWN.put("angel.co", "Wellfound");
        KNOWN.put("smartrecruiters", "SmartRecruiters");
        KNOWN.put("ashbyhq", "Ashby");
        KNOWN.put("icims", "iCIMS");
        KNOWN.put("taleo", "Taleo");
        KNOWN.put("successfactors", "SuccessFactors");
        KNOWN.put("glassdoor", "Glassdoor");
        KNOWN.put("cutshort", "Cutshort");
        KNOWN.put("hirist", "Hirist");
        KNOWN.put("internshala", "Internshala");
    }
    private static final Pattern DOMAIN = Pattern.compile("@([a-z0-9.-]+)");

    private MailPlatform() {}

    /** "LinkedIn", "Naukri", ... or, for mail straight from a company, "Company email". Never null. */
    static String of(String from) {
        String f = from == null ? "" : from.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : KNOWN.entrySet()) if (f.contains(e.getKey())) return e.getValue();
        Matcher m = DOMAIN.matcher(f);
        return m.find() ? "Company email" : "Email";
    }
}
