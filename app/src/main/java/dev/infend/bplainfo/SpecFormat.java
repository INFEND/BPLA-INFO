package dev.infend.bplainfo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Presentation-only formatting. Original specifications and provenance remain unchanged. */
public final class SpecFormat {
    private static final Pattern UNITS=Pattern.compile(
        "(?iu)(?<![\\p{L}])(?:км\\s*/\\s*ч|м\\s*/\\s*с|километров\\s*/\\s*час|километра|километров|километры|килограмм(?:а|ов)?|грамм(?:а|ов)?|метр(?:а|ов)?|минут(?:а|ы)?|час(?:а|ов)?|км|кг|мин|г|м|ч)(?![\\p{L}])");
    private static final Pattern UNCERTAIN=Pattern.compile(
        "(?iu)\\s*\\((?:по публикациям|публикация[^)]*|оценка[^)]*|ориентировочно[^)]*)\\)");
    private static final Pattern CONFIG=Pattern.compile(
        "(?iu)\\s*\\((?:зависит от конфигурации|без дополнительной батареи)\\)");
    private SpecFormat(){}
    public static String value(String raw){
        if(raw==null)return "";
        String v=raw.replace('\u00a0',' ').trim();
        if(v.isEmpty()||v.equalsIgnoreCase("н/д")||v.equals("—"))return "";
        boolean approximate=UNCERTAIN.matcher(v).find();
        v=UNCERTAIN.matcher(v).replaceAll("");
        v=CONFIG.matcher(v).replaceAll("");
        v=v.replace('≈','~').replace('−','-');
        v=v.replaceAll("(?<=\\d)\\.(?=\\d)",",");
        v=v.replaceAll("(?<=\\d)\\s*[-–—]\\s*(?=\\d)","–");
        Matcher matcher=UNITS.matcher(v);
        StringBuffer result=new StringBuffer();
        while(matcher.find()){
            String unit=matcher.group().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
            String target;
            if(unit.equals("КМ/Ч")||unit.startsWith("КИЛОМЕТРОВ/ЧАС"))target="КМ/Ч";
            else if(unit.equals("М/С"))target="М/С";
            else if(unit.startsWith("КИЛОМЕТР")||unit.equals("КМ"))target="КМ";
            else if(unit.startsWith("КИЛОГРАММ")||unit.equals("КГ"))target="КГ";
            else if(unit.startsWith("ГРАММ")||unit.equals("Г"))target="Г";
            else if(unit.startsWith("МЕТР")||unit.equals("М"))target="М";
            else if(unit.startsWith("МИНУТ")||unit.equals("МИН"))target="МИН";
            else if(unit.startsWith("ЧАС")||unit.equals("Ч"))target="Ч";
            else target=unit;
            matcher.appendReplacement(result,Matcher.quoteReplacement(target));
        }
        matcher.appendTail(result);
        v=result.toString().replaceAll("\\s{2,}"," ").trim();
        if(approximate&&!v.startsWith("~"))v="~"+v;
        return v;
    }
}