package com.club.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 转义单元测试(纯字符串处理, 不依赖 Spring 容器与数据库)。
 *
 * <p>报表导出最容易出问题的地方不是"拼字符串", 而是字段里混入逗号 / 引号 / 换行
 * 后整行错列, 以及缺了 BOM 导致 Excel 中文乱码。这里逐条覆盖。
 */
class CsvWriterTest {

    @Test
    @DisplayName("普通字段直接以逗号连接, 行尾为 CRLF")
    void shouldJoinPlainCellsWithComma() {
        String csv = new CsvWriter().row("排名", "社团名称", 73.3).build();
        assertEquals("排名,社团名称,73.3\r\n", csv.substring(CsvWriter.BOM.length()));
    }

    @Test
    @DisplayName("输出以 UTF-8 BOM 开头, 保证 Excel 打开中文不乱码")
    void shouldStartWithUtf8Bom() {
        String csv = new CsvWriter().row("社团").build();
        assertTrue(csv.startsWith(CsvWriter.BOM), "首字符必须是 BOM");
        assertEquals('\uFEFF', csv.charAt(0));
    }

    @Test
    @DisplayName("含逗号的字段用双引号包裹, 不会被拆成两列")
    void shouldQuoteCellContainingComma() {
        assertEquals("\"街舞社,南区分社\"", CsvWriter.escape("街舞社,南区分社"));
    }

    @Test
    @DisplayName("含双引号的字段: 内部引号翻倍并整体包裹")
    void shouldDoubleInnerQuote() {
        assertEquals("\"他说\"\"你好\"\"\"", CsvWriter.escape("他说\"你好\""));
    }

    @Test
    @DisplayName("含换行的字段被包裹, 避免一行裂成两行")
    void shouldQuoteCellContainingNewline() {
        assertEquals("\"第一行\n第二行\"", CsvWriter.escape("第一行\n第二行"));
    }

    @Test
    @DisplayName("null 转空字符串, 不出现字面量 null")
    void shouldRenderNullAsEmpty() {
        assertEquals("", CsvWriter.escape(null));
        String csv = new CsvWriter().row("a", null, "c").build();
        assertEquals("a,,c\r\n", csv.substring(CsvWriter.BOM.length()));
    }

    @Test
    @DisplayName("数字与布尔等非字符串类型正常输出")
    void shouldHandleNonStringTypes() {
        String csv = new CsvWriter().row(1, 0.5, true).build();
        assertEquals("1,0.5,true\r\n", csv.substring(CsvWriter.BOM.length()));
    }

    @Test
    @DisplayName("blank / title 组合能输出可读的分块报表")
    void shouldComposeReportBlocks() {
        String csv = new CsvWriter()
                .title("一、社团活跃度")
                .row("指标", "数值")
                .row("社团总数", 3)
                .blank()
                .build();
        assertEquals("一、社团活跃度\r\n指标,数值\r\n社团总数,3\r\n\r\n",
                csv.substring(CsvWriter.BOM.length()));
    }
}
