package com.aeacoh.quizBank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCUtil {
    private static final Logger logger = LoggerFactory.getLogger(JDBCUtil.class);

    public static void closeAll(ResultSet resultSet, PreparedStatement preparedStatement, Connection connection) {
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
