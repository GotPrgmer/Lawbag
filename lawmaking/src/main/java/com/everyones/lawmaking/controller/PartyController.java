package com.everyones.lawmaking.controller;

import com.everyones.lawmaking.common.dto.response.*;
import com.everyones.lawmaking.facade.Facade;
import com.everyones.lawmaking.global.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.everyones.lawmaking.global.SwaggerConstants.EXAMPLE_ERROR_500_CONTENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/party")
@Tag(name = "정당 관련 API", description = "정당 관련 API 호출")
public class PartyController {
    private final Facade facade;

    @Operation(summary = "의원의 발의안 리스트 조회", description = "의원이 발의한 법안 데이터 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류 (문제 지속시 BE팀 문의)",
                    content = {@Content(
                            mediaType = "application/json;charset=UTF-8",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = EXAMPLE_ERROR_500_CONTENT)
                    )}
            ),
    })
    @GetMapping("/detail")
    public BaseResponse<PartyDetailResponse> getParty(@Parameter(example = "1", description = "정당 id")
                                                      @RequestParam("party_id") long partyId) {

        var response = facade.getPartyById(partyId);
        return BaseResponse.ok(response);
    }

    @Operation(summary = "정당 소속의 발의안 리스트 조회", description = "정당 의원이 발의한 법안 데이터 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류 (문제 지속시 BE팀 문의)",
                    content = {@Content(
                            mediaType = "application/json;charset=UTF-8",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = EXAMPLE_ERROR_500_CONTENT)
                    )}
            ),
    })
    @GetMapping("/bill")
    public BaseResponse<BillListResponse> getBillsByParty(@Parameter(example = "1", description = "정당 id")
                                                          @RequestParam("party_id") long partyId,
                                                          @Parameter(example = "대표", description = "공동대표발의안 또는 대표발의 의안 조회 여부")
                                                          @Schema(type = "String", allowableValues = {"represent_proposer", "public_proposer"})
                                                          @RequestParam("type") String type,
                                                          @Parameter(example = "0", description = "스크롤할 때마다 page값을 0에서 1씩 늘려주면 됩니다.")
                                                          @RequestParam(name = "page", required = true) int page,
                                                          @Parameter(example = "3", description = "한번에 가져올 데이터 크기를 의미합니다.")
                                                          @RequestParam(name = "size", required = true) int size) {
        var pageable = PageRequest.of(page, size);
        var response = (type.equals("represent_proposer")) ? facade.getRepresentativeBillsByParty(pageable, partyId)
                : facade.getPublicBillsByParty(pageable, partyId);

        return BaseResponse.ok(response);

    }

    @Operation(summary = "정당 소속의 의원들 조회", description = "정당 소속 의원들 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류 (문제 지속시 BE팀 문의)",
                    content = {@Content(
                            mediaType = "application/json;charset=UTF-8",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = EXAMPLE_ERROR_500_CONTENT)
                    )}
            ),
    })
    @GetMapping("/congressman")
    public BaseResponse<PartyCongressmanResponse> getPartyCongressman(@Parameter(example = "1", description = "정당 id")
                                                                      @RequestParam("party_id") long partyId){
        var result = facade.getPartyCongressman(partyId);
        return BaseResponse.ok(result);

    }


    @Operation(summary = "정당 팔로우", description = "특정 정당 팔로우하기")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류 (문제 지속시 BE팀 문의)",
                    content = {@Content(
                            mediaType = "application/json;charset=UTF-8",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = EXAMPLE_ERROR_500_CONTENT)
                    )}
            ),
    })
    @PatchMapping("/user/follow")
    public BaseResponse<PartyFollowResponse> followParty(
            Authentication authentication,
            @Parameter(example = "1", description = "정당 Id")
            @RequestParam("party_id") long partyId,
            @Schema(type = "boolean", allowableValues = {"true", "false"})
            @RequestParam("follow_checked") boolean followChecked
    ) {

        var userDetails = (UserDetails) authentication.getPrincipal();
        var userId = Long.parseLong(userDetails.getUsername());
        var result = facade.followParty(userId, partyId, followChecked);
        return BaseResponse.ok(result);
    }
    @Operation(summary = "원내 정당 조회", description = "원내 정당 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류 (문제 지속시 BE팀 문의)",
                    content = {@Content(
                            mediaType = "application/json;charset=UTF-8",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = EXAMPLE_ERROR_500_CONTENT)
                    )}
            ),
    })
    @GetMapping("/parliamentary")
    public BaseResponse<List<ParliamentaryPartyResponse>> getParliamentaryParty() {
        var result = facade.getParliamentaryParty();
        return BaseResponse.ok(result);
    }


}
