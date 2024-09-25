package com.everyones.lawmaking.repository;

import com.everyones.lawmaking.domain.entity.Congressman;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CongressmanRepository extends JpaRepository<Congressman, String> {

    @Query("SELECT c FROM Congressman c LEFT JOIN FETCH c.party WHERE c.id = :congressmanId")
    Optional<Congressman> findByIdWithParty(@Param("congressmanId") String congressmanId);

    @Query("SELECT c FROM Congressman c JOIN FETCH c.party " +
            "JOIN c.congressmanLike cl " +
            "WHERE cl.user.id = :userId")
    List<Congressman> findLikingCongressmanByUserId(@Param("userId") long userId);

    @Query("SELECT c FROM Congressman c " +
            "JOIN FETCH c.party p " +
            "WHERE c.name LIKE %:searchWord% " )
    Slice<Congressman> findBySearchWord(@Param("searchWord") String searchWord);


    @Query("SELECT c FROM Congressman c " +
            "JOIN c.party p WHERE p.id = :partyId " +
            "and c.state = true " +
            "ORDER BY c.name ")
    Slice<Congressman> findByPartyId(@Param("partyId") long partyId);

    @Query("SELECT c FROM Congressman c " +
            "Where c.state = true and c.name =:congressmanName ")
    Optional<Congressman> findLawmakerByName(String congressmanName);


    Optional<Congressman> findCongressmanById(String congressmanId);

    @Query("SELECT c.id FROM Congressman c ")
    Set<String> findAllCongressmanId();

    Integer countByState(boolean state);

}
