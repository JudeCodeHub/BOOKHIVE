# Email in production: SES, SPF, DKIM and DMARC

How to set up email for the live site so BookHive's messages (password resets, due-date reminders, hold notices) reach inboxes instead of spam. Locally nothing here is needed: every email goes to Mailpit (<http://localhost:8025>).

Related: [ADR 0004](adr/0004-notifications.md) (how notifications are sent), `backend/src/main/resources/application-prod.yml` (SMTP settings), `backend/.env.example`.

## Why this matters

Gmail, Outlook and Yahoo check every incoming email for proof that it really comes from the domain in the `From` address. Without that proof, BookHive's emails will often land in spam or be rejected, and a password-reset email in spam means a member can't log in.

| Check | Question it answers | Set up by |
|---|---|---|
| **SPF** | Is this server allowed to send mail for this domain? | A TXT record listing allowed senders |
| **DKIM** | Was this email really signed by the domain, and not changed on the way? | Public keys published as DNS records; SES signs each email |
| **DMARC** | What should receivers do when SPF/DKIM fail, and where to report it? | A TXT record at `_dmarc` |

All three are DNS records. With AWS (Route 53 + SES, as in the PRD) most of the values are generated for you.

## What we send, and from where

- **From:** `BookHive <no-reply@bookhive.lk>` (`MAIL_FROM`)
- **Sending service:** Amazon SES, through its SMTP interface. The backend only needs SMTP settings; nothing in the code is SES-specific.
- **Volume:** a few hundred emails a day at most (reminders go out in the morning, see `bookhive.jobs.*`).

Replace `bookhive.lk` below with the real domain.

## Step by step

### 1. Pick the SES region

Use one region for everything, near the servers. For Sri Lanka, **Asia Pacific (Mumbai) `ap-south-1`** or **Asia Pacific (Singapore) `ap-southeast-1`**. The SMTP endpoint, the verified identity and the SMTP credentials all belong to that region.

### 2. Verify the domain in SES with Easy DKIM

In the SES console: *Identities* → *Create identity* → *Domain* → `bookhive.lk`, with **Easy DKIM** and **RSA 2048-bit** keys.

SES then shows **three CNAME records** like:

```
abc123._domainkey.bookhive.lk  CNAME  abc123.dkim.amazonses.com
def456._domainkey.bookhive.lk  CNAME  def456.dkim.amazonses.com
ghi789._domainkey.bookhive.lk  CNAME  ghi789.dkim.amazonses.com
```

Add exactly those (if the domain is in Route 53 in the same account, SES can add them for you). Verification usually completes within an hour. From then on SES **signs every email with DKIM for `bookhive.lk`**, which is what DMARC relies on.

### 3. Custom MAIL FROM domain (makes SPF line up with our domain)

By default the hidden "envelope sender" of SES emails is an `amazonses.com` address, so SPF passes for *Amazon's* domain, not ours. Set a **custom MAIL FROM domain** on the identity, e.g. `mail.bookhive.lk`, and add the two records SES shows:

```
mail.bookhive.lk  MX   10 feedback-smtp.<region>.amazonses.com
mail.bookhive.lk  TXT  "v=spf1 include:amazonses.com ~all"
```

That second record is **SPF**. It sits on the `mail.` subdomain, which is a subdomain of our own domain, so it counts for DMARC.

**SPF on the main domain:** only needed if other services send as `@bookhive.lk` (e.g. the library's own mailbox at Google Workspace or Microsoft 365). Then `bookhive.lk` gets **one** SPF record listing those services. There must never be two SPF records on the same name.

### 4. DMARC: start by watching, then tighten

Add a TXT record at `_dmarc.bookhive.lk`. Start in **monitoring mode**:

```
_dmarc.bookhive.lk  TXT  "v=DMARC1; p=none; rua=mailto:dmarc-reports@bookhive.lk"
```

- `p=none`: receivers deliver as usual but send daily reports to the `rua` address. Read them for 2–4 weeks: everything legitimate should show **DKIM pass** (and SPF pass via `mail.bookhive.lk`).
- Then change to `p=quarantine` (failures go to spam), and later `p=reject` (failures are refused). This stops other people sending fake "BookHive" emails, for example phishing for members' passwords.
- Keep the default *relaxed* alignment (don't add `aspf=s`): our SPF passes for `mail.bookhive.lk`, which is only "aligned" with `bookhive.lk` under relaxed rules.

The `rua` mailbox must exist (or use a DMARC report service).

### 5. Leave the SES sandbox

New SES accounts are in the **sandbox**: they can only send to verified addresses, at most 200 a day. Request **production access** in the SES console (*Account dashboard* → *Request production access*). Describe the use honestly: transactional emails for library members (account, loan reminders, holds), opt-out of reminders in the member's settings, bounces and complaints monitored. Approval usually takes about a day.

### 6. SMTP credentials → environment variables

In SES: *SMTP settings* → *Create SMTP credentials*. This creates an IAM user with send-only permission and shows a **username and password once**. These are **not** your AWS login; store them in the secrets store (e.g. AWS Secrets Manager / SSM Parameter Store) and give them to the backend as environment variables:

| Variable | Value |
|---|---|
| `MAIL_HOST` | `email-smtp.<region>.amazonaws.com` (e.g. `email-smtp.ap-south-1.amazonaws.com`) |
| `MAIL_PORT` | `587` (STARTTLS; `application-prod.yml` requires TLS) |
| `MAIL_USERNAME` | SMTP username from SES |
| `MAIL_PASSWORD` | SMTP password from SES (secret: never in git, never in logs) |
| `MAIL_FROM` | `BookHive <no-reply@bookhive.lk>` |
| `FRONTEND_URL` | `https://bookhive.lk` (used for links in emails) |

### 7. Bounces and complaints

SES watches the bounce and complaint rates; if they get high (around 5% bounces or 0.1% complaints) it can pause sending for the account. For our volume, start with:

- SES's **account-level suppression list** on (it stops sending to addresses that hard-bounced or complained).
- Email notifications for bounces/complaints to an admin mailbox (*Identity* → *Notifications*).

Later, if needed: SES → SNS → a small backend endpoint that marks the member's email as bad, so staff can ask for a new address at the desk.

## Checking it works

1. Log in as admin on the live site → **Admin console → Templates** → **Send test to me** (sends to your own address).
2. In Gmail, open the email → *⋮* → **Show original**. It should say **SPF: PASS**, **DKIM: PASS** (domain `bookhive.lk`) and **DMARC: PASS**.
3. Optionally send one to a tester such as mail-tester.com and aim for 9/10 or better.
4. Look at the email in the Gmail app on a phone, in English, Sinhala and Tamil (checklist step 326).
5. Keep an eye on **Admin console → Messages** for failed sends after go-live.

## Good to know

- **DNS takes time:** new records can take minutes to hours to be visible everywhere. Verify in SES before sending to members.
- **Never send from a free-mail address** (`@gmail.com`) through SES: DMARC at Gmail will reject it.
- **Changing the domain later** means repeating steps 2–4 for the new domain.
- **Large senders** (5,000+ emails a day to Gmail) must also add one-click unsubscribe headers. BookHive is far below that; members switch reminders off in *Notification settings*, linked at the bottom of every optional email.
