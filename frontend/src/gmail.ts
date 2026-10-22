/** Opens an email in Gmail: a search for its Message-ID, in the account it was read from. */
export function gmailLink(messageId: string, account: string | null): string {
  const id = messageId.replace(/^<|>$/g, '')
  const who = account ? `?authuser=${encodeURIComponent(account)}` : ''
  return `https://mail.google.com/mail/${who}#search/${encodeURIComponent(`rfc822msgid:${id}`)}`
}
