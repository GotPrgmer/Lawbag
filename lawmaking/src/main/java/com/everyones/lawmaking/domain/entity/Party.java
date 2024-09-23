package com.everyones.lawmaking.domain.entity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;

import java.util.List;

@Entity
@Data
@ToString
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Party extends BaseEntity{
    @Version
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "party_id")
    private long id;


    @OneToMany(mappedBy = "party")
    private List<Congressman> congressmanList;

    @OneToMany(mappedBy = "party")
    private List<PartyFollow> partyFollow;

    @OneToMany(mappedBy = "party")
    private List<VoteParty> votePartyList;


    private String name;

    @Column(name = "party_image_url")
    private String partyImageUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @ColumnDefault("0")
    @Builder.Default
    @Column(name = "proportional_congressman_count")
    private int proportionalCongressmanCount = 0;

    @ColumnDefault("0")
    @Builder.Default
    @Column(name = "district_congressman_count")
    private int districtCongressmanCount = 0;

    @ColumnDefault("0")
    @Builder.Default
    @Column(name = "representative_bill_count")
    private int representativeBillCount = 0;

    @ColumnDefault("0")
    @Builder.Default
    @Column(name = "public_bill_count")
    private int publicBillCount = 0;

    @ColumnDefault("0")
    @Column(name = "is_parliamentary")
    private Boolean isParliamentary;

    public static Party create(String partyName ){
        return Party.builder()
                .name(partyName)
                .build();
    }

    public void updateCongressmanCount(int districtCongressmanCount, int proportionalCongressmanCount){
        this.setDistrictCongressmanCount(districtCongressmanCount);
        this.setProportionalCongressmanCount(proportionalCongressmanCount);
    }

    public void updateBillCount(int representativeBillCount, int publicBillCount){
        this.setRepresentativeBillCount(representativeBillCount);
        this.setPublicBillCount(publicBillCount);
    }



    @Override
    public String toString() {
        return "Party{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", districtCongressmanCount=" + districtCongressmanCount +
                ", proportionalCongressmanCount=" + proportionalCongressmanCount +
                '}';
    }

}
