package com.mall.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 教学周解析工具：weeks 字段形如 "1-16" 或 "1-8,10-16"。
 * 放 common 是因为 enroll-service 的时间冲突检测和 course-service 都可能用到，
 * 纯函数、无框架依赖。
 */
public final class WeeksUtil {

    private WeeksUtil() {
    }

    /** 解析为 [start,end] 区间列表；非法格式抛 IllegalArgumentException */
    public static List<int[]> parse(String weeks) {
        List<int[]> ranges = new ArrayList<>();
        if (weeks == null || weeks.isBlank()) {
            throw new IllegalArgumentException("教学周不能为空");
        }
        for (String part : weeks.split(",")) {
            String p = part.trim();
            if (p.contains("-")) {
                String[] se = p.split("-");
                int start = Integer.parseInt(se[0].trim());
                int end = Integer.parseInt(se[1].trim());
                if (start < 1 || end < start) {
                    throw new IllegalArgumentException("非法教学周区间: " + p);
                }
                ranges.add(new int[]{start, end});
            } else {
                int w = Integer.parseInt(p);
                ranges.add(new int[]{w, w});
            }
        }
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("非法教学周: " + weeks);
        }
        return ranges;
    }

    /** 两门课的教学周是否有重叠 */
    public static boolean overlap(String weeksA, String weeksB) {
        try {
            List<int[]> a = parse(weeksA);
            List<int[]> b = parse(weeksB);
            for (int[] ra : a) {
                for (int[] rb : b) {
                    if (ra[0] <= rb[1] && rb[0] <= ra[1]) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            // 教学周格式异常时按"可能冲突"保守处理，避免漏检导致学生撞课
            return true;
        }
    }

    /** 节次区间是否有重叠：[1,2] 与 [2,3] 算冲突（第 2 节重了） */
    public static boolean sectionOverlap(int startA, int endA, int startB, int endB) {
        return startA <= endB && startB <= endA;
    }
}
