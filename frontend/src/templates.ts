/**
 * The note templates: plain text with {placeholders} that are filled in for one job. They live in the profile
 * (Templates section), where you can edit them, and the Referral window fills them in.
 *
 * Placeholders: {name} {headline} {experience} {email} {linkedin} {notice}
 *               {company} {role} {id}  (the last three are about the job; {id} becomes " (Job ID R-4821)" or nothing)
 */

export interface Notes {
  referralShort: string
  referralFull: string
  hrShort: string
  hrFull: string
}

export const DEFAULT_NOTES: Notes = {
  referralShort: `Hi,
I'm {name}, {headline}, with {experience} of experience.
Interested in {company} for {role}{id}. Can we connect?
Email: {email}
Thanks! {name}`,

  referralFull: `Hi,

I hope this message finds you well. I'm {name}, {headline} with {experience} of experience.

I'm very interested in the {role} position at {company}{id}, and I believe my background aligns well with the role. I'm reaching out to ask if you'd be willing to refer me for this opportunity. Your referral would be invaluable in helping my application get noticed.

For reference
Email: {email}
LinkedIn: {linkedin}

Thank you for considering my request. I'd love to connect further if you have any questions.

Best regards,
{name}`,

  hrShort: `Hi,
I just applied for the {role} role{id} at {company}. Excited about the opportunity.
I'm {name}, {headline}. I'm already serving my notice period ({notice}).
Happy to share my resume again or answer any questions.
Thanks! {name}
{email}`,

  hrFull: `Hi,

I just applied for the {role} role{id} at {company} and wanted to reach out directly. I'm genuinely excited about the opportunity and would love the chance to talk more about how I could contribute.

A bit about me: I'm {name}, {headline}, with {experience} of experience. I'm already serving my notice period ({notice}) and can join accordingly. Happy to share my resume again or answer any questions in the meantime.

Thank you for your time. Looking forward to hearing from you!

Best,
{name}
{email}`,
}

/**
 * Fills a template. A sentence that mentions {notice} is dropped when there is no notice period to mention,
 * so "I'm already serving my notice period ()." never appears. Unknown {words} are left as they are.
 */
export function fillTemplate(text: string, values: Record<string, string>): string {
  let t = text
  if (!values.notice) t = t.replace(/ ?[^.!?\n]*\{notice\}[^.!?\n]*[.!?]/g, '')
  return t.replace(/\{(\w+)\}/g, (whole, key: string) => (key in values ? values[key] : whole))
}
