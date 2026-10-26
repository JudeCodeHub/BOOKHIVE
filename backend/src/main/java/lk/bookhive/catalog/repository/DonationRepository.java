package lk.bookhive.catalog.repository;

import java.util.UUID;
import lk.bookhive.catalog.domain.Donation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository extends JpaRepository<Donation, UUID> {

  Page<Donation> findByDonorNameContainingIgnoreCase(String namePart, Pageable pageable);
}
