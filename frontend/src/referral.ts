/**
 * Referral note text - two fixed templates with the job's company/role/job-id substituted in. Deliberately
 * NOT written by Claude (the person's explicit instruction: "dont do this with claude, this should be
 * directly done") and never saved to a file - built in memory each time the modal opens, just text ready
 * to copy.
 */

const NAME = "Samyak";
const TITLE = "Software Engineer at Compro Technologies";
const EXPERIENCE = "2 years of experience";
/** The referral email, not the default one - matches Referral.EMAIL on the backend (same email the
 *  referral resume/cover letter are generated with), since this note is only ever used to ask for a
 *  referral. */
const EMAIL = "samyakj8766@gmail.com";

export interface ReferralNoteJob {
    company: string;
    role: string;
    /** The company's own posting id - omitted from the note entirely when null. */
    jobId: string | null;
}

export function shortReferralNote(job: ReferralNoteJob): string {
    const jobIdPart = job.jobId ? ` (Job ID ${job.jobId})` : "";
    return `Hi,
I'm ${NAME}, ${TITLE}, having ${EXPERIENCE}.
Interested in ${job.company} for ${job.role}${jobIdPart} — can we connect?
Email: ${EMAIL}.
Thanks! - ${NAME}`;
}

export function fullReferralNote(job: ReferralNoteJob): string {
    const jobIdLine = job.jobId ? `Job Id: ${job.jobId}\n` : "";
    return `Hi,

I hope this message finds you well. I'm ${NAME}, a ${TITLE} with ${EXPERIENCE}. I've progressed from an internship to my current role, building strong expertise in software delivery and cloud solutions.

I'm very interested in the ${job.role} position at ${job.company}, and I believe my technical background aligns well with the role. I'm reaching out to ask if you'd be willing to refer me for this opportunity — your referral would be invaluable in helping my application get noticed.

I'd be grateful for any support you can provide. Feel free to check out my LinkedIn profile for more details on my work.

For reference
${jobIdLine}Email: ${EMAIL}

Thank you for considering my request. I'd love to connect further if you have any questions.

Best regards,
${NAME}`;
}
