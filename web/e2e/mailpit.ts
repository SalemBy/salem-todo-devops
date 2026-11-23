const MAILPIT = (process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025').replace(/\/$/, '')

/** Reads the verification token from the newest email Mailpit holds for the address, as Postman does. */
export async function verificationToken(email: string): Promise<string> {
  const search = `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`
  for (let attempt = 0; attempt < 40; attempt++) {
    const found = (await (await fetch(search)).json()) as { messages: Array<{ ID: string }> }
    const id = found.messages[0]?.ID
    if (id) {
      const message = (await (await fetch(`${MAILPIT}/api/v1/message/${id}`)).json()) as {
        Text: string
      }
      const token = /token=([A-Za-z0-9_-]+)/.exec(message.Text)?.[1]
      if (!token) throw new Error(`The email to ${email} has no verification token`)
      return token
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`No verification email for ${email} in Mailpit at ${MAILPIT}`)
}
