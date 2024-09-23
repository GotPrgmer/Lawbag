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
        log.debug("Attempting to connect to the database...");
        try (Connection connection = dataSource.getConnection()) {
            log.debug("Setting transaction isolation to READ_COMMITTED.");
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            DatabaseMetaData metaData = connection.getMetaData();
            log.debug("Successfully connected to the database: {}", dbName);
            log.debug("Fetching table information from database: {}", dbName);

            try (ResultSet tableResultSet = metaData.getTables(dbName, "public", null, new String[]{"TABLE"})) {
                // metaData의 테이블 정보를 가져옴
                while (tableResultSet.next()) {
                    // 테이블 이름을 tableName에 저장
                    String tableName = tableResultSet.getString("TABLE_NAME");
                    log.debug("Found table: {}", tableName);

                    // 컬럼 네임별 인덱스 저장할 해시맵 생성
                    Map<String, Integer> columnOrders = new HashMap<>();
                    log.debug("Fetching columns for table: {}", tableName);

                    // 해당 table의 컬럼 가져와서 try-with 구문 시작
                    try (ResultSet columnResultSet = metaData.getColumns(dbName, "public", tableName, null)) {
                        // 컬럼 이름과 컬럼 매칭 인덱스를 맵에 저장
                        while (columnResultSet.next()) {
                            String columnName = columnResultSet.getString("COLUMN_NAME").toLowerCase();
                            int columnIndex = columnResultSet.getInt("ORDINAL_POSITION") - 1;
                            log.debug("Found column: {}, Index: {}", columnName, columnIndex);

                            columnOrders.put(columnResultSet.getString("COLUMN_NAME").toLowerCase(), columnResultSet.getInt("ORDINAL_POSITION") - 1);
                        }
                    }
                    // 테이블 당 컬럼들의 네임과 인덱스를 저장한 해시맵을 put
                    columnOrdersByTable.put(tableName, Collections.unmodifiableMap(columnOrders));
                    log.debug("Finished processing columns for table: {}", tableName);

                }

            }
            log.debug("Finished fetching table information.");

        } catch (SQLException e) {
            log.error("Failed to fetch column orders by table", e);
            throw new RuntimeException(e);
        }
        log.debug("Returning unmodifiable map of column orders by table.");

        return Collections.unmodifiableMap(columnOrdersByTable);
    }


    @Bean(destroyMethod = "disconnect")
    BinaryLogClient binaryLogClient() throws IOException {
        log.debug("Initializing BinaryLogClient...");

        final Map<String, Map<String, Integer>> columnOrdersByTable = fetchColumnOrdersByTable();
        log.debug("Successfully fetched column orders by table.");

        final List<ColumnEventType> watchedColumnEvents = List.of(ColumnEventType.values());
        final List<String> watchedTableNames = watchedColumnEvents.stream().map(ColumnEventType::getTableName).toList();

        log.debug("Watched Table Names: {}", watchedTableNames);

        BinaryLogClient logClient = new BinaryLogClient(
                host,
                port,
                user,
                password);

        log.debug("BinaryLogClient created with host: {}, port: {}, user: {}", host, port, user);



        logClient.registerEventListener(event -> {
            final EventType eventType = event.getHeader().getEventType();
            log.debug("Received EventType: {}", eventType);

            if (eventType == EventType.XID) {
                log.debug("XID event detected, processing DML events.");

                List<Map<String, List<String>>> filteredValuesByRows = new ArrayList<>();
                log.debug("Processing related table map events...");
                relatedTableMapEvents.forEach((_tableId, tableMapInfo) -> {
                    // 여기서 매핑정보들 테이블id 와 before after 변화 감지해서 원하는 알림처리 메소드 호출
                    final TableMapEventData tableMapEventData = tableMapInfo.getTableMapEventData();

                    final String tableName = tableMapEventData.getTable();
                    // 이벤트 감지가 필요없는 테이블이면 스킵
                    // tableName은 all 소문자로 옴
                    if (!watchedTableNames.contains(tableName)) return;

                    final Set<Event> dmlOperations = tableMapInfo.getDmlEvents();
                    dmlOperations.stream()
                            .sorted(Comparator.comparing(e -> e.getHeader().getTimestamp()))
                            .forEach(e -> {
                                final EventData data = e.getData();
                                if (data instanceof WriteRowsEventData insertedData) {
                                    List<Serializable[]> rows = insertedData.getRows();
                                    log.debug("WriteRowsEventData detected for table: {}", tableName);

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

                                    filteredValuesByRows.addAll(
                                            rows.stream()
                                                    .map(row -> {
                                                        final Map<String, List<String>> filteredValues = new HashMap<>();
                                                        resultColumnsIndicesByEvent.forEach((eventName, columnIndices) -> {
                                                            final List<String> values = new ArrayList<>();
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
                            });
                });

                log.debug("Creating notification thread...");
                Thread notificationCreatorThread = new Thread(() -> {

                    int delay = 100;  // 초기 지연 시간
                    int maxDelay = 1000;  // 최대 지연 시간
                    while (delay <= maxDelay) {
                        try {
                            Thread.sleep(delay);
                            notificationCreator.createNotification(filteredValuesByRows);
                            log.debug("Notification created successfully.");
                            break;  // 성공적으로 알림 생성 후 루프 종료
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Thread interrupted while waiting", e);
                            break;
                        } catch (Exception e) {
                            delay *= 2;  // 실패 시 지연 시간 증가
                            log.error("Failed to create notification, retrying with delay {}", delay, e);
                        }
                    }
                });
                notificationCreatorThread.start();

                relatedTableMapEvents.clear();
            } else if (recordedEventTypes.contains(eventType)) {
                Long tableId = getTableId(event);
                if (tableId != null) {
                    log.debug("Handling event for table ID: {}", tableId);
                    handleEvent(event, tableId);
                }
            }
        });
        Thread thread = new Thread(() -> {
            try {
                logClient.connect();
                log.debug("BinaryLogClient connected.");
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
