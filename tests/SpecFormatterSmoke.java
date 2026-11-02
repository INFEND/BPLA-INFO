package dev.infend.bplainfo;

/** Independent JVM smoke tests for Android's presentation-only unit formatter. */
public final class SpecFormatterSmoke {
    private static void eq(String source,String expected){
        String actual=SpecFormatter.display(source);
        if(!expected.equals(actual))throw new AssertionError(
            "Expected '"+expected+"' but got '"+actual+"' from '"+source+"'");
    }
    public static void main(String[] args){
        eq("100 км/ч","100 КМ/Ч");
        eq("100 Км/Ч","100 КМ/Ч");
        eq("100 КМ/Ч","100 КМ/Ч");
        eq("160 км/ч (публикация 2026 г.)","~160 КМ/Ч");
        eq("200 км/ч (по публикациям)","~200 КМ/Ч");
        eq("140–160 км/ч (зависит от конфигурации)","~140–160 КМ/Ч");
        eq("1,95 м","1,95 М");
        eq("2.5 кг","2,5 КГ");
        eq("65 минут","65 МИН");
        eq("1,5 часа","1,5 Ч");
        eq("25 км","25 КМ");
        eq("до 115 км/ч (32 м/с)","до 115 КМ/Ч");
        eq("2,0 - 2,5 часа","2,0–2,5 Ч");
        eq("примерно 160 км/ч","~160 КМ/Ч");
        eq("Н/Д","");
        eq("—","");
        eq("Электрический","Электрический");
        eq("ДВС • электрический","ДВС | электрический");
        System.out.println("Formatting checks passed: 18");
    }
}
