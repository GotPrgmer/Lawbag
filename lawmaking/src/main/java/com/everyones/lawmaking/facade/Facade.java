package com.everyones.lawmaking.facade;

import com.everyones.lawmaking.common.dto.BillDto;
import com.everyones.lawmaking.common.dto.request.*;
import com.everyones.lawmaking.common.dto.response.*;
import com.everyones.lawmaking.domain.entity.ColumnEventType;
import com.everyones.lawmaking.domain.entity.User;
import com.everyones.lawmaking.global.error.AuthException;
import com.everyones.lawmaking.global.util.AuthenticationUtil;
import com.everyones.lawmaking.global.util.NullUtil;
import com.everyones.lawmaking.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class Facade {
    private final PartyService partyService;
    private final BillService billService;
    private final CongressmanService congressmanService;
    private final LikeService likeService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final AuthService authService;
    private final DataService dataService;
    private final RedisService redisService;
    private final RepresentativeProposerService representativeProposerService;
    private final BillProposerService billProposerService;

    public BillListResponse findByPage(Pageable pageable) {
        var billListResponse = billService.findByPage(pageable);
        return setBillListResponseBookMark(billListResponse);
    }

    // 법안의 처리 단계에 따라 법안 페이징해서 법안 반환
    public BillListResponse findByPage(Pageable pageable, String stage) {
        var billListResponse = billService.findByPage(pageable, stage);
        return setBillListResponseBookMark(billListResponse);
    }

    // 법안 아이디를 기준으로, 하나의 법안 상세 조회
    public BillDetailResponse getBillByBillId(String billId) {
        var billDto =  billService.getBillWithDetail(billId);
        var userId = AuthenticationUtil.getUserId();
        if (userId.isEmpty()) {
            return billDto;
        }
        var isBookMark = likeService.getBillLikeChecked(billDto.getBillInfoDto().getBillId(), userId.get());
        billDto.setIsBookMark(isBookMark);
        return billDto;
    }

    // 법안 조회수 증가
    public BillViewCountResponse updateViewCount(String billId) {
        redisService.addToViewsQueue(billId);
        return billService.updateViewCount(billId);
    }

    // Congressman 대표 발의 가져오기
    public BillListResponse getBillsFromRepresentativeProposer(String congressmanId, Pageable pageable) {
        var billListResponse = billService.getBillInfoFromRepresentativeProposer(congressmanId, pageable);
        return setBillListResponseBookMark(billListResponse);
    }

    // Congressman 공동 발의 법안 가져오기
    public BillListResponse getBillsFromPublicProposer(String congressmanId, Pageable pageable) {
        var billListResponse = billService.getBillInfoFromPublicProposer(congressmanId, pageable);
        return setBillListResponseBookMark(billListResponse);
    }

    // 의원 상세 조회
    public CongressmanResponse getCongressman(String congressmanId) {
        var congressman = congressmanService.getCongressman(congressmanId);
        var followCount = likeService.getCongressmanFollowerCount(congressmanId);
        var representCount = representativeProposerService.countBillByCongressmanAsPublicProposer(congressmanId);
        var publicCount = billProposerService.countBillByCongressmanAsPublicProposer(congressmanId);
        var userId = AuthenticationUtil.getUserId();
        if (userId.isEmpty()) {
            return CongressmanResponse.of(congressman, followCount, representCount, publicCount);
        }
        var congressmanLike = likeService.getCongressmanLike(congressmanId, userId.get());
        return CongressmanResponse.of(congressman, followCount, representCount, publicCount, congressmanLike);
    }
    public CountDto getCongressmanLikeCount() {
        var userId = AuthenticationUtil.getUserId();
        if (userId.isEmpty()) {
            throw new AuthException.AuthInfoNotFound();
        }
        return likeService.getCongressmanLikeCount(userId.get());
    }

    // 정당 상세 조회
    public PartyDetailResponse getPartyById(long partyId) {
        var partyDetailResponse =  partyService.getPartyById(partyId);
        var totalCongressmanCount = congressmanService.getTotalCongressmanState(true);
        partyDetailResponse.setTotalCongressmanCount(totalCongressmanCount);
        var userId = AuthenticationUtil.getUserId();
        if (userId.isEmpty()) {
            return partyDetailResponse;
        }
        var followChecked = likeService.getFollowParty(partyId, userId.get());
        partyDetailResponse.setFollowed(followChecked);
        return partyDetailResponse;
    }

    //
    public BillListResponse getRepresentativeBillsByParty(Pageable pageable, long partyId) {
        var billListResponse = billService.getRepresentativeBillsByParty(pageable, partyId);
        return setBillListResponseBookMark(billListResponse);
    }

    public BillListResponse getPublicBillsByParty(Pageable pageable, long partyId) {
        var billListResponse = billService.getPublicBillsByParty(pageable, partyId);
        return setBillListResponseBookMark(billListResponse);
    }

    public PartyCongressmanResponse getPartyCongressman(long partyId) {
        return congressmanService.getPartyCongressman(partyId);
    }

    // 법안 좋아요 기능
    @Transactional
    public BillLikeResponse likeBill(long userId, String billId, boolean likeChecked) {
        var user = userService.findById(userId);
        var bill = billService.findById(billId);
        if (likeChecked) {
            redisService.addToLikesQueue(billId);
        }
        return likeService.likeBill(user, bill, likeChecked);
    }

    // 의원 좋아요 기능
    @Transactional
    public CongressmanLikeResponse likeCongressman(long userId, String congressmanId, boolean likeChecked) {
        var user = userService.findById(userId);
        var congressman = congressmanService.findById(congressmanId);
        return likeService.likeCongressman(user, congressman, likeChecked);
    }

    // 정당 팔로우 기능
    @Transactional
    public PartyFollowResponse followParty(long userId, long partyId, boolean followChecked) {
        var user = userService.findById(userId);
        var party = partyService.findById(partyId);
        return likeService.followParty(user, party, followChecked);
    }

    // 팔로우한 의원 조회
    public List<LikingCongressmanResponse> getLikingCongressman(long userId) {
        return congressmanService.getLikingCongressman(userId);
    }
    // 북마크한 법안 조회
    // 좋아요를 한 날짜 순서대로 조회하는게 맞나? 근데 그러면 너무 로직이 복잡해짐...
    public BillListResponse getBookmarkedBills(Pageable pageable, long userId) {
        var billListResponse = billService.getBookmarkingBills(pageable, userId);
        return setBillListResponseBookMark(billListResponse);
    }

    // 팔로우한 정당 조회
    public List<FollowingPartyResponse> getFollowingParty(long userId) {
        return partyService.getFollowingParty(userId);
    }

    // 마에 페이지 유저 정보 조회
    public UserMyPageInfoResponse getUserMyPageInfo(long userId) {
        return userService.getUserMyPageInfo(userId);
    }

    // 검색 정당 및 의원 조회
    public SearchDataResponse searchCongressmanAndParty(String searchWord) {
        var searchDataResponse = congressmanService.searchCongressman(searchWord);
            var searchPartyList = partyService.searchParty(searchWord);
            searchDataResponse.setSearchResponse(Stream
                    .concat(searchDataResponse.getSearchResponse().stream(), searchPartyList.stream())
                    .toList());
        return searchDataResponse;

    }

    public CountDto getBookmarkedBillCount() {
        var userId = AuthenticationUtil.getUserId();
        return likeService.getBookmarkedBillCount(userId.get());
    }

    // 검색 법안 조회
    public SearchBillResponse searchBill(String searchWord,int page) {

        Pageable pageable = PageRequest.of(page, 5);
        var billList = billService.searchBill(searchWord, pageable);
        var billListByUser = setBillListResponseBookMark(billList);

        return SearchBillResponse.builder()
                .paginationResponse(billList.getPaginationResponse())
                .searchResponse(billListByUser.getBillList())
                .build();
    }

    // 알림 조회
    public List<NotificationResponse> getNotifications(long userId){
        return notificationService.getNotifications(userId);
    }

    // 알림 읽음 처리
    public List<NotificationResponse> readNotifications(long userId) {
        return notificationService.readNotifications(userId);
    }

    // 알림 데이터를 각 테이블에 해당하는 실제 데이터로 변환 (ex : bill_id
    public List<String> convertRepresentativeBillNotification(List<String> relatedEntityIds) {
        var congressman = congressmanService.findById(relatedEntityIds.get(0));
        var billRepProposer = congressman.getName();
        var bill = billService.findById(relatedEntityIds.get(1));
        var billId = bill.getId();
        var billName = bill.getBillName();
        var billProposers = bill.getProposers();

        String partyImageUrl = NullUtil.nullCoalescing(() -> congressman.getParty().getPartyImageUrl(), "defaultImageUrl");

        return List.of(billId, billRepProposer, billProposers, billName, partyImageUrl);
    }


    public List<String> updateBillStage(List<String> relatedEntityIds) {
        var partyImageList = partyService.getPartyByBillId(relatedEntityIds.get(0));

        return Stream.concat(relatedEntityIds.stream(), partyImageList.stream())
                .toList();
    }

    public List<String> updateCongressmanParty(List<String> relatedEntityIds) {
        var congressman = congressmanService.findById(relatedEntityIds.get(0));
        var congressmanId = congressman.getId();
        var congressmanName = congressman.getName();

        var party = partyService.findById(Long.parseLong(relatedEntityIds.get(1)));
        var partyName = party.getName();
        String partyImageUrl = NullUtil.nullCoalescing(party::getPartyImageUrl, "defaultImageUrl");
        return List.of(congressmanId, partyName, congressmanName, partyImageUrl);
    }

    public List<String> getProcessedData(ColumnEventType cet,List<String> eventData) {
        return switch (cet) {
            case RP_INSERT -> convertRepresentativeBillNotification(eventData);
            case CONGRESSMAN_PARTY_UPDATE -> updateCongressmanParty(eventData);
            case BILL_STAGE_UPDATE -> updateBillStage(eventData);
        };

    }

    public List<User> getSubscribedUsers(ColumnEventType cet, String targetId) {
        return switch (cet) {
            case RP_INSERT, CONGRESSMAN_PARTY_UPDATE -> userService.getUserByLikedCongressmanId(targetId);
            case BILL_STAGE_UPDATE -> userService.getUserByLikedBillId(targetId);
        };
    }

    private BillListResponse setBillListResponseBookMark(BillListResponse billListResponse) {
        var userId = AuthenticationUtil.getUserId();

        if (userId.isEmpty()) {
            return billListResponse;
        }
        var billList = billListResponse.getBillList().stream()
                .map(billDto -> {
                    var isBookMark = likeService.getBillLikeChecked(billDto.getBillInfoDto().getBillId(), userId.get());
                    billDto.setIsBookMark(isBookMark);
                    return billDto;
                })
                .toList();
        billListResponse.setBillList(billList);
        return billListResponse;
    }



    public WithdrawResponse withdraw(String userId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws Exception {
        return authService.withdraw(userId,httpRequest, httpResponse);

    }


    public void reissueToken(HttpServletRequest httpServletRequest,HttpServletResponse httpServletResponse){
        authService.reissueToken(httpServletRequest, httpServletResponse);
    }

    public void insertBillInfoDf(List<BillDfRequest> billDfRequestList) {
        dataService.insertBillInfoDf(billDfRequestList);
    }


    public Map<String, List<String>> updateBillStageDf(List<BillStageDfRequest> billStageDfRequestList) {
        // list 길이가 1이상이면
        return dataService.updateBillStageDf(billStageDfRequestList);
    }

    public void updateBillResultDf(List<BillResultDfRequest> billResultDfRequestList) {
        // list 길이가 1이상이면
        dataService.updateBillResultDf(billResultDfRequestList);
    }

    public void updateLawmakerDf(List<LawmakerDfRequest> lawmakerDfRequestList) {
        dataService.updateLawmakerDf(lawmakerDfRequestList);
    }

    public List<String> insertAssemblyVote(List<VoteDfRequest> voteDfRequestList){
        return dataService.insertAssemblyVote(voteDfRequestList);

    }

    public List<String> insertVoteIndividual(List<VotePartyRequest> votePartyRequestList){
        return dataService.insertVoteParty(votePartyRequestList);

    }

    public void updateCongressmanCountByParty(){
        dataService.updateCongressmanCountByParty();
    }
    public void updateBillCountByParty(){
        dataService.updateBillCountByParty();
    }

    public void updateProposeDateByCongressman(){
        dataService.updateProposeDateByCongressman();
    }




    public List<BillDto> getPopularBills() {
        var popularBillIds = redisService.getPopularBills();
        var billList = billService.getBillListResponse(popularBillIds);
        var userIdOptional = AuthenticationUtil.getUserId();

        return userIdOptional.map(userId -> billList.stream()
                .map(billDto -> {
                    var isBookMark = likeService.getBillLikeChecked(billDto.getBillInfoDto().getBillId(), userId);
                    billDto.setIsBookMark(isBookMark);
                    return billDto;
                })
                .toList()).orElse(billList);
    }

    // 팔로우한 의원들의 대표발의안을 최신순으로 페이징해서 가져옴
    public BillListResponse getBillsFromFollowingTab(Pageable pageable) {
        var billList =  billService.findByUserAndCongressmanLike(pageable);
        return setBillListResponseBookMark(billList);
    }

    public List<ParliamentaryPartyResponse> getParliamentaryParty() {
        return partyService.getParliamentaryParty();
    }



}
