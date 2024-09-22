package com.everyones.lawmaking.global.config;

import com.everyones.lawmaking.domain.entity.ColumnEventType;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TableEventListener {
    /**
     * DB host(ipv4)
     */
    @Value("${binlog.host}")
    private String host;
    /**
     * DB port
     */
    @Value("${binlog.port}")
    private int port;
    /**
     * DB username
     */
    @Value("${binlog.user}")
    private String user;
    /**
     * DB password
     */
    @Value("${binlog.password}")
    private String password;

    @Value("${db-connection-pool.db-name}")
    private String dbName;

    private final Set<EventType> recordedEventTypes = Set.of(EventType.EXT_WRITE_ROWS, EventType.EXT_UPDATE_ROWS, EventType.EXT_DELETE_ROWS, EventType.TABLE_MAP);
    private final Map<Long, TableMapInfo> relatedTableMapEvents = new HashMap<>();

    private final NotificationCreator notificationCreator;
    private final DataSource dataSource;

    /**
     * 테이블별 (컬럼네임,컬럼 인덱스) 정보 가져오는 메서드
     *
     * @return Map(테이블명, Map ( 컬럼명, 컬럼 인덱스))
     */
    Map<String, Map<String, Integer>> fetchColumnOrdersByTable() {
        Map<String, Map<String, Integer>> columnOrdersByTable = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tableResultSet = metaData.getTables(dbName, "public", null, new String[]{"TABLE"})) {
                // metaData의 테이블 정보를 가져옴
                while (tableResultSet.next()) {
                    // 테이블 이름을 tableName에 저장
                    String tableName = tableResultSet.getString("TABLE_NAME").toLowerCase();

                    // 컬럼 네임별 인덱스 저장할 해시맵 생성
                    Map<String, Integer> columnOrders = new HashMap<>();

                    // 해당 table의 컬럼 가져와서 try-with 구문 시작
                    try (ResultSet columnResultSet = metaData.getColumns(dbName, "public", tableName, null)) {
                        // 컬럼 이름과 컬럼 매칭 인덱스를 맵에 저장
                        while (columnResultSet.next()) {
                            columnOrders.put(columnResultSet.getString("COLUMN_NAME").toLowerCase(), columnResultSet.getInt("ORDINAL_POSITION") - 1);
                        }
                    }
                    // 테이블 당 컬럼들의 네임과 인덱스를 저장한 해시맵을 put
                    columnOrdersByTable.put(tableName, Collections.unmodifiableMap(columnOrders));
                }

            }
        } catch (SQLException e) {
            log.error("Failed to fetch column orders by table", e);
            throw new RuntimeException(e);
        }
        return Collections.unmodifiableMap(columnOrdersByTable);
    }


    @Bean(destroyMethod = "disconnect")
    BinaryLogClient binaryLogClient() throws IOException {
        final Map<String, Map<String, Integer>> columnOrdersByTable = fetchColumnOrdersByTable();

        final List<ColumnEventType> watchedColumnEvents = List.of(ColumnEventType.values());

        final List<String> watchedTableNames = watchedColumnEvents.stream().map(ColumnEventType::getTableName).toList();

        BinaryLogClient logClient = new BinaryLogClient(
                host,
                port,
                user,
                password);

        // 받은 데이터를 BYTE 로 표현
        EventDeserializer eventDeserializer = new EventDeserializer();
        eventDeserializer.setCompatibilityMode(
                EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
                EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY
        );
        List<Event> dmlEvents = new ArrayList<>();  // DML 이벤트 저장 리스트


        logClient.registerEventListener(event -> {

            // 이벤트 타입에 따라서 Data가 없는 것도 있음.
            // 따라서 바로 event.getData를 호출하는 것은 주의
            final EventType eventType = event.getHeader().getEventType();
            if (eventType == EventType.WRITE_ROWS || eventType == EventType.UPDATE_ROWS || eventType == EventType.DELETE_ROWS) {
                dmlEvents.add(event);  // DML 이벤트 저장
            }

            // XID 이벤트 발생 시 트랜잭션 커밋

            if (eventType == EventType.XID) {
                dmlEvents.forEach(dmlEvent -> {
                    // DML 이벤트에서 데이터를 추출하여 로그 출력
                    if (dmlEvent.getData() instanceof WriteRowsEventData) {
                        WriteRowsEventData data = (WriteRowsEventData) dmlEvent.getData();
                        data.getRows().forEach(row -> log.info("Inserted row: " + Arrays.toString(row)));
                    } else if (dmlEvent.getData() instanceof UpdateRowsEventData) {
                        UpdateRowsEventData data = (UpdateRowsEventData) dmlEvent.getData();
                        data.getRows().forEach(row -> log.info("Updated row before: " + Arrays.toString(row.getKey()) +
                                ", after: " + Arrays.toString(row.getValue())));
                    }
                    // DeleteRowsEventData 처리 추가 가능
                });

                dmlEvents.clear();  // 트랜잭션 완료 후 DML 이벤트 리스트 초기화

                List<Map<String, List<String>>> filteredValuesByRows = new ArrayList<>();

                // Operation에 따라서 before after 변화를 감지.
                relatedTableMapEvents.forEach((_tableId, tableMapInfo) -> {
                    // 여기서 매핑정보들 테이블id 와 before after 변화 감지해서 원하는 알림처리 메소드 호출
                    final TableMapEventData tableMapEventData = tableMapInfo.getTableMapEventData();

                    final String tableName = tableMapEventData.getTable().toLowerCase();
                    // 이벤트 감지가 필요없는 테이블이면 스킵
                    // tableName은 all 소문자로 옴
                    if (!watchedTableNames.contains(tableName)) return;

                    final Set<Event> dmlOperations = tableMapInfo.getDmlEvents();
                    //TODO: 시간 순서대로 리스트를 받지 않는 문제가 있음 만약 수정 삭제 생성이 한 트랜잭션에서 여러 작업이 동시에 일어나는 경우 잘못된 알림이 발생할 경우가 있음.
                    dmlOperations.stream()
                            .sorted(Comparator.comparing(e -> e.getHeader().getTimestamp()))
                            .forEach(e -> {
                        final EventData data = e.getData();
                        // 하나의 트랜잭션에는 여러개의 로우가 관여할 수 있음, 또한 하나의 Serializable 객체는 하나의 컬럼 값임.
                        if (data instanceof WriteRowsEventData insertedData) {
                            List<Serializable[]> rows = insertedData.getRows();

                            // WriteRows에 관한 알림처리할 컬럼 인덱스 필터링
                            // 알림 데이터로 필요한 (이벤트네임, 컬럼 인덱스)를 저장
                            Map<String, List<Integer>> resultColumnsIndicesByEvent = watchedColumnEvents
                                    .stream()
                                    .filter(wce -> tableName.equalsIgnoreCase(wce.getTableName())
                                            && wce.getEventType() == ColumnEventType.EventType.INSERT)
                                    .map(wce -> {
                                        final Map<String, Integer> columnOrdersForTable = columnOrdersByTable.getOrDefault(tableName, new HashMap<>());
                                        return new AbstractMap.SimpleEntry<>(
                                                wce.getEventName(),
                                                wce.getResultColumnNames()
                                                        .stream()
                                                        .map(columnName ->
                                                                columnOrdersForTable.getOrDefault(columnName.toLowerCase(), -1))
                                                        .toList()

                                        );
                                    })
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            // 이벤트 타입, 필터된 컬럼데이터
                            filteredValuesByRows.addAll(
                                    rows.stream()
                                            .map(row -> {
                                                final Map<String, List<String>> filteredValues = new HashMap<>();
                                                resultColumnsIndicesByEvent.forEach((eventName, columnIndices) -> {
                                                    final List<String> values = new ArrayList<>();
                                                    //관련된 컬럼 인덱스 리스트인 columnIndices와 매칭되는 인덱스의 데이터값을 values에 넣기
                                                    columnIndices.stream().filter(index -> index >= 0).forEach((index) -> values.add(row[index].toString()));
                                                    filteredValues.put(eventName, Collections.unmodifiableList(values));
                                                });
                                                return Collections.unmodifiableMap(filteredValues);
                                            })
                                            .toList());


                            // 필요한 테이블 : bill, CongressMan
                            // 컬럼 : 의원이 새로운 법안을 발의함(bill의 bill_name), 의원이 새로 추가됨(name)
                            // 알림 보내야할 테이블이면 bill_name 혹은 의원 이름을 추출해서 알림 메시지로 보내버림


                            // @TODO: 데이터 삭제 알림 필요시 주석 해제
                            //} else if (data instanceof DeleteRowsEventData) {
                            //final DeleteRowsEventData insertedData = (DeleteRowsEventData) data;
                        } else if (data instanceof UpdateRowsEventData changedData) {

                            // 필요한 테이블 : bill, CongressMan
                            // 컬럼 : 법안의 처리상태 변동(bill의 bill_name, stage), 의원의 정당 바뀜(이름, 바뀐정당)

                            //List로 스트림 건 후 before after를 지정 컬럼으로 확인
                            // 알림 이벤트 타입이랑 인덱스 추출
                            Map<String, List<Integer>> resultColumnsIndicesByEvent = watchedColumnEvents
                                    .stream()
                                    .filter(wce -> tableName.equalsIgnoreCase(wce.getTableName())
                                            && wce.getEventType() == ColumnEventType.EventType.UPDATE)
                                    .map(wce -> {
                                        final Map<String, Integer> columnOrdersForTable = columnOrdersByTable.getOrDefault(tableName, new HashMap<>());
                                        return new AbstractMap.SimpleEntry<>(
                                                wce.getEventName(),
                                                Stream.concat(
                                                        Stream.of(columnOrdersForTable.getOrDefault(wce.getColumnName().toLowerCase(), -1)),
                                                        wce.getResultColumnNames()
                                                                .stream()
                                                                .map(columnName ->
                                                                        columnOrdersForTable.getOrDefault(columnName.toLowerCase(), -1))
                                                ).toList()
                                        );
                                    })
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


                            List<Map.Entry<Serializable[], Serializable[]>> rows = changedData.getRows();

                            //이벤트 타입, 필터된 컬럼데이터
                            //Before Rows와 After Rows의 지정 인덱스가 바뀌었는지 변화 감지
                            //감지한 컬럼 인덱스로 컬럼 내용 변화 감지.
                            filteredValuesByRows.addAll(
                                    rows.stream()
                                            .map(row -> {
                                                // key와 value가 각각 before after
                                                Serializable[] before = row.getKey();
                                                Serializable[] after = row.getValue();

                                                final Map<String, List<String>> filteredValues = new HashMap<>();

                                                resultColumnsIndicesByEvent.forEach((eventName, columnIndices) -> {
                                                    // 각 행의 변화감지 인덱스를 넣어서 만약에 바뀌면 아래를 실행
                                                    int watchedColumnIndex = columnIndices.get(0);
                                                    if (!Objects.equals(before[watchedColumnIndex], after[watchedColumnIndex])) {
                                                        final List<String> values = new ArrayList<>();
                                                        columnIndices.stream()
                                                                .skip(1)
                                                                .filter(index -> index >= 0)
                                                                .forEach(index -> values.add(after[index].toString()));
                                                        filteredValues.put(eventName, Collections.unmodifiableList(values));
                                                    }
                                                });
                                                return Collections.unmodifiableMap(filteredValues);
                                            })
                                            .toList());
                        }
                        // TODO: DeleteRowsEventData 처리 필요 시 추가
                    });
                });


                Thread notificationCreatorThread = new Thread(()->{

                    int delay = 100;  // 초기 지연 시간
                    int maxDelay = 1000;  // 최대 지연 시간
                    while (delay <= maxDelay) {
                        try {
                            Thread.sleep(delay);
                            notificationCreator.createNotification(filteredValuesByRows);
                            break;  // 성공적으로 알림 생성 후 루프 종료
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Thread interrupted while waiting", e);
                            break;
                        } catch (Exception e) {
                            delay *= 2;  // 실패 시 지연 시간 증가
                            if (delay > maxDelay) {
                                log.error("Failed to create notification after retries", e);
                                break;
                            }
                        }
                    }});
                notificationCreatorThread.start();

                relatedTableMapEvents.clear();
            } else if (recordedEventTypes.contains(eventType)) {
                Long tableId = getTableId(event);
                if (tableId != null) {
                    handleEvent(event, tableId);
                }
            }
        });
        Thread thread = new Thread(() -> {
            try {
                logClient.connect();
            } catch (IOException e) {
                log.error("Failed to connect BinaryLogClient", e);
            }
        });


        thread.start();


        return logClient;
    }


    private void handleEvent(Event event, long tableId) {

        relatedTableMapEvents.compute(tableId, (tid, tableMapInfo) -> {
            if (tableMapInfo == null) {
                tableMapInfo = new TableMapInfo();
            }

            if (event.getHeader().getEventType().equals(EventType.TABLE_MAP)) {
                // 테이블 매핑 정보 처리
                tableMapInfo.setTableMapEventData(event.getData());
            } else {
                // 실제 변경 이벤트 처리
                if (tableMapInfo.getDmlEvents() == null) {
                    tableMapInfo.setDmlEvents(new HashSet<>());
                }
                tableMapInfo.getDmlEvents().add(event);
            }
            return tableMapInfo;
        });
    }

    private static Long getTableId(Event event) {
        EventData eventData = event.getData();
        Long tableId = null;
        if (eventData instanceof WriteRowsEventData) {
            tableId = ((WriteRowsEventData) eventData).getTableId();
        } else if (eventData instanceof UpdateRowsEventData) {
            tableId = ((UpdateRowsEventData) eventData).getTableId();
        } else if (eventData instanceof DeleteRowsEventData) {
            tableId = ((DeleteRowsEventData) eventData).getTableId();
        } else if (eventData instanceof TableMapEventData) {
            tableId = ((TableMapEventData) eventData).getTableId();
        }
        return tableId;
    }
}

@Data
class TableMapInfo {
    private TableMapEventData tableMapEventData;
    private Set<Event> dmlEvents;
}
