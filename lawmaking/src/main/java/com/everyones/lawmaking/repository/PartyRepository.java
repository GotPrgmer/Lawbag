package com.everyones.lawmaking.repository;

import com.everyones.lawmaking.common.dto.response.ParliamentaryPartyResponse;
import com.everyones.lawmaking.domain.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {


    @Query("SELECT p " +
            "FROM Party p " +
            "WHERE p.id = :partyId")
    Optional<Party> findPartyDetailById(@Param("partyId") long partyId);

    @Query("SELECT p FROM Party p " +
            "JOIN p.partyFollow pf " +
            "WHERE pf.user.id = :userId")
    List<Party> findFollowingPartyByUserId(@Param("userId") long userId);

    @Query("SELECT p FROM Party p " +
            "WHERE p.name LIKE %:searchWord% ")
    List<Party> findBySearchWord(@Param("searchWord") String searchWord);


    @Query("SELECT distinct p.partyImageUrl " +
            "from Party p " +
            "join Congressman c on c.party.id = p.id " +
            "join RepresentativeProposer rp on rp.congressman.id =c.id " +
            "where rp.bill.id =:billId")
    List<String> findPartyByBillId(@Param("billId") String billId);

    Optional<Party> findPartyByName(String partyName);
    @Query("SELECT new com.everyones.lawmaking.common.dto.response.ParliamentaryPartyResponse(p.id, p.name, p.partyImageUrl, COUNT(c)) " +
            "FROM Party p " +
            "JOIN p.congressmanList c GROUP BY p " +
            "having p.isParliamentary = true ")
    List<ParliamentaryPartyResponse> findByIsParliamentary(boolean isParliamentary);

}
