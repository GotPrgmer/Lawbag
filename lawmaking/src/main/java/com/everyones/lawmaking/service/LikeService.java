package com.everyones.lawmaking.service;

import com.everyones.lawmaking.common.dto.response.BillLikeResponse;
import com.everyones.lawmaking.common.dto.response.CongressmanLikeResponse;
import com.everyones.lawmaking.domain.entity.*;
import com.everyones.lawmaking.repository.BillLikeRepository;
import com.everyones.lawmaking.repository.CongressmanLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LikeService {
    private final BillLikeRepository billLikeRepository;
    private final CongressmanLikeRepository congressmanLikeRepository;

    @Transactional
    public BillLikeResponse likeBill(User user, Bill bill) {
        var billLike = billLikeRepository.findByUserIdAndBillId(user.getId(), bill.getId());

        return billLike.isPresent() ? updateBillLike(billLike.get()) : createBillLike(user, bill);
    }

    @Transactional
    public CongressmanLikeResponse likeCongressman(User user, Congressman congressman) {
        var congressmanLike = congressmanLikeRepository.findByUserIdAndCongressmanId(user.getId(), congressman.getId());

        return congressmanLike.isPresent() ? updateCongressmanLike(congressmanLike.get()) : createCongressmanLike(user ,congressman);
    }

    private CongressmanLikeResponse createCongressmanLike(User user, Congressman congressman) {
        var congressmanLike = CongressManLike.builder()
                .congressMan(congressman)
                .user(user)
                .likeChecked(true)
                .build();
        congressmanLikeRepository.save(congressmanLike);

        return CongressmanLikeResponse.from(congressmanLike);
    }

    private CongressmanLikeResponse updateCongressmanLike(CongressManLike congressmanLike) {
        var updatedCongressmanLike = CongressManLike.builder()
                .id(congressmanLike.getId())
                .congressMan(congressmanLike.getCongressMan())
                .user(congressmanLike.getUser())
                .likeChecked(!congressmanLike.isLikeChecked())
                .build();
        congressmanLikeRepository.save(updatedCongressmanLike);
        return CongressmanLikeResponse.from(updatedCongressmanLike);
    }

    private BillLikeResponse createBillLike(User user, Bill bill) {
        var billLike = BillLike.builder()
                .bill(bill)
                .user(user)
                .likeChecked(true)
                .build();
        billLikeRepository.save(billLike);

        return BillLikeResponse.from(billLike);
    }


    private BillLikeResponse updateBillLike(BillLike billLike) {
        var updatedBillLike = BillLike.builder()
                .id(billLike.getId())
                .bill(billLike.getBill())
                .user(billLike.getUser())
                .likeChecked(!billLike.isLikeChecked())
                .build();
        billLikeRepository.save(updatedBillLike);
        return BillLikeResponse.from(updatedBillLike);
    }


}
