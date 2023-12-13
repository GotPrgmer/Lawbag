package com.everyones.lawmaking.common.dto;

import com.everyones.lawmaking.domain.entity.Bill;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
//TODO: 추후 의원 이미지 및 정당 이미지 주소 넣어야됨.
public class BillDto {
    private String billId;
    private String billName;
    private String representProposer;
    private String representProposerId;
    private String representProposerParty;
    private long representProposerPartyId;
    private String representProposerImgUrl = "";
    private String proposers;
    private List<String> partyList;
    private List<Long> partyIdList;
    private String summary;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
    private LocalDate proposeDate;
    private int view;
    private int like;

    // Repository에서 데이터 가져오기 위한 생성자
    public BillDto(String billId, String name, String representProposer, String representProposerId, String representProposerParty, Long representProposerPartyId, String proposers, String summary, LocalDate proposeDate) {
        this.billId = billId;
        this.billName = name;
        this.representProposer = representProposer.substring(0, 3);
        this.representProposerId = representProposerId;
//        this.representProposerImageUrl = representProposerImageUrl;
        this.representProposerParty = representProposerParty;
        this. representProposerPartyId = representProposerPartyId;
        this.proposers = proposers;
        this.summary = summary;
        this.proposeDate = proposeDate;
        this.view = 0;
        this.like = 0;
    }



}
