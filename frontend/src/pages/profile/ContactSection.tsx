import LabeledList from './LabeledList'
import type { SectionProps } from './types'

/** Every email and phone number you might use. The Profiles section picks which ones go where. */
export default function ContactSection({ data, update }: SectionProps) {
  return (
    <div className="cgroups">
      <LabeledList
        title="Emails"
        labelHint="Main"
        valueHint="you@example.com"
        items={data.contact.emails}
        onChange={(next) => update((d) => ({ ...d, contact: { ...d.contact, emails: next } }))}
      />
      <LabeledList
        title="Phone numbers"
        labelHint="Mobile"
        valueHint="+91 98765 43210"
        items={data.contact.phones}
        onChange={(next) => update((d) => ({ ...d, contact: { ...d.contact, phones: next } }))}
      />
    </div>
  )
}
