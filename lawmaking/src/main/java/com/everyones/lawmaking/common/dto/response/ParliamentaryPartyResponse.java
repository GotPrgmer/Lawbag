package com.everyones.lawmaking.common.dto.response;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ParliamentaryPartyResponse {
    private long partyId;
    private String partyName;
    private String partyImageUrl;
    private Long congressmanCount;

    public ParliamentaryPartyResponse(long partyId, String partyName, String partyImageUrl, Long congressmanCount) {
        this.partyId = partyId;
        this.partyName = partyName;
        this.partyImageUrl = partyImageUrl;
        this.congressmanCount = congressmanCount;
    }
}
