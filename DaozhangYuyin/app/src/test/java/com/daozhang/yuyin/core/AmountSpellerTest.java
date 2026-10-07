package com.daozhang.yuyin.core;

import static org.junit.Assert.assertEquals;

import com.daozhang.yuyin.core.AmountSpeller.Options;
import com.daozhang.yuyin.core.AmountSpeller.Style;

import org.junit.Test;

public class AmountSpellerTest {

    private static final Options ORAL = new Options(Style.COLLOQUIAL, true, false);
    private static final Options ORAL_ER = new Options(Style.COLLOQUIAL, false, false);
    private static final Options STD = new Options(Style.STANDARD, false, false);

    private static String oral(String amount) {
        return AmountSpeller.spell(AmountSpeller.parseFen(amount), ORAL);
    }

    private static String std(String amount) {
        return AmountSpeller.spell(AmountSpeller.parseFen(amount), STD);
    }

    @Test public void integers() {
        assertEquals("十块", oral("10"));
        assertEquals("十五块", oral("15"));
        assertEquals("一百一十五块", oral("115"));
        assertEquals("一千零五块", oral("1005"));
        assertEquals("一万零五十块", oral("10050"));
        assertEquals("一万零五块", oral("10005"));
        assertEquals("九万九千九百九十九块", oral("99999"));
        assertEquals("一千零一十块", oral("1010"));
        assertEquals("一万一千块", oral("11000"));
    }

    @Test public void liang() {
        assertEquals("两块", oral("2"));
        assertEquals("二十二块", oral("22"));
        assertEquals("两百块", oral("200"));
        assertEquals("两千两百二十块", oral("2220"));
        assertEquals("两万块", oral("20000"));
        assertEquals("二百块", AmountSpeller.spell(20000, ORAL_ER));
        assertEquals("两毛", oral("0.2"));
    }

    @Test public void decimals() {
        assertEquals("十九块九", oral("19.9"));
        assertEquals("十九块九", oral("19.90"));
        assertEquals("十九块九毛五", oral("19.95"));
        assertEquals("十九块零五分", oral("19.05"));
        assertEquals("五毛", oral("0.5"));
        assertEquals("五毛五", oral("0.55"));
        assertEquals("一分", oral("0.01"));
        assertEquals("九万九千九百九十九块九毛九", oral("99999.99"));
    }

    @Test public void standard() {
        assertEquals("十九点九元", std("19.9"));
        assertEquals("十九点零五元", std("19.05"));
        assertEquals("零点零一元", std("0.01"));
        assertEquals("二百元", std("200"));
        assertEquals("二十元整", AmountSpeller.spell(2000, new Options(Style.STANDARD, false, true)));
    }

    @Test public void parsing() {
        assertEquals(1990, AmountSpeller.parseFen("19.9"));
        assertEquals(1, AmountSpeller.parseFen("0.01"));
        assertEquals(9_999_999, AmountSpeller.parseFen("99999.99"));
        assertEquals(-1, AmountSpeller.parseFen("0"));
        assertEquals(-1, AmountSpeller.parseFen("0.00"));
        assertEquals(-1, AmountSpeller.parseFen("100000"));
        assertEquals(-1, AmountSpeller.parseFen("1.234"));
        assertEquals(-1, AmountSpeller.parseFen("-5"));
        assertEquals(-1, AmountSpeller.parseFen(""));
        assertEquals(-1, AmountSpeller.parseFen("abc"));
    }
}
