package com.aeacoh.quizBank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "퀴즈 히스토리 관리", description = "퀴즈 히스토리를 추가, 조회, 수정, 삭제하는 기능을 가진 API입니다.")
@RestController
@RequestMapping("/quizHistories")
@CrossOrigin("*")
public class QuizHistoryController {
    private static final String EXIST_QUERY = "SELECT 1 FROM QUIZ_MASTER WHERE ID = ?";
    private static final String INSERT_QUERY = "INSERT INTO QUIZ_HISTORY (QUIZ_ID, CHOICE_OPTION, CORRECT_YN, SOLVE_TYPE) VALUES (?, ?, ?, ?)";
    private static final String TOTAL_COUNT_QUERY = "SELECT COUNT(*) FROM QUIZ_HISTORY A";
    private static final String LIST_QUERY = "SELECT A.ID, A.QUIZ_ID, B.CATEGORY_CD, C.CATEGORY_NM, B.QUESTION, B.OPTION_1, B.OPTION_2, B.OPTION_3, B.OPTION_4, B.ANSWER, B.EXPLANATION, B.LEVEL, A.CHOICE_OPTION, A.CORRECT_YN, A.REG_DTM FROM QUIZ_HISTORY A INNER JOIN QUIZ_MASTER B ON A.QUIZ_ID = B.ID INNER JOIN CATEGORY C ON B.CATEGORY_CD = C.CATEGORY_CD";
    
    private final Logger logger = LoggerFactory.getLogger(QuizHistoryController.class);
    private final DataSource dataSource;

    public QuizHistoryController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private String validateData(Map<String, Object> data) {
        // 파라미터 추출
        Integer quizId = ParserUtil.parseInteger(data.get("quizId"));
        Integer choiceOption = ParserUtil.parseInteger(data.get("choiceOption"));
        String correctYn = ParserUtil.parseString(data.get("correctYn"));
        String solveType = ParserUtil.parseString(data.get("solveType"));
        
        // 필수 값 체크
        if (quizId == null) {
            return "'quizId'는 필수 값입니다.";
        }
        if (choiceOption == null) {
            return "'choiceOption'는 필수 값입니다.";
        }
        if (correctYn == null || correctYn.trim().isEmpty()) {
            return "'correctYn'는 필수 값입니다.";
        }

        // 형식 체크
        if (choiceOption < 1 || choiceOption > 4) {
            return "'choiceOption'는 1에서 4 사이의 값이어야 합니다.";
        }
        if (!"Y".equals(correctYn) && !"N".equals(correctYn)) {
            return "'correctYn'는 Y 또는 N이어야 합니다.";
        }
        if (!"ALL".equals(solveType) && !"WRONG_ONLY".equals(solveType)) {
            return "'solveType'는 ALL 또는 WRONG_ONLY여야 합니다.";
        }

        return null;
    }

    private Map<String, Object> getCurrentDataRow(ResultSet resultSet) throws SQLException {
        Map<String, Object> dataRow = new HashMap<>();
        dataRow.put("id", resultSet.getLong("ID"));
        dataRow.put("quizId", resultSet.getLong("QUIZ_ID"));
        dataRow.put("categoryCd", resultSet.getString("CATEGORY_CD"));
        dataRow.put("categoryNm", resultSet.getString("CATEGORY_NM"));
        dataRow.put("question", resultSet.getString("QUESTION"));
        dataRow.put("option1", resultSet.getString("OPTION_1"));
        dataRow.put("option2", resultSet.getString("OPTION_2"));
        dataRow.put("option3", resultSet.getString("OPTION_3"));
        dataRow.put("option4", resultSet.getString("OPTION_4"));
        dataRow.put("answer", resultSet.getLong("ANSWER"));
        dataRow.put("explanation", resultSet.getString("EXPLANATION"));
        dataRow.put("level", resultSet.getLong("LEVEL"));
        dataRow.put("choiceOption", resultSet.getLong("CHOICE_OPTION"));
        dataRow.put("correctYn", resultSet.getString("CORRECT_YN"));
        dataRow.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());

        return dataRow;
    }

    private String buildWhereClause(Long quizId, Long choiceOption, String correctYn, String solveType, LocalDateTime regStart, LocalDateTime regEnd) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        if (quizId != null) {
            whereClause.append(" AND A.QUIZ_ID = ?");
        }
        if (choiceOption != null) {
            whereClause.append(" AND A.CHOICE_OPTION = ?");
        }
        if (correctYn != null && !correctYn.trim().isEmpty()) {
            whereClause.append(" AND A.CORRECT_YN = ?");
        }
        if (solveType != null && !solveType.trim().isEmpty()) {
            whereClause.append(" AND A.SOLVE_TYPE = ?");
        }
        
        if (regStart != null) {
            whereClause.append(" AND A.REG_DTM >= ?");
        }
        if (regEnd != null) {
            whereClause.append(" AND A.REG_DTM <= ?");
        }
        
        return whereClause.toString();
    }

    private String buildOrderByClause(String sort) {
        StringBuilder orderByClause = new StringBuilder(" ORDER BY");
        if (sort != null && !sort.isEmpty()) {
            String[] split = sort.split(",");
            Set<String> availableColumns = Set.of("ID", "QUIZ_ID", "CHOICE_OPTION", "CORRECT_YN", "REG_DTM");
            Set<String> availableDirections = Set.of("DESC", "ASC");
            for (int i = 0; i < split.length; i += 2) {
                String column = split[i].toUpperCase();
                String direction = i + 1 < split.length? split[i + 1].toUpperCase() : "ASC";
                if (!availableColumns.contains(column)) {
                    return " ORDER BY A.ID DESC";
                }
                if (!availableDirections.contains(direction)) {
                    return " ORDER BY A.ID DESC";
                }
                
                if (i > 0) {
                    orderByClause.append(",");
                }
                orderByClause.append(" " + column + " " + direction);
            }

            return orderByClause.toString();
        }

        return " ORDER BY A.ID DESC";
    }
    
    // 단건 등록
    @Operation(summary = "단건 등록", description = "새로운 데이터 하나를 등록합니다.")
    @ApiResponse(responseCode = "201", description = "단건 등록 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PostMapping
    public ResponseEntity<?> create(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "데이터 정보",
            required = true,
            content = @Content(schema = @Schema(example = "{\"quizId\":1, \"choiceOption\":2, \"correctYn\":\"Y\", \"solveType\":\"ALL\"}"))
        )
        @RequestBody Map<String, Object> data
    ) {
        logger.info("단건 등록 시작::data={}", data);
        
        // 밸리데이션 체크
        String validateMessage = this.validateData(data);
        if (validateMessage != null) {
            return ResponseEntity.badRequest().body(validateMessage);
        }

        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement1 = null;
        PreparedStatement preparedStatement2 = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 퀴즈 아이디 존재 여부 체크 쿼리 세팅
            preparedStatement1 = connection.prepareStatement(EXIST_QUERY);
            preparedStatement1.setLong(1, ParserUtil.parseInteger(data.get("quizId")));

            // 퀴즈 아이디 존재 여부 체크 쿼리 실행
            resultSet = preparedStatement1.executeQuery();
            if (!resultSet.next()) {
                return ResponseEntity.badRequest().body("존재하지 않는 퀴즈 아이디입니다.");
            }

            preparedStatement1.close();

            // 단건 등록 쿼리 세팅
            preparedStatement2 = connection.prepareStatement(INSERT_QUERY);
            preparedStatement2.setLong(1, ParserUtil.parseInteger(data.get("quizId")));
            preparedStatement2.setLong(2, ParserUtil.parseInteger(data.get("choiceOption")));
            preparedStatement2.setString(3, ParserUtil.parseString(data.get("correctYn")));
            preparedStatement2.setString(4, ParserUtil.parseString(data.get("solveType")));

            // 단건 등록 쿼리 실행
            int createCount = preparedStatement2.executeUpdate();
            if (createCount == 0) {
                logger.error("단건 등록 실패");
                return ResponseEntity.internalServerError().body("단건 등록 실패");
            }

            logger.info("등록 완료::{}", createCount);
            return ResponseEntity.status(201).body("단건 등록 완료::" + createCount);
        } catch (Exception e) {
            logger.error("단건 등록 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(resultSet, preparedStatement2, connection);
        }
    }

    // 목록 조회(페이징)
    @Operation(summary = "목록 조회", description = "조건에 해당하는 데이터 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping
    public ResponseEntity<?> findAll(
        @Parameter(description = "퀴즈 아이디 조건", example = "1") @RequestParam(required = false) Long quizId,
        @Parameter(description = "사용자 선택 번호 조건", example = "2") @RequestParam(required = false) Long choiceOption,
        @Parameter(description = "정답 여부 조건", example = "Y") @RequestParam(required = false) String correctYn,
        @Parameter(description = "풀이 타입 조건", example = "ALL") @RequestParam(defaultValue = "ALL") String solveType,
        @Parameter(description = "등록 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regStart,
        @Parameter(description = "등록 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regEnd,
        @Parameter(description = "페이지 번호 (1부터 시작)", example = "1") @RequestParam(required = false) Long page,
        @Parameter(description = "페이지당 출력 개수", example = "10") @RequestParam(required = false) Long size,
        @Parameter(description = "정렬 방법", example = "id,desc") @RequestParam(required = false) String sort
    ) {
        // 밸리데이션 체크
        // 형식 체크
        if (page != null && page < 1) {
            return ResponseEntity.badRequest().body("'page'는 0보다 큰 숫자여야 합니다.");
        }
        if (size != null && (size < 1 || size > 500)) {
            return ResponseEntity.badRequest().body("'size'는 1에서 500 사이의 숫자여야 합니다.");
        }
        if (regStart != null && regEnd != null) {
            if (regStart.isAfter(regEnd)) {
                return ResponseEntity.badRequest().body("'regStart'는 'regEnd' 이전 날짜여야 합니다.");
            }
        }
        

        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement1 = null;
        PreparedStatement preparedStatement2 = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 전체 개수 조회
            int parameterIndex = 1;
            long totalCount = 0;
            preparedStatement1 = connection.prepareStatement(TOTAL_COUNT_QUERY + this.buildWhereClause(quizId, choiceOption, correctYn, solveType, regStart, regEnd));
            logger.debug("전체 개수 조회 쿼리::{}", TOTAL_COUNT_QUERY + this.buildWhereClause(quizId, choiceOption, correctYn, solveType, regStart, regEnd));
            if (quizId != null) {
                preparedStatement1.setLong(parameterIndex++, quizId);
            }
            if (choiceOption != null) {
                preparedStatement1.setLong(parameterIndex++, choiceOption);
            }
            if (correctYn != null && !correctYn.trim().isEmpty()) {
                preparedStatement1.setString(parameterIndex++, correctYn);
            }
            if (solveType != null) {
                preparedStatement1.setString(parameterIndex++, solveType);
            }
            if (regStart != null) {
                preparedStatement1.setTimestamp(parameterIndex++, Timestamp.valueOf(regStart));
            }
            if (regEnd != null) {
                preparedStatement1.setTimestamp(parameterIndex++, Timestamp.valueOf(regEnd));
            }

            resultSet = preparedStatement1.executeQuery();
            if (resultSet.next()) {
                totalCount = resultSet.getLong(1);
            }

            JDBCUtil.closeAll(resultSet, preparedStatement1, null);

            // 목록 조회
            parameterIndex = 1;
            StringBuilder sql = new StringBuilder(LIST_QUERY + this.buildWhereClause(quizId, choiceOption, correctYn, solveType, regStart, regEnd) + this.buildOrderByClause(sort));
            if (page != null && size != null) {
                sql.append(" LIMIT ? OFFSET ?");
            }
            preparedStatement2 = connection.prepareStatement(sql.toString());
            if (quizId != null) {
                preparedStatement2.setLong(parameterIndex++, quizId);
            }
            if (choiceOption != null) {
                preparedStatement2.setLong(parameterIndex++, choiceOption);
            }
            if (correctYn != null && !correctYn.trim().isEmpty()) {
                preparedStatement2.setString(parameterIndex++, correctYn);
            }
            if (solveType != null) {
                preparedStatement2.setString(parameterIndex++, solveType);
            }
            if (regStart != null) {
                preparedStatement2.setTimestamp(parameterIndex++, Timestamp.valueOf(regStart));
            }
            if (regEnd != null) {
                preparedStatement2.setTimestamp(parameterIndex++, Timestamp.valueOf(regEnd));
            }
            if (page != null && size != null) {
                preparedStatement2.setLong(parameterIndex++, size);
                preparedStatement2.setLong(parameterIndex++, (page - 1) * size);
            }

            resultSet = preparedStatement2.executeQuery();
            List<Map<String, Object>> datas = new ArrayList<>();
            while (resultSet.next()) {
                datas.add(this.getCurrentDataRow(resultSet));
            }

            // 응답 객체 생성
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount); // 전체 데이터 수
            result.put("totalPage", page == null || size == null? 1 : (int) Math.ceil((double) totalCount / size)); // 전체 페이지 수
            result.put("currentPage", page); // 현재 페이지
            result.put("list", datas); // 현재 페이지 데이터

            logger.info("목록 조회 완료::{}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("목록 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(resultSet, preparedStatement2, connection);
        }
    }
}