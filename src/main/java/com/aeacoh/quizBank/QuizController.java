package com.aeacoh.quizBank;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "퀴즈 관리 API v1", description = "퀴즈를 추가, 조회, 수정, 삭제하는 기능을 가진 API입니다.")
@RestController
@CrossOrigin("*")
public class QuizController {
    private final Logger logger = LoggerFactory.getLogger(QuizController.class);
    private final DataSource dataSource;

    public QuizController(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    // 단건 등록
    @Operation(summary = "단건 등록", description = "새로운 데이터 하나를 등록합니다.")
    @ApiResponse(responseCode = "201", description = "단건 등록 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PostMapping(value = "/quizzes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
        @Parameter(description = "데이터 정보", schema = @Schema(type = "Object", example = "{}")) @RequestPart("data") Map<String, Object> data, 
        @Parameter(description = "업로드 파일", required = false) @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        logger.info("단건 등록 시작::data={},files={}", data, files);
        
        // 파라미터 추출
        String categoryCd = ParserUtil.parseString(data.get("categoryCd"));
        String question = ParserUtil.parseString(data.get("question"));
        String option1 = ParserUtil.parseString(data.get("option1"));
        String option2 = ParserUtil.parseString(data.get("option2"));
        String option3 = ParserUtil.parseString(data.get("option3"));
        String option4 = ParserUtil.parseString(data.get("option4"));
        Integer answer = ParserUtil.parseInteger(data.get("answer"));
        String explanation = ParserUtil.parseString(data.get("explanation"));
        Integer level = ParserUtil.parseInteger(data.get("level"));
        
        // 필수 값 체크
        if (categoryCd == null || categoryCd.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'categoryCd'는 필수 값입니다.");
        }
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'question'는 필수 값입니다.");
        }
        if (option1 == null || option1.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option1'는 필수 값입니다.");
        }
        if (option2 == null || option2.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option2'는 필수 값입니다.");
        }
        if (option3 == null || option3.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option3'는 필수 값입니다.");
        }
        if (option4 == null || option4.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option4'는 필수 값입니다.");
        }
        if (answer == null) {
            return ResponseEntity.badRequest().body("'answer'는 필수 값입니다.");
        }

        // 길이 체크
        if (categoryCd.length() > 100) {
            return ResponseEntity.badRequest().body("'categoryCd'는 100자를 초과할 수 없습니다.");
        }
        if (option1.length() > 500) {
            return ResponseEntity.badRequest().body("'option1'는 500자를 초과할 수 없습니다.");
        }
        if (option2.length() > 500) {
            return ResponseEntity.badRequest().body("'option2'는 500자를 초과할 수 없습니다.");
        }
        if (option3.length() > 500) {
            return ResponseEntity.badRequest().body("'option3'는 500자를 초과할 수 없습니다.");
        }
        if (option4.length() > 500) {
            return ResponseEntity.badRequest().body("'option4'는 500자를 초과할 수 없습니다.");
        }

        // 형식 체크
        if (answer < 1 || answer > 4) {
            return ResponseEntity.badRequest().body("'answer'는 1에서 4 사이의 숫자여야 합니다.");
        }
        if (level != null && (level < 1 || level > 3)) {
            return ResponseEntity.badRequest().body("'level'는 1에서 3 사이의 숫자여야 합니다.");
        }

        // 디비 연결 객체
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            // 디비 연결
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            // 퀴즈 등록 쿼리 세팅
            logger.debug("퀴즈 단건 등록 시작");
            StringBuilder quizInsertSql = new StringBuilder("INSERT INTO QUIZ_MASTER (CATEGORY_CD, QUESTION, OPTION_1, OPTION_2, OPTION_3, OPTION_4, ANSWER, EXPLANATION, LEVEL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            logger.debug("퀴즈 등록 쿼리::{}", quizInsertSql);

            // 퀴즈 등록 쿼리 실행 준비
            preparedStatement = connection.prepareStatement(quizInsertSql.toString(), PreparedStatement.RETURN_GENERATED_KEYS);

            // 퀴즈 등록 쿼리 파라미터 세팅
            preparedStatement.setString(1, categoryCd);
            preparedStatement.setString(2, question);
            preparedStatement.setString(3, option1);
            preparedStatement.setString(4, option2);
            preparedStatement.setString(5, option3);
            preparedStatement.setString(6, option4);
            preparedStatement.setInt(7, answer);
            preparedStatement.setString(8, explanation);
            if (level != null) {
                preparedStatement.setInt(9, level);
            } else {
                preparedStatement.setInt(9, 1);
            }

            // 퀴즈 등록 쿼리 실행
            int createCount = preparedStatement.executeUpdate();
            if (createCount == 0) {
                logger.error("퀴즈 단건 등록 실패");
                return ResponseEntity.internalServerError().body("퀴즈 단건 등록 실패");
            }

            // 등록된 퀴즈 아이디(AUTO_INCREMENT) 추출
            long quizId = 0;
            resultSet = preparedStatement.getGeneratedKeys();
            if (resultSet.next()) {
                quizId = resultSet.getLong(1);
            }

            logger.info("단건 등록 완료::quizId={}", quizId);
            return ResponseEntity.status(201).body("단건 등록 완료::quizId=" + quizId);
        } catch (Exception e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                logger.error("Connection 롤백 중 오류", e);
            }

            logger.error("단건 등록 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 엑셀 업로드
    @Operation(summary = "엑셀 업로드", description = "엑셀에서 데이터 정보를 추출하여 일괄 등록합니다.")
    @ApiResponse(responseCode = "201", description = "엑셀 업로드 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PostMapping(value = "/quizzes/excel-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> excelUpload(@Parameter(description = "업로드할 엑셀 파일 (.xlsx)") @RequestPart("file") MultipartFile file) {
        logger.info("엑셀 업로드 시작::file={}", file);
        StringBuilder sql = new StringBuilder("INSERT INTO QUIZ_MASTER (CATEGORY_CD, QUESTION, OPTION_1, OPTION_2, OPTION_3, OPTION_4, ANSWER, EXPLANATION, LEVEL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        Workbook workbook = null;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            preparedStatement = connection.prepareStatement(sql.toString());

            // 엑셀 워크북 열기
            workbook = WorkbookFactory.create(file.getInputStream());

            // 첫 번째 시트 선택
            Sheet sheet = workbook.getSheetAt(0);
            // DataFormatter를 이용하면 엑셀에서 숫자나 날짜 타입으로 적힌 것들도 보이는 그대로의 문자로 추출할 수 있다.
            DataFormatter dataFormatter = new DataFormatter();

            // 헤더 부분을 제외한 내용 행 읽기
            int headerCount = 1;
            for (int i = headerCount; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                logger.debug("{}번째 행 데이터::{}", i, row);
                if (row == null) {
                    continue;
                }

                // 행에서 파라미터 추출
                String categoryCd = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(0)));
                String question = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(1)));
                String option1 = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(2)));
                String option2 = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(3)));
                String option3 = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(4)));
                String option4 = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(5)));
                Integer answer = ParserUtil.parseInteger(dataFormatter.formatCellValue(row.getCell(6)));
                String explanation = ParserUtil.parseString(dataFormatter.formatCellValue(row.getCell(7)));
                Integer level = ParserUtil.parseInteger(dataFormatter.formatCellValue(row.getCell(8)));

                // 필수 값 체크
                if (categoryCd == null || categoryCd.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'categoryCd'는 필수 값입니다.");
                }
                if (question == null || question.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'question'는 필수 값입니다.");
                }
                if (option1 == null || option1.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'option1'는 필수 값입니다.");
                }
                if (option2 == null || option2.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'option2'는 필수 값입니다.");
                }
                if (option3 == null || option3.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'option3'는 필수 값입니다.");
                }
                if (option4 == null || option4.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("'option4'는 필수 값입니다.");
                }
                if (answer == null) {
                    return ResponseEntity.badRequest().body("'answer'는 필수 값입니다.");
                }

                // 길이 체크
                if (categoryCd.length() > 100) {
                    return ResponseEntity.badRequest().body("'categoryCd'는 100자를 초과할 수 없습니다.");
                }
                if (option1.length() > 500) {
                    return ResponseEntity.badRequest().body("'option1'는 500자를 초과할 수 없습니다.");
                }
                if (option2.length() > 500) {
                    return ResponseEntity.badRequest().body("'option2'는 500자를 초과할 수 없습니다.");
                }
                if (option3.length() > 500) {
                    return ResponseEntity.badRequest().body("'option3'는 500자를 초과할 수 없습니다.");
                }
                if (option4.length() > 500) {
                    return ResponseEntity.badRequest().body("'option4'는 500자를 초과할 수 없습니다.");
                }

                // 형식 체크
                if (answer < 1 || answer > 4) {
                    return ResponseEntity.badRequest().body("'answer'는 1에서 4 사이의 숫자여야 합니다.");
                }
                if (level != null && (level < 1 || level > 3)) {
                    return ResponseEntity.badRequest().body("'level'는 1에서 3 사이의 숫자여야 합니다.");
                }

                // 퀴즈 등록 쿼리 파라미터 세팅
                preparedStatement.setString(1, categoryCd);
                preparedStatement.setString(2, question);
                preparedStatement.setString(3, option1);
                preparedStatement.setString(4, option2);
                preparedStatement.setString(5, option3);
                preparedStatement.setString(6, option4);
                preparedStatement.setInt(7, answer);
                preparedStatement.setString(8, explanation);
                if (level != null) {
                    preparedStatement.setInt(9, level);
                } else {
                    preparedStatement.setInt(9, 1);
                }

                // 퀴즈 등록 쿼리 배치에 추가
                preparedStatement.addBatch();
            }
            // 배치 실행
            int[] createCounts = preparedStatement.executeBatch();
            int successRowCount = 0;
            for (int createCount : createCounts) {
                // DB에 따라 성공적인 인서트 결과가 -2로 반환될수도 있음
                if (createCount >= 0 || createCount == -2) {
                    successRowCount++;
                }
            }

            connection.commit();

            logger.info("엑셀 업로드 완료::총 {}건, 성공 {}건, 실패 {}건", sheet.getLastRowNum() - headerCount + 1, successRowCount, sheet.getLastRowNum() - headerCount + 1 - successRowCount);
            return ResponseEntity.status(201).body("성공적으로 " + successRowCount + "건이 등록되었습니다.");
        } catch (Exception e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                logger.error("Connection 롤백 중 오류", e);
            }
            logger.error("엑셀 업로드 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (workbook != null) {
                    workbook.close();
                }
            } catch (IOException e) {
                logger.error("Workbook 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 상세 조회
    @Operation(summary = "상세 조회", description = "아이디에 해당하는 데이터 상세 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "404", description = "아이디에 해당하는 데이터 없음")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping("/quizzes/{id}")
    public ResponseEntity<?> findById(@Parameter(description = "아이디 조건", example = "1") @PathVariable("id") Long id) {
        logger.info("상세 조회 시작::id={}", id);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT A.ID");
        sql.append("     , A.CATEGORY_CD");
        sql.append("     , A.QUESTION");
        sql.append("     , A.OPTION_1");
        sql.append("     , A.OPTION_2");
        sql.append("     , A.OPTION_3");
        sql.append("     , A.OPTION_4");
        sql.append("     , A.ANSWER");
        sql.append("     , A.EXPLANATION");
        sql.append("     , A.LEVEL");
        sql.append("     , A.USE_YN");
        sql.append("     , A.REG_DTM");
        sql.append("     , A.MODI_DTM");
        sql.append("     , COUNT(B.ID) AS FILE_CNT");
        sql.append("  FROM QUIZ_MASTER A");
        sql.append("  LEFT OUTER JOIN QUIZ_FILES B");
        sql.append("    ON A.ID = B.QUIZ_ID");
        sql.append(" WHERE A.ID = ?");
        sql.append(" GROUP BY A.ID");
        sql.append("     , A.CATEGORY_CD");
        sql.append("     , A.QUESTION");
        sql.append("     , A.OPTION_1");
        sql.append("     , A.OPTION_2");
        sql.append("     , A.OPTION_3");
        sql.append("     , A.OPTION_4");
        sql.append("     , A.ANSWER");
        sql.append("     , A.EXPLANATION");
        sql.append("     , A.LEVEL");
        sql.append("     , A.USE_YN");
        sql.append("     , A.REG_DTM");
        sql.append("     , A.MODI_DTM");

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement(sql.toString());

            preparedStatement.setLong(1, id);

            resultSet = preparedStatement.executeQuery();

            Map<String, Object> result = new HashMap<>();
            if (resultSet.next()) {
                result.put("id", resultSet.getLong("ID"));
                result.put("categoryCd", resultSet.getString("CATEGORY_CD"));
                result.put("question", resultSet.getString("QUESTION"));
                result.put("option1", resultSet.getString("OPTION_1"));
                result.put("option2", resultSet.getString("OPTION_2"));
                result.put("option3", resultSet.getString("OPTION_3"));
                result.put("option4", resultSet.getString("OPTION_4"));
                result.put("answer", resultSet.getString("ANSWER"));
                result.put("explanation", resultSet.getString("EXPLANATION"));
                result.put("level", resultSet.getLong("LEVEL"));
                result.put("useYn", resultSet.getString("USE_YN"));
                result.put("fileCnt", resultSet.getLong("FILE_CNT"));
                result.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());
                result.put("modiDtm", resultSet.getTimestamp("MODI_DTM").toLocalDateTime());
            } else {
                return ResponseEntity.notFound().build();
            }

            logger.info("상세 조회 완료::{}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("상세 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 목록 조회(페이징)
    @Operation(summary = "목록 조회", description = "조건에 해당하는 데이터 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping("/quizzes")
    public ResponseEntity<?> findAll(
        @Parameter(description = "카테고리 조건", example = "정보처리기사") @RequestParam(required = false) String categoryCd,
        @Parameter(description = "문제 내용 조건", example = "클래스") @RequestParam(required = false) String question,
        @Parameter(description = "선택지 조건", example = "자바") @RequestParam(required = false) String option,
        @Parameter(description = "정답 번호 조건", example = "1") @RequestParam(required = false) Integer answer,
        @Parameter(description = "정답 해설 조건", example = "클래스") @RequestParam(required = false) String explanation,
        @Parameter(description = "난이도 조건", example = "1") @RequestParam(required = false) Integer level,
        @Parameter(description = "등록 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regStart,
        @Parameter(description = "등록 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regEnd,
        @Parameter(description = "수정 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptStart,
        @Parameter(description = "수정 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptEnd,
        @Parameter(description = "페이지 번호 (1부터 시작)", example = "1") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "페이지당 출력 개수", example = "10") @RequestParam(defaultValue = "10") int size,
        @Parameter(description = "정렬 방법", example = "id,desc") @RequestParam(required = false) String sort
    ) {
        logger.info("목록 조회 시작::categoryCd={},question={},option={},answer={},explanation={},level={},regStart={},regEnd={},uptStart={},uptEnd={},page={},size={},sort={}", categoryCd, question, option, answer, explanation, level, regStart, regEnd, uptStart, uptEnd, page, size, sort);
        
        // 형식 체크
        if (page < 1) {
            return ResponseEntity.badRequest().body("'page'는 0보다 큰 숫자여야 합니다.");
        }
        if (size < 1 || size > 500) {
            return ResponseEntity.badRequest().body("'size'는 1에서 500 사이의 숫자여야 합니다.");
        }

        // 조회 조건 세팅
        StringBuilder whereClause = new StringBuilder(" WHERE USE_YN = 'Y'");
        if (categoryCd != null && !categoryCd.isEmpty()) {
            whereClause.append(" AND CATEGORY_CD LIKE '%' || ? || '%'");
        }
        if (question != null && !question.isEmpty()) {
            whereClause.append(" AND QUESTION LIKE '%' || ? || '%'");
        }
        if (option != null && !option.isEmpty()) {
            whereClause.append(" AND (OPTION_1 LIKE '%' || ? || '%' OR OPTION_2 LIKE '%' || ? || '%' OR OPTION_3 LIKE '%' || ? || '%' OR OPTION_4 LIKE '%' || ? || '%')");
        }
        if (answer != null) {
            whereClause.append(" AND ANSWER LIKE '%' || ? || '%'");
        }
        if (explanation != null && !explanation.isEmpty()) {
            whereClause.append(" AND EXPLANATION LIKE '%' || ? || '%'");
        }
        if (level != null) {
            whereClause.append(" AND LEVEL = ?");
        }
        logger.debug("whereClause::{}", whereClause);

        // 정렬 세팅
        // '?'를 이용해서 파라미터를 세팅하는 방식이 아니므로 SQL 인젝션에 취약합니다.
        // 그래서 각각의 값을 검사해야 합니다.
        StringBuilder orderByClause = new StringBuilder(" ORDER BY");
        if (sort != null && !sort.isEmpty()) {
            String[] split = sort.split(",");
            Set<String> availableColumns = Set.of("ID", "CATEGORY_CD", "QUESTION", "OPTION_1", "OPTION_2", "OPTION_3", "OPTION_4", "ANSWER", "EXPLANATION", "LEVEL", "REG_DTM", "MODI_DTM");
            Set<String> availableDirections = Set.of("DESC", "ASC");
            for (int i = 0; i < split.length; i += 2) {
                String column = split[i].toUpperCase();
                String direction = i + 1 < split.length? split[i + 1].toUpperCase() : "ASC";
                if (!availableColumns.contains(column)) {
                    return ResponseEntity.badRequest().body("'sort'에 올바른 컬럼명을 입력해주세요.");
                }
                if (!availableDirections.contains(direction)) {
                    return ResponseEntity.badRequest().body("'sort'에 올바른 정렬순서를 입력해주세요.");
                }
                
                if (i > 0) {
                    orderByClause.append(",");
                }
                orderByClause.append(" " + column + " " + direction);
            }
        } else {
            orderByClause.append(" ID DESC");
        }
        logger.debug("orderByClause::{}", orderByClause);

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();

            // 전체 개수 조회
            long totalCount = 0;
            StringBuilder totalSql = new StringBuilder("SELECT COUNT(*) FROM QUIZ_MASTER");
            totalSql.append(whereClause);
            logger.debug("전체 개수 조회 쿼리::{}", totalSql);

            preparedStatement = connection.prepareStatement(totalSql.toString());

            // 쿼리 파라미터 세팅
            int parameterIndex = 1;
            if (categoryCd != null && !categoryCd.isEmpty()) {  
                preparedStatement.setString(parameterIndex++, categoryCd);
            }
            if (question != null && !question.isEmpty()) {
                preparedStatement.setString(parameterIndex++, question);
            }
            if (option != null && !option.isEmpty()) {
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
            }
            if (answer != null) {
                preparedStatement.setLong(parameterIndex++, answer);
            }
            if (explanation != null && !explanation.isEmpty()) {
                preparedStatement.setString(parameterIndex++, explanation);
            }
            if (level != null) {
                preparedStatement.setLong(parameterIndex++, level);
            }

            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                totalCount = resultSet.getLong(1);
            }

            // 목록 조회
            StringBuilder sql = new StringBuilder("SELECT * FROM QUIZ_MASTER");
            sql.append(whereClause);
            sql.append(orderByClause);
            sql.append(" LIMIT ? OFFSET ?");
            logger.debug("목록 조회 쿼리::{}", sql);

            resultSet.close();
            preparedStatement.close();
            preparedStatement = connection.prepareStatement(sql.toString());

            // 쿼리 파라미터 세팅
            // 오프셋 계산
            int offset = (page - 1) * size;

            parameterIndex = 1;
            if (categoryCd != null && !categoryCd.isEmpty()) {  
                preparedStatement.setString(parameterIndex++, categoryCd);
            }
            if (question != null && !question.isEmpty()) {
                preparedStatement.setString(parameterIndex++, question);
            }
            if (option != null && !option.isEmpty()) {
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
            }
            if (answer != null) {
                preparedStatement.setLong(parameterIndex++, answer);
            }
            if (explanation != null && !explanation.isEmpty()) {
                preparedStatement.setString(parameterIndex++, explanation);
            }
            if (level != null) {
                preparedStatement.setLong(parameterIndex++, level);
            }
            preparedStatement.setLong(parameterIndex++, size);
            preparedStatement.setLong(parameterIndex++, offset);

            resultSet = preparedStatement.executeQuery();

            List<Map<String, Object>> datas = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", resultSet.getLong("ID"));
                result.put("categoryCd", resultSet.getString("CATEGORY_CD"));
                result.put("question", resultSet.getString("QUESTION"));
                result.put("option1", resultSet.getString("OPTION_1"));
                result.put("option2", resultSet.getString("OPTION_2"));
                result.put("option3", resultSet.getString("OPTION_3"));
                result.put("option4", resultSet.getString("OPTION_4"));
                result.put("answer", resultSet.getString("ANSWER"));
                result.put("explanation", resultSet.getString("EXPLANATION"));
                result.put("level", resultSet.getLong("LEVEL"));
                result.put("randomYn", resultSet.getString("RANDOM_YN"));
                result.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());
                result.put("modiDtm", resultSet.getTimestamp("MODI_DTM").toLocalDateTime());

                datas.add(result);
            }

            // 응답 객체 생성
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount); // 전체 데이터 수
            result.put("totalPage", (int) Math.ceil((double) totalCount / size)); // 전체 페이지 수
            result.put("currentPage", page); // 현재 페이지
            result.put("list", datas); // 현재 페이지 데이터

            logger.info("목록 조회 완료::{}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("목록 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 랜덤 목록 조회
    @Operation(summary = "랜덤 목록 조회", description = "조건에 해당하는 데이터 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping("/quizzes/random")
    public ResponseEntity<?> findRandom(
        @Parameter(description = "카테고리 조건", example = "정보처리기사") @RequestParam(required = false) String categoryCd,
        @Parameter(description = "출력 개수", example = "5") @RequestParam(defaultValue = "5") int amount
    ) {
        logger.info("목록 조회 시작::categoryCd={},amount={}", categoryCd, amount);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT A.*");
        sql.append("     , COALESCE(B.CNT, 0) AS SOLVE_COUNT");
        sql.append("  FROM QUIZ_MASTER A");
        sql.append("  LEFT OUTER JOIN (");
        sql.append("                    SELECT QUIZ_ID");
        sql.append("                         , COUNT(*) AS CNT");
        sql.append("                      FROM QUIZ_HISTORY");
        sql.append("                     GROUP BY QUIZ_ID");
        sql.append("                  ) B");
        sql.append("    ON A.ID = B.QUIZ_ID");
        sql.append(" WHERE A.USE_YN = 'Y'");
        if (categoryCd != null && !categoryCd.trim().isEmpty()) {
            sql.append("   AND A.CATEGORY_CD = ?");
        }
        sql.append(" ORDER BY RANDOM() / (COALESCE(B.CNT, 0) + 1) DESC");
        sql.append(" LIMIT ?");
        logger.debug("sql::{}", sql);


        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();

            // 목록 조회
            preparedStatement = connection.prepareStatement(sql.toString());

            // 쿼리 파라미터 세팅
            int parameterIndex = 1;
            if (categoryCd != null && !categoryCd.trim().isEmpty()) {
                preparedStatement.setString(parameterIndex++, categoryCd);
            }
            preparedStatement.setLong(parameterIndex++, amount);

            resultSet = preparedStatement.executeQuery();

            List<Map<String, Object>> datas = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", resultSet.getLong("ID"));
                result.put("categoryCd", resultSet.getString("CATEGORY_CD"));
                result.put("question", resultSet.getString("QUESTION"));
                result.put("option1", resultSet.getString("OPTION_1"));
                result.put("option2", resultSet.getString("OPTION_2"));
                result.put("option3", resultSet.getString("OPTION_3"));
                result.put("option4", resultSet.getString("OPTION_4"));
                result.put("answer", resultSet.getString("ANSWER"));
                result.put("explanation", resultSet.getString("EXPLANATION"));
                result.put("level", resultSet.getLong("LEVEL"));
                result.put("randomYn", resultSet.getString("RANDOM_YN"));
                result.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());
                result.put("modiDtm", resultSet.getTimestamp("MODI_DTM").toLocalDateTime());

                datas.add(result);
            }

            // 응답 객체 생성
            Map<String, Object> result = new HashMap<>();
            result.put("list", datas); // 현재 페이지 데이터

            logger.info("목록 조회 완료::{}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("목록 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 엑셀 다운로드
    @Operation(summary = "엑셀 다운로드", description = "조건에 해당하는 데이터 목록을 엑셀로 다운로드합니다.")
    @ApiResponse(responseCode = "200", description = "상세 조회 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @GetMapping("/quizzes/excel-download")
    public void excelDownload(
        @Parameter(description = "카테고리 조건", example = "정보처리기사") @RequestParam(required = false) String categoryCd,
        @Parameter(description = "문제 내용 조건", example = "클래스") @RequestParam(required = false) String question,
        @Parameter(description = "선택지 조건", example = "자바") @RequestParam(required = false) String option,
        @Parameter(description = "정답 번호 조건", example = "1") @RequestParam(required = false) Integer answer,
        @Parameter(description = "정답 해설 조건", example = "클래스") @RequestParam(required = false) String explanation,
        @Parameter(description = "난이도 조건", example = "1") @RequestParam(required = false) Integer level,
        @Parameter(description = "등록 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regStart,
        @Parameter(description = "등록 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime regEnd,
        @Parameter(description = "수정 시간 시작 조건", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptStart,
        @Parameter(description = "수정 시간 종료 조건", example = "2030-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime uptEnd,
        @Parameter(description = "정렬 방법", example = "id,desc") @RequestParam(required = false) String sort,
        HttpServletResponse response
    ) {
        logger.info("엑셀 다운로드 시작::categoryCd={},question={},option={},answer={},explanation={},level={},regStart={},regEnd={},uptStart={},uptEnd={},sort={}", categoryCd, question, option, answer, explanation, level, regStart, regEnd, uptStart, uptEnd, sort);


        // 조회 조건 세팅
        StringBuilder whereClause = new StringBuilder(" WHERE USE_YN = 'Y'");
        if (categoryCd != null && !categoryCd.isEmpty()) {
            whereClause.append(" AND CATEGORY_CD LIKE '%' || ? || '%'");
        }
        if (question != null && !question.isEmpty()) {
            whereClause.append(" AND QUESTION LIKE '%' || ? || '%'");
        }
        if (option != null && !option.isEmpty()) {
            whereClause.append(" AND (OPTION_1 LIKE '%' || ? || '%' OR OPTION_2 LIKE '%' || ? || '%' OR OPTION_3 LIKE '%' || ? || '%' OR OPTION_4 LIKE '%' || ? || '%')");
        }
        if (answer != null) {
            whereClause.append(" AND ANSWER LIKE '%' || ? || '%'");
        }
        if (explanation != null && !explanation.isEmpty()) {
            whereClause.append(" AND EXPLANATION LIKE '%' || ? || '%'");
        }
        if (level != null) {
            whereClause.append(" AND LEVEL = ?");
        }
        logger.debug("whereClause::{}", whereClause);

        // 정렬 세팅
        // '?'를 이용해서 파라미터를 세팅하는 방식이 아니므로 SQL 인젝션에 취약합니다.
        // 그래서 각각의 값을 검사해야 합니다.
        StringBuilder orderByClause = new StringBuilder(" ORDER BY");
        if (sort != null && !sort.isEmpty()) {
            String[] split = sort.split(",");
            Set<String> availableColumns = Set.of("ID", "CATEGORY_CD", "QUESTION", "OPTION_1", "OPTION_2", "OPTION_3", "OPTION_4", "ANSWER", "EXPLANATION", "LEVEL", "REG_DTM", "MODI_DTM");
            Set<String> availableDirections = Set.of("DESC", "ASC");
            for (int i = 0; i < split.length; i += 2) {
                String column = split[i].toUpperCase();
                String direction = i + 1 < split.length? split[i + 1].toUpperCase() : "ASC";
                if (!availableColumns.contains(column)) {
                    return;
                }
                if (!availableDirections.contains(direction)) {
                    return;
                }
                
                if (i > 0) {
                    orderByClause.append(",");
                }
                orderByClause.append(" " + column + " " + direction);
            }
        } else {
            orderByClause.append(" ID DESC");
        }
        logger.debug("orderByClause::{}", orderByClause);

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        Workbook workbook = null;

        try {
            connection = dataSource.getConnection();

            // 목록 조회
            StringBuilder sql = new StringBuilder("SELECT * FROM QUIZ_MASTER");
            sql.append(whereClause);
            sql.append(orderByClause);
            logger.debug("목록 조회 쿼리::{}", sql);

            preparedStatement = connection.prepareStatement(sql.toString());

            // 쿼리 파라미터 세팅
            int parameterIndex = 1;
            if (categoryCd != null && !categoryCd.isEmpty()) {  
                preparedStatement.setString(parameterIndex++, categoryCd);
            }
            if (question != null && !question.isEmpty()) {
                preparedStatement.setString(parameterIndex++, question);
            }
            if (option != null && !option.isEmpty()) {
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
                preparedStatement.setString(parameterIndex++, option);
            }
            if (answer != null) {
                preparedStatement.setLong(parameterIndex++, answer);
            }
            if (explanation != null && !explanation.isEmpty()) {
                preparedStatement.setString(parameterIndex++, explanation);
            }
            if (level != null) {
                preparedStatement.setLong(parameterIndex++, level);
            }

            resultSet = preparedStatement.executeQuery();

            List<Map<String, Object>> datas = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", resultSet.getLong("ID"));
                result.put("categoryCd", resultSet.getString("CATEGORY_CD"));
                result.put("question", resultSet.getString("QUESTION"));
                result.put("option1", resultSet.getString("OPTION_1"));
                result.put("option2", resultSet.getString("OPTION_2"));
                result.put("option3", resultSet.getString("OPTION_3"));
                result.put("option4", resultSet.getString("OPTION_4"));
                result.put("answer", resultSet.getString("ANSWER"));
                result.put("explanation", resultSet.getString("EXPLANATION"));
                result.put("level", resultSet.getLong("LEVEL"));
                result.put("regDtm", resultSet.getTimestamp("REG_DTM").toLocalDateTime());
                result.put("modiDtm", resultSet.getTimestamp("MODI_DTM").toLocalDateTime());

                datas.add(result);
            }

            // 엑셀 생성
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("학습 내용 목록");

            // 시트의 컬럼별 너비 설정
            sheet.setColumnWidth(2, 100 * 256);
            sheet.setColumnWidth(3, 50 * 256);
            sheet.setColumnWidth(4, 50 * 256);
            sheet.setColumnWidth(5, 50 * 256);
            sheet.setColumnWidth(6, 50 * 256);
            sheet.setColumnWidth(8, 100 * 256);

            // 폰트 스타일 생성
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            // 셀 스타일 생성
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFont(headerFont);

            // 헤더 생성
            Row headerRow = sheet.createRow(0);
            String[] headers = {"카테고리", "문제", "보기1", "보기2", "보기3", "보기4", "정답 번호", "해설", "난이도", "등록 시간", "수정 시간"};
            for (int i = 0; i < headers.length; i++) {
                Cell headerCell = headerRow.createCell(i);
                headerCell.setCellValue(headers[i]);
                headerCell.setCellStyle(headerStyle);
            }

            // 데이터 행 추가
            int rowIndex = 1;
            DateTimeFormatter dtmFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (Map<String, Object> rowData : datas) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(String.valueOf(rowData.get("categoryCd")));
                row.createCell(1).setCellValue(String.valueOf(rowData.get("question")));
                row.createCell(2).setCellValue(String.valueOf(rowData.get("option1")));
                row.createCell(3).setCellValue(String.valueOf(rowData.get("option2")));
                row.createCell(4).setCellValue(String.valueOf(rowData.get("option3")));
                row.createCell(5).setCellValue(String.valueOf(rowData.get("option4")));
                row.createCell(6).setCellValue(String.valueOf(rowData.get("answer")));
                row.createCell(7).setCellValue(String.valueOf(rowData.get("explanation")));
                row.createCell(8).setCellValue(String.valueOf(rowData.get("level")));
                row.createCell(9).setCellValue(((LocalDateTime) rowData.get("regDtm")).format(dtmFormatter));
                row.createCell(10).setCellValue(((LocalDateTime) rowData.get("modiDtm")).format(dtmFormatter));
            }

            // 브라우저 다운로드 설정 및 출력
            String fileName = "QUIZ_LIST_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            workbook.write(response.getOutputStream());

            logger.info("엑셀 다운로드 완료");
        } catch (Exception e) {
            logger.error("엑셀 다운로드 중 오류 발생", e);
        } finally {
            try {
                if (workbook != null) {
                    workbook.close();
                }
            } catch (IOException e) {
                logger.error("Workbook 리소스 반환 중 오류", e);
            }
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 단건 수정
    @Operation(summary = "단건 수정", description = "아이디에 해당하는 데이터 정보를 전체 수정합니다.")
    @ApiResponse(responseCode = "200", description = "단건 수정 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PutMapping(value = "/quizzes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
        @Parameter(description = "아이디 조건", example = "1") @PathVariable("id") Long id,
        @Parameter(description = "데이터 정보", schema = @Schema(type = "Object", example = "{}")) @RequestPart("data") Map<String, Object> data, 
        @Parameter(description = "업로드 파일", required = false) @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        logger.info("단건 수정 시작::id={},data={},files={}", id, data, files);
        
        // 파라미터 추출
        String categoryCd = ParserUtil.parseString(data.get("categoryCd"));
        String question = ParserUtil.parseString(data.get("question"));
        String option1 = ParserUtil.parseString(data.get("option1"));
        String option2 = ParserUtil.parseString(data.get("option2"));
        String option3 = ParserUtil.parseString(data.get("option3"));
        String option4 = ParserUtil.parseString(data.get("option4"));
        Integer answer = ParserUtil.parseInteger(data.get("answer"));
        String explanation = ParserUtil.parseString(data.get("explanation"));
        Integer level = ParserUtil.parseInteger(data.get("level"));
        
        // 필수 값 체크
        if (categoryCd == null || categoryCd.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'categoryCd'는 필수 값입니다.");
        }
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'question'는 필수 값입니다.");
        }
        if (option1 == null || option1.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option1'는 필수 값입니다.");
        }
        if (option2 == null || option2.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option2'는 필수 값입니다.");
        }
        if (option3 == null || option3.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option3'는 필수 값입니다.");
        }
        if (option4 == null || option4.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("'option4'는 필수 값입니다.");
        }
        if (answer == null) {
            return ResponseEntity.badRequest().body("'answer'는 필수 값입니다.");
        }

        // 길이 체크
        if (categoryCd.length() > 100) {
            return ResponseEntity.badRequest().body("'categoryCd'는 100자를 초과할 수 없습니다.");
        }
        if (option1.length() > 500) {
            return ResponseEntity.badRequest().body("'option1'는 500자를 초과할 수 없습니다.");
        }
        if (option2.length() > 500) {
            return ResponseEntity.badRequest().body("'option2'는 500자를 초과할 수 없습니다.");
        }
        if (option3.length() > 500) {
            return ResponseEntity.badRequest().body("'option3'는 500자를 초과할 수 없습니다.");
        }
        if (option4.length() > 500) {
            return ResponseEntity.badRequest().body("'option4'는 500자를 초과할 수 없습니다.");
        }

        // 형식 체크
        if (answer < 1 || answer > 4) {
            return ResponseEntity.badRequest().body("'answer'는 1에서 4 사이의 숫자여야 합니다.");
        }
        if (level != null && (level < 1 || level > 3)) {
            return ResponseEntity.badRequest().body("'level'는 1에서 3 사이의 숫자여야 합니다.");
        }

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            logger.debug("퀴즈 단건 수정 시작");
            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE QUIZ_MASTER");
            sql.append("   SET CATEGORY_CD = ?");
            sql.append("     , QUESTION = ?");
            sql.append("     , OPTION_1 = ?");
            sql.append("     , OPTION_2 = ?");
            sql.append("     , OPTION_3 = ?");
            sql.append("     , OPTION_4 = ?");
            sql.append("     , ANSWER = ?");
            sql.append("     , EXPLANATION = ?");
            sql.append("     , LEVEL = ?");
            sql.append(" WHERE ID = ?");
            logger.debug("퀴즈 수정 쿼리::{}", sql);

            // 퀴즈 수정 쿼리 실행 준비
            preparedStatement = connection.prepareStatement(sql.toString());

            // 퀴즈 수정 쿼리 파라미터 세팅
            preparedStatement.setString(1, categoryCd);
            preparedStatement.setString(2, question);
            preparedStatement.setString(3, option1);
            preparedStatement.setString(4, option2);
            preparedStatement.setString(5, option3);
            preparedStatement.setString(6, option4);
            preparedStatement.setLong(7, answer);
            preparedStatement.setString(8, explanation);
            preparedStatement.setLong(9, level);
            preparedStatement.setLong(10, id);

            // 퀴즈 등록 쿼리 실행
            int updateCount = preparedStatement.executeUpdate();
            if (updateCount == 0) {
                logger.error("퀴즈 단건 수정 실패");
                return ResponseEntity.internalServerError().body("퀴즈 단건 수정 실패");
            }

            // 기존 파일 목록 조회
            List<String> existFileNames = new ArrayList<>();
            preparedStatement.close();

            logger.debug("퀴즈 파일 목록 조회 시작");
            StringBuilder quizSelectSql = new StringBuilder("SELECT STORED_NAME FROM QUIZ_FILES WHERE QUIZ_ID = ?");
            logger.debug("퀴즈 파일 목록 조회 쿼리::{}", quizSelectSql);

            preparedStatement = connection.prepareStatement(quizSelectSql.toString());
            preparedStatement.setLong(1, id);

            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                existFileNames.add(resultSet.getString("STORED_NAME"));
            }

            logger.info("단건 수정 완료");
            return ResponseEntity.ok("단건 수정 완료");
        } catch (Exception e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                logger.error("Connection 롤백 중 오류", e);
            }
            
            logger.error("단건 수정 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 단건 패치
    @Operation(summary = "단건 패치", description = "아이디에 해당하는 데이터 정보를 부분 수정합니다.")
    @ApiResponse(responseCode = "200", description = "단건 패치 성공")
    @ApiResponse(responseCode = "400", description = "데이터 유효성 검사 실패")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PatchMapping(value = "/quizzes/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> patch(
        @Parameter(description = "아이디 조건", example = "1") @PathVariable("id") Long id,
        @Parameter(description = "데이터 정보", schema = @Schema(type = "Object", example = "{}")) @RequestPart(value = "data", required = false) Map<String, Object> data, 
        @Parameter(description = "업로드 파일", required = false) @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        logger.info("단건 패치 시작::id={},data={},files={}", id, data, files);
        
        // 파라미터 추출
        String categoryCd = ParserUtil.parseString(data.get("categoryCd"));
        String question = ParserUtil.parseString(data.get("question"));
        String option1 = ParserUtil.parseString(data.get("option1"));
        String option2 = ParserUtil.parseString(data.get("option2"));
        String option3 = ParserUtil.parseString(data.get("option3"));
        String option4 = ParserUtil.parseString(data.get("option4"));
        Integer answer = ParserUtil.parseInteger(data.get("answer"));
        String explanation = ParserUtil.parseString(data.get("explanation"));
        Integer level = ParserUtil.parseInteger(data.get("level"));
        
        // 필수 값 체크
        if (categoryCd != null) {
            if (categoryCd.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'categoryCd'는 필수 값입니다.");
            }
            if (categoryCd.length() > 100) {
                return ResponseEntity.badRequest().body("'categoryCd'는 100자를 초과할 수 없습니다.");
            }
        }
        if (question != null) {
            if (question.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'question'는 필수 값입니다.");
            }
        }
        if (option1 != null) {
            if (option1.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'option1'는 필수 값입니다.");
            }
            if (option1.length() > 500) {
                return ResponseEntity.badRequest().body("'option1'는 500자를 초과할 수 없습니다.");
            }
        }
        if (option2 != null) {
            if (option2.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'option2'는 필수 값입니다.");
            }
            if (option2.length() > 500) {
                return ResponseEntity.badRequest().body("'option2'는 500자를 초과할 수 없습니다.");
            }
        }
        if (option3 != null) {
            if (option3.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'option3'는 필수 값입니다.");
            }
            if (option3.length() > 500) {
                return ResponseEntity.badRequest().body("'option3'는 500자를 초과할 수 없습니다.");
            }
        }
        if (option4 != null) {
            if (option4.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("'option4'는 필수 값입니다.");
            }
            if (option4.length() > 500) {
                return ResponseEntity.badRequest().body("'option4'는 500자를 초과할 수 없습니다.");
            }
        }
        if (answer != null) {
            if (answer < 1 || answer > 4) {
                return ResponseEntity.badRequest().body("'answer'는 1에서 4 사이의 숫자여야 합니다.");
            }
        }
        if (level != null) {
            if (level != null && (level < 1 || level > 3)) {
                return ResponseEntity.badRequest().body("'level'는 1에서 3 사이의 숫자여야 합니다.");
            }
        }

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            logger.debug("퀴즈 단건 패치 시작");
            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE QUIZ_MASTER");
            sql.append("   SET MODI_DTM = CURRENT_TIMESTAMP");
            if (categoryCd != null) {
                sql.append("     , CATEGORY_CD = ?");
            }
            if (question != null) {
                sql.append("     , QUESTION = ?");
            }
            if (option1 != null) {
                sql.append("     , OPTION_1 = ?");
            }
            if (option2 != null) {
                sql.append("     , OPTION_2 = ?");
            }
            if (option3 != null) {
                sql.append("     , OPTION_3 = ?");
            }
            if (option4 != null) {
                sql.append("     , OPTION_4 = ?");
            }
            if (answer != null) {
                sql.append("     , ANSWER = ?");
            }
            if (explanation != null) {
                sql.append("     , EXPLANATION = ?");
            }
            if (level != null) {
                sql.append("     , LEVEL = ?");
            }
            sql.append(" WHERE ID = ?");
            logger.debug("퀴즈 패치 쿼리::{}", sql);

            // 퀴즈 패치 쿼리 실행 준비
            preparedStatement = connection.prepareStatement(sql.toString());

            // 퀴즈 패치 쿼리 파라미터 세팅
            int parameterIndex = 1;
            if (categoryCd != null) {
                preparedStatement.setString(parameterIndex++, categoryCd);
            }
            if (question != null) {
                preparedStatement.setString(parameterIndex++, question);
            }
            if (option1 != null) {
                preparedStatement.setString(parameterIndex++, option1);
            }
            if (option2 != null) {
                preparedStatement.setString(parameterIndex++, option2);
            }
            if (option3 != null) {
                preparedStatement.setString(parameterIndex++, option3);
            }
            if (option4 != null) {
                preparedStatement.setString(parameterIndex++, option4);
            }
            if (answer != null) {
                preparedStatement.setLong(parameterIndex++, answer);
            }
            if (explanation != null) {
                preparedStatement.setString(parameterIndex++, explanation);
            }
            if (level != null) {
                preparedStatement.setLong(parameterIndex++, level);
            }
            preparedStatement.setLong(parameterIndex++, id);

            // 퀴즈 패치 쿼리 실행
            int patchCount = preparedStatement.executeUpdate();
            if (patchCount == 0) {
                logger.error("퀴즈 단건 패치 실패");
                return ResponseEntity.internalServerError().body("퀴즈 단건 패치 실패");
            }

            logger.info("단건 패치 완료");
            return ResponseEntity.ok("단건 패치 완료");
        } catch (Exception e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException e1) {
                logger.error("Connection 롤백 중 오류", e);
            }
            
            logger.error("단건 패치 중 오류 발생", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
            } catch (SQLException e) {
                logger.error("ResultSet 리소스 반환 중 오류", e);
            }
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }

    // 단건 논리적 삭제
    @Operation(summary = "단건 논리적 삭제", description = "아이디에 해당하는 데이터 정보를 논리적으로 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "단건 논리적 삭제 성공")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @DeleteMapping("/quizzes/{id}/trash")
    public ResponseEntity<?> softRemove(@Parameter(description = "아이디 조건", example = "1") @PathVariable("id") Long id) {
        logger.info("단건 논리적 삭제 시작::id={}", id);
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = dataSource.getConnection();

            StringBuilder sql = new StringBuilder("UPDATE QUIZ_MASTER SET USE_YN = 'N' WHERE USE_YN = 'Y' AND ID = ?");
            preparedStatement = connection.prepareStatement(sql.toString());

            preparedStatement.setLong(1, id);

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
            try {
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
            } catch (SQLException e) {
                logger.error("PreparedStatement 리소스 반환 중 오류", e);
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.error("Connection 리소스 반환 중 오류", e);
            }
        }
    }
}