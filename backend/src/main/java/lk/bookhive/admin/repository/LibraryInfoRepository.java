package lk.bookhive.admin.repository;

import lk.bookhive.admin.domain.LibraryInfo;
import org.springframework.data.jpa.repository.JpaRepository;

/** One row (id = 1). Read and written through LibraryInfoService. */
public interface LibraryInfoRepository extends JpaRepository<LibraryInfo, Short> {}
