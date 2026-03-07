package com.aeacoh.quizBank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

@Tag(name = "카테고리 관리", description = "카테고리를 추가, 조회, 수정, 삭제하는 기능을 가진 API입니다.")
@RestController
@RequestMapping("/categories")
@CrossOrigin("*")
public class CategoryController {
    private static final String DUPLICATE_QUERY = "SELECT 1 FROM CATEGORY WHERE CATEGORY_CD = ?";
    private static final String INSERT_QUERY = "INSERT INTO CATEGORY (CATEGORY_NM) VALUES (?)";
    private static final String VIEW_QUERY = "SELECT * FROM CATEGORY WHERE CATEGORY_CD = ?";
    private static final String TOTAL_COUNT_QUERY = "SELECT COUNT(*) FROM CATEGORY";
    private static final String LIST_QUERY = "SELECT * FROM CATEGORY";
    private static final String UPDATE_QUERY = "UPDATE CATEGORY SET CATEGORY_NM = ? WHERE CATEGORY_CD = ?";
    private static final String PATCH_QUERY = "UPDATE CATEGORY SET MODI_DTM = CURRENT_TIMESTAMP";
    private static final String DELETE_QUERY = "UPDATE CATEGORY SET USE_YN = 'N' WHERE USE_YN = 'Y' AND CATEGORY_CD = ?";
    
    private final Logger logger = LoggerFactory.getLogger(CategoryController.class);
    private final DataSource dataSource;

    public CategoryController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private String validateData(Map<String, Object> data) {
        // 파라미터 추출
        String categoryCd = ParserUtil.parseString(data.get("categoryCd"));
        String categoryNm = ParserUtil.parseString(data.get("categoryNm"));
        
        // 필수 값 체크
        if (categoryCd == null || categoryCd.trim().isEmpty()) {
            return "'categoryCd'는 필수 값입니다.";
        }
        if (categoryNm == null || categoryNm.trim().isEmpty()) {
            return "'categoryNm'는 필수 값입니다.";
        }

        // 길이 체크
        if (categoryCd.length() > 50) {
            return "'categoryCd'는 50자를 초과할 수 없습니다.";
        }
        if (categoryNm.length() > 100) {
            return "'categoryNm'는 100자를 초과할 수 없습니다.";
        }

        return null;
    }

    private Map<String, Object> getCurrentDataRow(ResultSet resultSet) throws SQLException {
        Map<String, Object> dataRow = new HashMap<>();
        dataRow.put("categoryCd", resultSet.getString("CATEGORY_CD"));
        dataRow.put("categoryNm", resultSet.getString("CATEGORY_NM"));
        dataRow.put("useYn", resultSet.getString("USE_YN"));
        dataRow.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());
        dataRow.put("modiDtm", resultSet.getTimestamp("MODI_DTM").toLocalDateTime());

        return dataRow;
    }

    private String buildWhereClause(String categoryNm) {
        StringBuilder whereClause = new StringBuilder(" WHERE USE_YN = 'Y'");
        if (categoryNm != null && !categoryNm.isEmpty()) {
            whereClause.append(" AND CATEGORY_NM LIKE '%' || ? || '%'");
        }
        
        return whereClause.toString();
    }

    private String buildOrderByClause(String sort) {
        StringBuilder orderByClause = new StringBuilder(" ORDER BY");
        if (sort != null && !sort.isEmpty()) {
            String[] split = sort.split(",");
            Set<String> availableColumns = Set.of("CATEGORY_CD", "CATEGORY_NM", "REG_DTM", "MODI_DTM");
            Set<String> availableDirections = Set.of("DESC", "ASC");
            for (int i = 0; i < split.length; i += 2) {
                String column = split[i].toUpperCase();
                String direction = i + 1 < split.length? split[i + 1].toUpperCase() : "ASC";
                if (!availableColumns.contains(column)) {
                    return " ORDER BY CATEGORY_CD DESC";
                }
                if (!availableDirections.contains(direction)) {
                    return " ORDER BY CATEGORY_CD DESC";
                }
                
                if (i > 0) {
                    orderByClause.append(",");
                }
                orderByClause.append(" " + column + " " + direction);
            }

            return orderByClause.toString();
        }

        return " ORDER BY ORDER_NO ASC, CATEGORY_CD ASC";
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
            content = @Content(schema = @Schema(example = "{\"categoryCd\":\"JCG\", \"categoryNm\":\"정보처리기사\"}"))
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
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 중복 체크 쿼리 세팅
            preparedStatement = connection.prepareStatement(DUPLICATE_QUERY);
            preparedStatement.setString(1, ParserUtil.parseString(data.get("categoryCd")));

            // 중복 체크 쿼리 실행
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return ResponseEntity.badRequest().body("이미 존재하는 코드입니다.");
            }

            preparedStatement.close();

            // 단건 등록 쿼리 세팅
            preparedStatement = connection.prepareStatement(INSERT_QUERY);
            preparedStatement.setString(1, ParserUtil.parseString(data.get("categoryNm")));

            // 단건 등록 쿼리 실행
            int createCount = preparedStatement.executeUpdate();
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
            JDBCUtil.closeAll(resultSet, preparedStatement, connection);
        }
    }

    // 상세 조회
    @Operation(summary = "상세 조회", description = "아이디에 해당하는 데이터 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "404", description = "아이디에 해당하는 데이터 없음")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping("/{categoryCd}")
    public ResponseEntity<?> findById(@Parameter(description = "카테고리 코드 조건", example = "1") @PathVariable("categoryCd") String categoryCd) {
        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 상세 조회 쿼리 세팅
            preparedStatement = connection.prepareStatement(VIEW_QUERY);
            preparedStatement.setString(1, categoryCd);

            // 상세 조회 쿼리 실행
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Map<String, Object> result = this.getCurrentDataRow(resultSet);
                logger.info("상세 조회 완료::{}", result);
                return ResponseEntity.ok(result);
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("상세 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(resultSet, preparedStatement, connection);
        }
    }

    // 목록 조회(페이징)
    @Operation(summary = "목록 조회", description = "조건에 해당하는 데이터 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping
    public ResponseEntity<?> findAll(
        @Parameter(description = "카테고리명 조건", example = "정보처리기사") @RequestParam(required = false) String categoryNm,
        @Parameter(description = "등록 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regStart,
        @Parameter(description = "등록 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regEnd,
        @Parameter(description = "수정 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptStart,
        @Parameter(description = "수정 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptEnd,
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

        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 전체 개수 조회
            long totalCount = 0;
            preparedStatement = connection.prepareStatement(TOTAL_COUNT_QUERY + this.buildWhereClause(categoryNm));
            if (categoryNm != null && !categoryNm.isEmpty()) {  
                preparedStatement.setString(1, categoryNm);
            }

            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                totalCount = resultSet.getLong(1);
            }

            JDBCUtil.closeAll(resultSet, preparedStatement, null);

            // 목록 조회
            int parameterIndex = 1;
            StringBuilder sql = new StringBuilder(LIST_QUERY + this.buildWhereClause(categoryNm) + this.buildOrderByClause(sort));
            if (page != null && size != null) {
                sql.append(" LIMIT ? OFFSET ?");
            }
            preparedStatement = connection.prepareStatement(sql.toString());
            if (categoryNm != null && !categoryNm.isEmpty()) {  
                preparedStatement.setString(parameterIndex++, categoryNm);
            }
            if (page != null && size != null) {
                preparedStatement.setLong(parameterIndex++, size);
                preparedStatement.setLong(parameterIndex++, (page - 1) * size);
            }

            resultSet = preparedStatement.executeQuery();
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
            JDBCUtil.closeAll(resultSet, preparedStatement, connection);
        }
    }

    // 단건 수정
    @Operation(summary = "단건 수정", description = "아이디에 해당하는 데이터 정보를 전체 수정합니다.")
    @ApiResponse(responseCode = "200", description = "단건 수정 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PutMapping("/{categoryCd}")
    public ResponseEntity<?> update(
        @Parameter(description = "카테고리 코드 조건", example = "1") @PathVariable("categoryCd") String categoryCd,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "데이터 정보",
            required = true,
            content = @Content(schema = @Schema(example = "{\"categoryNm\":\"정보처리기사\"}"))
        )
        @RequestBody Map<String, Object> data
    ) {
        // 밸리데이션 체크
        String validateMessage = this.validateData(data);
        if (validateMessage != null) {
            return ResponseEntity.badRequest().body(validateMessage);
        }

        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 단건 수정 쿼리 세팅
            preparedStatement = connection.prepareStatement(UPDATE_QUERY);
            preparedStatement.setString(1, ParserUtil.parseString(data.get("categoryNm")));
            preparedStatement.setString(2, categoryCd);

            // 단건 수정 쿼리 실행
            int updateCount = preparedStatement.executeUpdate();
            if (updateCount == 0) {
                logger.error("단건 수정 실패");
                return ResponseEntity.internalServerError().body("단건 수정 실패");
            }

            logger.info("단건 수정 완료::{}", updateCount);
            return ResponseEntity.ok("단건 수정 완료::" + updateCount);
        } catch (Exception e) {
            logger.error("단건 수정 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(null, preparedStatement, connection);
        }
    }

    // 단건 패치
    @Operation(summary = "단건 패치", description = "아이디에 해당하는 데이터 정보를 부분 수정합니다.")
    @ApiResponse(responseCode = "200", description = "단건 패치 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PatchMapping("/{categoryCd}")
    public ResponseEntity<?> patch(
        @Parameter(description = "카테고리 코드 조건", example = "1") @PathVariable("categoryCd") String categoryCd,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "데이터 정보",
            required = false,
            content = @Content(schema = @Schema(example = "{\"categoryNm\":\"정보처리기사\"}"))
        )
        @RequestBody(required = false) Map<String, Object> data
    ) { 
        // 파라미터 추출
        String categoryNm = ParserUtil.parseString(data.get("categoryNm"));
        
        // 필수 값 체크
        if (categoryNm != null) {
            if (categoryNm.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'categoryNm'는 필수 값입니다.");
            }
            if (categoryNm.length() > 100) {
                return ResponseEntity.badRequest().body("'categoryNm'는 100자를 초과할 수 없습니다.");
            }
        }

        // 디비 연결 객체 선언
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();

            // 단건 패치 쿼리 세팅
            int parameterIndex = 1;
            StringBuilder sql = new StringBuilder(PATCH_QUERY);
            if (categoryNm != null) {
                sql.append("     , CATEGORY_NM = ?");
            }
            sql.append(" WHERE ID = ?");
            preparedStatement = connection.prepareStatement(sql.toString());
            if (categoryNm != null) {
                preparedStatement.setString(parameterIndex++, categoryNm);
            }
            preparedStatement.setString(parameterIndex++, categoryCd);

            // 단건 패치 쿼리 실행
            int patchCount = preparedStatement.executeUpdate();
            if (patchCount == 0) {
                logger.error("단건 패치 실패");
                return ResponseEntity.internalServerError().body("단건 패치 실패");
            }

            logger.info("단건 패치 완료::{}", patchCount);
            return ResponseEntity.ok("단건 패치 완료::" + patchCount);
        } catch (Exception e) {
            logger.error("단건 패치 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(null, preparedStatement, connection);
        }
    }

    // 단건 논리적 삭제
    @Operation(summary = "단건 논리적 삭제", description = "아이디에 해당하는 데이터 정보를 논리적으로 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "단건 논리적 삭제 성공")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @DeleteMapping("/{categoryCd}/trash")
    public ResponseEntity<?> softRemove(@Parameter(description = "카테고리 코드 조건", example = "1") @PathVariable("categoryCd") String categoryCd) {
        logger.info("단건 논리적 삭제 시작::categoryCd={}", categoryCd);
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = dataSource.getConnection();

            preparedStatement = connection.prepareStatement(DELETE_QUERY);
            preparedStatement.setString(1, categoryCd);

            int deleteCount = preparedStatement.executeUpdate();
            if (deleteCount == 0) {
                logger.error("단건 논리적 삭제 실패");
                return ResponseEntity.internalServerError().body("단건 논리적 삭제 실패");
            }

            logger.info("단건 논리적 삭제 완료");
            return ResponseEntity.ok("단건 논리적 삭제 성공");
        } catch (Exception e) {
            logger.error("단건 논리적 삭제 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            JDBCUtil.closeAll(null, preparedStatement, connection);
        }
    }
}