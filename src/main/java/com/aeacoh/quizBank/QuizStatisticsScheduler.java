package com.aeacoh.quizBank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@Component
public class QuizStatisticsScheduler {
    private final Logger logger = LoggerFactory.getLogger(CategoryController.class);
    private final DataSource dataSource;
    private final BrevoEmailSender emailSender;

    @Value("${brevo.target.email}")
    private String targetEmail;

    @Value("${brevo.use")
    private String brevoUse;

    public QuizStatisticsScheduler(DataSource dataSource, BrevoEmailSender emailSender) {
        this.dataSource = dataSource;
        this.emailSender = emailSender;
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Seoul")
    public void sendStatistics() {
        if ("N".equals(brevoUse)) {
            return;
        }
        logger.info("QuizStatisticsScheduler::sendStatistics START");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT A.DT");
        sql.append("     , A.NAME");
        sql.append("     , COALESCE(B.CNT, 0) AS TOTAL_CNT");
        sql.append("     , COALESCE(B.CORRECT_CNT, 0) AS CORRECT_CNT");
        sql.append("     , COALESCE(B.CORRECT_RATE, 0.00) AS POINT");
        sql.append("  FROM (");
        sql.append("         SELECT A.DT");
        sql.append("              , B.CODE");
        sql.append("              , B.NAME");
        sql.append("           FROM (");
        sql.append("                  SELECT DISTINCT TO_CHAR(REG_DTM, 'YYYY-MM-DD') AS DT");
        sql.append("                    FROM QUIZ_HISTORY");
        sql.append("                ) A");
        sql.append("          CROSS JOIN (");
        sql.append("                       SELECT 'A' AS CODE");
        sql.append("                            , '1과목 : 소프트웨어 설계' AS NAME");
        sql.append("                       UNION ALL");
        sql.append("                       SELECT 'B' AS CODE");
        sql.append("                            , '2과목 : 소프트웨어 개발' AS NAME");
        sql.append("                       UNION ALL");
        sql.append("                       SELECT 'C' AS CODE");
        sql.append("                            , '3과목 : 데이터베이스 구축' AS NAME");
        sql.append("                       UNION ALL");
        sql.append("                       SELECT 'D' AS CODE");
        sql.append("                            , '4과목 : 프로그래밍 언어 활용' AS NAME");
        sql.append("                       UNION ALL");
        sql.append("                       SELECT 'E' AS CODE");
        sql.append("                            , '5과목 : 정보시스템 구축 관리' AS NAME");
        sql.append("                       UNION ALL");
        sql.append("                       SELECT 'TOTAL' AS CODE");
        sql.append("                            , '합계' AS NAME");
        sql.append("                     ) B");
        sql.append("          ORDER BY A.DT DESC");
        sql.append("                 , B.CODE ASC");
        sql.append("       ) A");
        sql.append("  LEFT OUTER JOIN (");
        sql.append("                    SELECT DT");
        sql.append("                         , COALESCE(CODE, 'TOTAL') AS CODE");
        sql.append("                         , COUNT(*) AS CNT");
        sql.append("                         , SUM(CASE WHEN CORRECT_YN = 'Y' THEN 1 ELSE 0 END) AS CORRECT_CNT");
        sql.append("                         , ROUND(SUM(CASE WHEN CORRECT_YN = 'Y' THEN 1.0 ELSE 0.0 END) / COUNT(*) * 100, 2) AS CORRECT_RATE");
        sql.append("                      FROM (");
        sql.append("                             SELECT TO_CHAR(A.REG_DTM, 'YYYY-MM-DD') AS DT");
        sql.append("                                  , CASE WHEN B.QUIZ_NO < 21 THEN 'A'");
        sql.append("                                         WHEN B.QUIZ_NO < 41 THEN 'B'");
        sql.append("                                         WHEN B.QUIZ_NO < 61 THEN 'C'");
        sql.append("                                         WHEN B.QUIZ_NO < 81 THEN 'D'");
        sql.append("                                         ELSE 'E'");
        sql.append("                                     END AS CODE");
        sql.append("                                   , A.CORRECT_YN");
        sql.append("                                FROM QUIZ_HISTORY A");
        sql.append("                               INNER JOIN (");
        sql.append("                                            SELECT ID");
        sql.append("                                                 , CAST(REGEXP_REPLACE(QUESTION, '^([0-9]+)\\..*', '\\1') AS INTEGER) AS QUIZ_NO");
        sql.append("                                              FROM QUIZ_MASTER");
        sql.append("                                          ) B");
        sql.append("                                  ON A.QUIZ_ID = B.ID");
        sql.append("                               WHERE A.SOLVE_TYPE = 'ALL'");
        sql.append("                           )");
        sql.append("                     GROUP BY DT");
        sql.append("                            , ROLLUP(CODE)");
        sql.append("                  ) B");
        sql.append("    ON A.DT = B.DT");
        sql.append("   AND A.CODE = B.CODE");
        sql.append(" ORDER BY 1 DESC");
        sql.append("     , CASE WHEN A.CODE = 'TOTAL' THEN 2 ELSE 1 END ASC");
        sql.append("     , A.CODE ASC;");
        logger.debug("sql::{}", sql);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql.toString());
             ResultSet resultSet = preparedStatement.executeQuery()) {

            StringBuilder html = new StringBuilder();
            html.append("<table>");
            html.append("   <tr>");
            html.append("       <th>");
            html.append("           일자");
            html.append("       </th>");
            html.append("       <th>");
            html.append("           과목");
            html.append("       </th>");
            html.append("       <th>");
            html.append("           문제 푼 개수");
            html.append("       </th>");
            html.append("       <th>");
            html.append("           정답 개수");
            html.append("       </th>");
            html.append("       <th>");
            html.append("           점수");
            html.append("       </th>");
            html.append("   </tr>");

            while (resultSet.next()) {
                html.append("   <tr>");
                html.append("       <th>");
                html.append("           " + resultSet.getString("DT"));
                html.append("       </th>");
                html.append("       <th>");
                html.append("           " + resultSet.getString("NAME"));
                html.append("       </th>");
                html.append("       <th>");
                html.append("           " + resultSet.getLong("TOTAL_CNT"));
                html.append("       </th>");
                html.append("       <th>");
                html.append("           " + resultSet.getLong("CORRECT_CNT"));
                html.append("       </th>");
                html.append("       <th>");
                html.append("           " + resultSet.getDouble("POINT"));
                html.append("       </th>");
                html.append("   </tr>");
            }

            html.append("</table>");
            logger.debug("html::{}", html);

            emailSender.sendEmail(targetEmail, "정보처리기사 통계", html.toString());
        } catch (SQLException e) {
            logger.error("QuizStatisticsScheduler::sendStatistics exception::{}", e.getMessage());
        }
    }
}
