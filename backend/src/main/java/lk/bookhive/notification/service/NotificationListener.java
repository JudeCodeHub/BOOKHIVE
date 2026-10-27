package lk.bookhive.notification.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Put on every method that turns an event into a notification (ADR 0004, checklist step 306). It
 * combines the three things such a listener always needs:
 *
 * <ol>
 *   <li><b>After commit</b>: runs only once the change the event talks about is saved, so nobody is
 *       told about a checkout that rolled back.
 *   <li><b>Fallback execution</b>: also runs when the event was published outside a transaction
 *       (e.g. by a job); a plain after-commit listener would silently drop it.
 *   <li><b>Its own transaction</b> ({@code REQUIRES_NEW}): after the commit, the original
 *       transaction is finished, so writes (in-app messages, outbox rows) need a new one, or they
 *       would silently not be saved.
 * </ol>
 *
 * A failure here can't undo the original change (it's already committed); the notifier logs it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public @interface NotificationListener {}
