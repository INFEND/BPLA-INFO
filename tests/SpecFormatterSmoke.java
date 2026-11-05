package dev.infend.bplainfo;

/** Independent JVM smoke tests for Android's presentation-only unit formatter. */
public final class SpecFormatterSmoke {
    private static void eq(String source,String expected){
        String actual=SpecFormatter.display(source);
        if(!expected.equals(actual))throw new AssertionError(
            "Expected '"+expected+"' but got '"+actual+"' from '"+source+"'");
    }
    public static void main(String[] args){
        eq("100 км/ч","100 км/ч");
        eq("100 Км/Ч","100 км/ч");
        eq("100 КМ/Ч","100 км/ч");
        eq("160 км/ч (публикация 2026 г.)","~160 км/ч");
        eq("200 км/ч (по публикациям)","~200 км/ч");
        eq("140–160 км/ч (зависит от конфигурации)","~140–160 км/ч");
        eq("1,95 м","1,95 м");
        eq("2.5 кг","2,5 кг");
        eq("65 минут","65 мин");
        eq("1,5 часа","1,5 ч");
        eq("25 км","25 км");
        eq("до 115 км/ч (32 м/с)","до 115 км/ч");
        eq("2,0 - 2,5 часа","2,0–2,5 ч");
        eq("примерно 160 км/ч","~160 км/ч");
        eq("Н/Д","");
        eq("—","");
        eq("Электрический","Электрический");
        eq("ДВС • электрический","ДВС | электрический");
        System.out.println("Formatting checks passed: 18");
    }
}
