# 0004. Notifications: event listeners, one notifier, an outbox

- Status: Accepted
- Date: 2026-10-27
- Related: checklist steps 305–329, PRD §5.9 (NOTIF-1 to NOTIF-6), ADR 0001

## Context

BookHive has to tell people things: "your book is due in 2 days", "your reserved book is ready", "your membership was approved", "here is your receipt". The PRD asks for:

- **Three channels:** email (all three languages), SMS (a Sri Lankan gateway, optional), and in-app messages with a bell and an unread count.
- **Triggers** from many modules: circulation (due soon, overdue, receipt), holds (ready, expiring), membership (approved, expiring), payments (success), events (reminders).
- **Per-member preferences** per type and channel.
- **Reliable delivery:** a message that fails (SMTP down, SMS gateway timeout) is retried, and nothing is lost when the app restarts.

What exists before this decision: a few listeners in the `notification` module send plain-text English emails directly through SMTP after the transaction commits (auth emails, membership decisions, checkout receipt, contact form). A failure is only logged, and the message is lost.

The modules that cause notifications must not know about email, SMS or templates. Circulation should say "a loan was created", not "send an email".

## Decision

### 1. Business modules publish events; the notification module listens

Every trigger is a domain event that already exists or is added to its own module: `LoanCreated`, `HoldReady`, `HoldExpiring`, `MembershipApproved`, `MembershipFeeCharged`, `PaymentSucceeded`, and so on. The `notification` module depends on those events; they never depend on it.

Time-based reminders that no single action causes ("due in 2 days", "7 days overdue", "membership ends in 30 days") are found by **daily jobs** (ShedLock, like the other jobs). The jobs publish the same kind of event, so a reminder is handled exactly like an action.

### 2. Listen after the commit

Listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)`. A notification is only created once the thing it talks about is saved: no "your book is checked out" for a checkout that rolled back. (This differs from the fine and hold listeners, which must run *inside* the transaction because they change data that has to be saved together.)

### 3. One entry point: `Notifier.send(NotificationRequest)`

Each listener only translates its event into a `NotificationRequest`: **who** (a user id), **what** (a `NotificationType`), the **values** for the template (title, due date…), an optional **link**, and a **dedupe key** (for example `DUE_SOON:<loanId>:<dueDate>`). The notifier then does everything else in one place:

1. Look up the recipient: email, phone, preferred language.
2. Decide the channels: the type's defaults, minus what the member switched off. Some types can't be switched off.
3. Write the in-app notification row, if that channel is on.
4. Render the email and SMS in the member's language and put them in the **outbox**.

### 4. An outbox for email and SMS

Email and SMS aren't sent inside the request. They are saved as rows in `outbox_messages` and sent by a dispatcher job with **exponential backoff** (retry after 1 min, 2, 4… up to a limit, then marked failed for an admin to see). A unique dedupe key stops the same reminder being queued twice, for example when a job runs twice in one day.

### 5. The list of notification types is code

`NotificationType` lists every message BookHive can send, with its default channels and whether a member may turn it off. Security messages (email verification, password reset, staff invite, password changed) and receipts are email-only and can't be turned off. Reminders can.

## Alternatives considered

- **Send directly from the services** (call `emailSender` inside `CheckoutService`): simplest, but it ties every module to email and sends messages for transactions that later roll back. Rejected.
- **Message broker** (Kafka, RabbitMQ): reliable and scalable, but a whole extra system to run for one library's volume (a few hundred messages a day). The outbox table gives the same at-least-once delivery with the database we already have. It can feed a broker later if ever needed.
- **Async listeners (`@Async`) without an outbox**: faster responses, but a crash between "committed" and "sent" loses the message, and there's no retry. Rejected.
- **Templates in the database** (editable by admins): flexible, but needs an editor, versioning and validation. Templates are files in the code (Thymeleaf, step 309). Admins get a preview page (step 324).

## Consequences

- Adding a trigger means: an event in its module, a mapping to a `NotificationRequest` in the notification module, and a template in three languages.
- Delivery is **at least once**: after a crash during sending, a message may go out twice. Dedupe keys keep reminders from being *created* twice; an occasional duplicate email is accepted.
- Messages arrive a few seconds after the action (the next dispatcher run), not instantly.
- The existing direct-SMTP listeners move onto the notifier and the outbox (steps 306–310), so they also get retries and three languages.
- The outbox and the in-app table grow over time; old sent rows are cleaned up by the data-retention job (step 399).
