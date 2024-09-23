package com.everyones.lawmaking.service;

import com.everyones.lawmaking.common.dto.SearchPartyDto;
import com.everyones.lawmaking.common.dto.response.*;
import com.everyones.lawmaking.domain.entity.Party;
import com.everyones.lawmaking.global.error.PartyException;
import com.everyones.lawmaking.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {
    private final PartyRepository partyRepository;

    private static final String PARTY_ID_KEY_STRING = "partyId";

    public Party findById(long partyId) {
        return partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyException.PartyNotFound(Map.of(PARTY_ID_KEY_STRING, Long.toString(partyId))));
    }

    public PartyDetailResponse getPartyById(long partyId) {
        var party = partyRepository.findPartyDetailById(partyId)
                .orElseThrow(() -> new PartyException.PartyNotFound(Map.of(PARTY_ID_KEY_STRING, Long.toString(partyId))));
        return PartyDetailResponse.from(party);
    }

    public List<FollowingPartyResponse> getFollowingParty(long userId) {
        var parties = partyRepository.findFollowingPartyByUserId(userId);

        return parties.stream()
                .map(FollowingPartyResponse::from)
                .toList();
    }

    // 검색 파티
    public List<SearchResponse> searchParty(String searchWord) {
        var searchPartyList= partyRepository.findBySearchWord(searchWord);
        return searchPartyList.stream()
                .map(SearchPartyDto::from)
                .collect(Collectors.toList());
    }

    public List<String> getPartyByBillId(String billId) {
        List<String> partyImageList = partyRepository.findPartyByBillId(billId);
        if (partyImageList.isEmpty()) {
            throw new PartyException.PartyNotFound(Map.of("billId", billId));
        }
        return partyImageList;

    }

    public List<ParliamentaryPartyResponse> getParliamentaryParty() {
        return partyRepository.findByIsParliamentary(true).stream()
                .sorted(Comparator.comparing(ParliamentaryPartyResponse::getCongressmanCount).reversed())
                .toList();
    }






}
