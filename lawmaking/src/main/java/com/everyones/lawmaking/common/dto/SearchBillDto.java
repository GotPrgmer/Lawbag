package com.everyones.lawmaking.common.dto;

import com.everyones.lawmaking.common.dto.response.SearchResponse;
import com.everyones.lawmaking.domain.entity.Bill;
import com.everyones.lawmaking.global.constant.SearchType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
@AllArgsConstructor
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SearchBillDto implements SearchResponse {

    @NotNull
    private String id;

    @NotNull
    private String name;

    @NotNull
    private String searchType;

    @NotNull
    private LocalDate proposedDate;

    @NotNull
    private String summary;

    @NotNull
    private long likeCount;

    @NotNull
    private long viewCount;

    @NotNull
    private long partyId;

    @NotNull
    private String proposers;

    @NotNull
    private String partyName;

    @NotNull
    private String partyImageUrl;

    @NotNull
    private String congressmanImageUrl;

    @NotNull
    private String representativeProposer;

    public static SearchResponse from(Bill bill) {

        var congressMan = bill.getRepresentativeProposer().getCongressman();
        var party = bill.getParty();

        return SearchBillDto.builder()
                .id(bill.getId())
                .name(bill.getBillName())
                .searchType(SearchType.BILL.name())
                .proposedDate(bill.getProposeDate())
                .summary(bill.getSummary())
                .likeCount(bill.getLikeCount())
                .viewCount(bill.getViewCount())
                .proposers(bill.getProposers())
                .representativeProposer(congressMan.getName())
                .congressmanImageUrl(congressMan.getCongressmanImageUrl())
                .partyId(party.getId())
                .partyName(party.getName())
                .partyImageUrl(party.getPartyImageUrl())
                .build();
    }
}