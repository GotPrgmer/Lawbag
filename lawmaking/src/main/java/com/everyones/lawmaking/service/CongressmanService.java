package com.everyones.lawmaking.service;


import com.everyones.lawmaking.common.dto.PartyCongressmanDto;
import com.everyones.lawmaking.common.dto.SearchCongressmanDto;
import com.everyones.lawmaking.common.dto.response.*;
import com.everyones.lawmaking.domain.entity.Congressman;
import com.everyones.lawmaking.global.error.CongressmanException;
import com.everyones.lawmaking.repository.CongressmanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CongressmanService {
    private final CongressmanRepository congressmanRepository;
    private static final String CONGRESSMAN_ID_KEY_STRING = "congressmanId";

    public Congressman findById(String congressmanId) {
        return congressmanRepository.findByIdWithParty(congressmanId)
                .orElseThrow(() -> new CongressmanException.CongressmanNotFound(Map.of(CONGRESSMAN_ID_KEY_STRING, congressmanId)));
    }

    public Congressman getCongressman(String congressmanId) {
        var congressman = findById(congressmanId);
        return congressman;
    }

    public PartyCongressmanResponse getPartyCongressman(long partyId) {
        var congressman = congressmanRepository.findByPartyId(partyId);
        var congresssmanList = congressman.stream()
                .map(PartyCongressmanDto::from)
                .toList();
        return PartyCongressmanResponse.of(congresssmanList);
    }

    public List<LikingCongressmanResponse> getLikingCongressman(long userId){
        var likingCongressman = congressmanRepository.findLikingCongressmanByUserId(userId);
        return likingCongressman.stream()
                .map(LikingCongressmanResponse::from)
                .toList();

    }

    public SearchDataResponse searchCongressman(String searchWord) {
        var congressmanList = congressmanRepository.findBySearchWord(searchWord);
        var searchResponse =  congressmanList.stream()
                .map(SearchCongressmanDto::from)
                .toList();

        return SearchDataResponse.builder()
                .searchResponse(searchResponse)
                .build();
    }

    public int getTotalCongressmanState(boolean congressmanState){
        return congressmanRepository.countByState(congressmanState);
    }

}

