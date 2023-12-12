package com.everyones.lawmaking.common.dto.response;

import com.everyones.lawmaking.common.dto.BillDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import org.springframework.data.domain.Slice;

import java.util.List;


@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
public class MainFeedBillResponse {
    private PaginationResponse paginationResponse;

    private List<BillDto> Bills;

    public static MainFeedBillResponse of(PaginationResponse paginationResponse,List<BillDto> billList) {
        return MainFeedBillResponse.builder()
                .Bills(billList)
                .paginationResponse(paginationResponse)
                .build();
    }
}