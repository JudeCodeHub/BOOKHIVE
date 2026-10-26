package lk.bookhive.community.repository;

import java.util.UUID;
import lk.bookhive.community.domain.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {}
