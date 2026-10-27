package org.okq550.exchange_api.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") long id);

//    @Modifying // Telling sprint that this method is modifying not reading data
//    @Query("update Account a set a.reservedInCents = 0, a.reservedShares = 0")
//    int releaseAllReservations();
}

