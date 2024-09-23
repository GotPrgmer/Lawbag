package com.everyones.lawmaking.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;


@AllArgsConstructor
@Getter
public enum ColumnEventType {
    RP_INSERT("rp_insert",
            "RepresentativeProposer",
            null,
            EventType.INSERT,
            List.of("congressman_id","bill_id")),
    BILL_STAGE_UPDATE(
            "bill_stage_update",
            "Bill",
            "stage",
            EventType.UPDATE,
            List.of("bill_id", "bill_name", "proposers", "stage")),
    CONGRESSMAN_PARTY_UPDATE(
            "congressman_party_update",
            "Congressman",
            "party_id",
            EventType.UPDATE,
            List.of("congressman_id", "party_id", "name"));

    public enum EventType {
        INSERT, UPDATE, DELETE
    }
    private final String eventName;
    private final String tableName;
    private final String columnName;
    private final EventType eventType;
    private final List<String> resultColumnNames;

    public static ColumnEventType from(String code) {
        return Arrays.stream(ColumnEventType.values())
                .filter(cet -> cet.getEventName().equals(code))
                .findAny()
                .orElseThrow();
    }

}