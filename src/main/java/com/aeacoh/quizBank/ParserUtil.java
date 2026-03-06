package com.aeacoh.quizBank;

public class ParserUtil {
    public static String parseString(Object str) {
        if (str == null) {
            return null;
        }

        return String.valueOf(str);
    }

    public static Integer parseInteger(Object n) {
        try {
            return Integer.parseInt(String.valueOf(n));
        } catch (Exception e) {
            return null;
        }
    }
}
