package com.club.util;

/**
 * 极简 CSV 构建器, 供统计报表导出使用(设计说明书 表 2-8 的 export / 辅助类)。
 *
 * <h3>为什么需要它</h3>
 * CSV 不是"用逗号拼接"这么简单: 字段里一旦出现逗号、换行或双引号, 直接拼接会把
 * 一列裂成多列, Excel 打开后错位。本类统一处理转义, 并把 UTF-8 BOM 写在最前面
 * ——没有 BOM 时 Excel(简体中文 Windows)会按 GBK 解码, 中文全部乱码。
 *
 * <p>纯字符串处理, 不依赖 Spring 与数据库, 便于单元测试。
 */
public final class CsvWriter {

    /** UTF-8 BOM: 让 Excel 双击打开时正确识别编码, 否则中文乱码 */
    public static final String BOM = "\uFEFF";

    /** 换行符: Excel 对 CRLF 兼容最好 */
    public static final String EOL = "\r\n";

    private final StringBuilder sb = new StringBuilder(BOM);

    /** 追加一行(单元格内容自动转义) */
    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append(EOL);
        return this;
    }

    /** 追加一个空行, 用于分隔报表的不同区块 */
    public CsvWriter blank() {
        sb.append(EOL);
        return this;
    }

    /** 追加区块标题(独占一行) */
    public CsvWriter title(String text) {
        sb.append(escape(text)).append(EOL);
        return this;
    }

    /** 输出完整 CSV 文本(含 BOM) */
    public String build() {
        return sb.toString();
    }

    /**
     * CSV 字段转义: 含逗号 / 双引号 / 换行 / 回车时用双引号包裹, 内部双引号翻倍。
     * <p>null 视为空字符串, 避免导出时出现字面量 "null"。
     */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuote) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
