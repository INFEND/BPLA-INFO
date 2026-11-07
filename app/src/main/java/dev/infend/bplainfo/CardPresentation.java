package dev.infend.bplainfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Canonical presentation without modifying source records, local edits or photos. */
public final class CardPresentation {
    private CardPresentation(){}
    private static final Pattern NICK=Pattern.compile("^([\\p{L}\\d.-]{2,24})\\s+([А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+){0,2})$");
    private static final List<String> ORDER=Arrays.asList(
        "РАЗМАХ КРЫЛЬЕВ","ДЛИНА ФЮЗЕЛЯЖА","РАДИУС ДЕЙСТВИЯ",
        "ДАЛЬНОСТЬ ПОЛЁТА","РАБОЧАЯ ВЫСОТА","ПРАКТИЧЕСКИЙ ПОТОЛОК",
        "МАКСИМАЛЬНАЯ СКОРОСТЬ","ВЗЛЁТНАЯ МАССА","АВТОНОМНОСТЬ",
        "ТИП ЗАПУСКА","ТИП ДВИГАТЕЛЯ","ПРОИЗВОДИТЕЛЬ",
        "ПОЛЕЗНАЯ НАГРУЗКА");
    private static final int[] BASE={3,4,6,7,8,5,10,11,12,13,2,9};
    private static final String[] LABELS={
        "НАЗВАНИЕ","ТИП","ПРОИЗВОДИТЕЛЬ","РАЗМАХ КРЫЛЬЕВ",
        "ДЛИНА ФЮЗЕЛЯЖА","МАКСИМАЛЬНАЯ СКОРОСТЬ","ДАЛЬНОСТЬ ПОЛЁТА",
        "РАБОЧАЯ ВЫСОТА","ПРАКТИЧЕСКИЙ ПОТОЛОК","ПОЛЕЗНАЯ НАГРУЗКА",
        "ВЗЛЁТНАЯ МАССА","АВТОНОМНОСТЬ","ТИП ЗАПУСКА","ТИП ДВИГАТЕЛЯ"};
    public static boolean isAir(String type,String sourceGroup) {
        String s=((type==null?"":type)+" "+(sourceGroup==null?"":sourceGroup)).toUpperCase(Locale.ROOT);
        return !(s.contains("МОРСК")||s.contains("НАЗЕМ")||s.contains("НАДВОД")||
                 s.contains("ПОДВОД")||s.contains("СУХОПУТН")||s.contains("UGV")||s.contains("USV"));
    }
    public static String name(String source) {
        if(source==null)return "";
        String s=source.trim().replaceAll("\\s+"," ");
        if(s.equalsIgnoreCase("1-20пс ночная"))return "1-20ПС «Ночная»";
        if(s.contains("«")||s.contains("»"))return s;
        Matcher m=NICK.matcher(s);
        return m.matches()&&m.group(1).matches(".*\\d.*")
            ?m.group(1)+" «"+m.group(2)+"»":s;
    }
    public static String manufacturer(String source) {
        if(source==null||source.trim().isEmpty())return "";
        String s=source.trim();
        // A country is an origin, not the name of a manufacturer.
        if(s.equalsIgnoreCase("Украина")||s.equalsIgnoreCase("Ukraine")||
           s.equalsIgnoreCase("Россия")||s.equalsIgnoreCase("Russia"))return "";
        return s.startsWith("«")&&s.endsWith("»")?s:"«"+s.replace("«","").replace("»","")+"»";
    }
    public static String engine(String source) {
        if(source==null)return "";
        String s=source.trim().replaceAll("\\s+"," ");
        if(s.equalsIgnoreCase("ДВС")||s.equalsIgnoreCase("ГТД"))return s.toUpperCase(Locale.ROOT);
        // Source shorthand can be "электро", "эл.", "electro", or an engine name.
        // Present any electric propulsion consistently as "Электрический".
        String lower=s.toLowerCase(Locale.forLanguageTag("ru"));
        if(lower.equals("эл")||lower.equals("эл.")||lower.startsWith("электр")||
           lower.startsWith("электро")||lower.startsWith("electric")||
           lower.equals("electro"))return "Электрический";
        boolean hasLetter=false;
        for(int i=0;i<s.length();i++){
            if(Character.isLetter(s.charAt(i))){hasLetter=true;break;}
        }
        // ICU regex on some Android builds rejects (?U); use portable code points.
        if(s.length()>1&&s.equals(s.toUpperCase(Locale.ROOT))&&hasLetter) {
            return lower.substring(0,1).toUpperCase(Locale.forLanguageTag("ru"))+lower.substring(1);
        }
        return s;
    }
    public static String label(String source) {
        if(source==null)return "";
        String s=source.trim().toUpperCase(Locale.ROOT).replace('Ё','Е');
        // Strip source-page prefixes, not the actual parameter name.
        s=s.replaceFirst("^(?:АЛЬБОМ\\s*\\([^)]*\\)|БПЛА\\s+ВСУ\\s*\\([^)]*\\)|БЮЛЛЕТЕНЬ\\s*\\([^)]*\\))\\s*[·|:]\\s*","");
        if(s.startsWith("ИСТОЧНИК ДОПОЛНЕННЫХ")||s.startsWith("ССЫЛКА НА ИСТОЧНИК")||
           s.equals("ПРИМЕЧАНИЯ")||s.equals("ИСТОЧНИК"))return "";
        if(s.startsWith("СТРАНА (ПО АЛЬБОМУ)"))return "СТРАНА";
        if(s.equals("ДЛИНА"))return "ДЛИНА ФЮЗЕЛЯЖА";
        if(s.equals("РАЗМАХ КРЫЛА"))return "РАЗМАХ КРЫЛЬЕВ";
        if(s.equals("ДВИГАТЕЛЬ"))return "ТИП ДВИГАТЕЛЯ";
        if(s.equals("ТИП СТАРТА"))return "ТИП ЗАПУСКА";
        if(s.equals("ВЗЛЕТНАЯ МАССА")||s.equals("ВЗЛЕТНЫЙ ВЕС"))return "ВЗЛЁТНАЯ МАССА";
        if(s.equals("ВРЕМЯ ПОЛЕТА")||s.equals("ПРОДОЛЖИТЕЛЬНОСТЬ ПОЛЕТА")||
           s.equals("ВРЕМЯ РАБОТЫ"))return "АВТОНОМНОСТЬ";
        if(s.equals("ДАЛЬНОСТЬ ПОЛЕТА"))return "ДАЛЬНОСТЬ ПОЛЁТА";
        if(s.equals("МАКС. ГОРИЗОНТ. СКОРОСТЬ"))return "МАКСИМАЛЬНАЯ СКОРОСТЬ";
        if(s.equals("ПОТОЛОК"))return "ПРАКТИЧЕСКИЙ ПОТОЛОК";
        return source.trim().toUpperCase(Locale.ROOT);
    }
    public static String value(String label,String source) {
        if(label.equals("ПРОИЗВОДИТЕЛЬ"))return manufacturer(source);
        if(label.equals("ТИП ДВИГАТЕЛЯ"))return engine(source);
        return SpecFormatter.display(source);
    }
    private static void append(Map<String,String> lines,String name,String raw) {
        String normalized=label(name);
        if(normalized.isEmpty())return;
        String data=value(normalized,raw);
        String lower=name.toLowerCase(Locale.forLanguageTag("ru"));
        if(lower.contains("скорост")&&(lower.contains("км/ч")||lower.contains("м/с")))
            data=PreviewFormatter.speed(raw,java.util.Collections.singletonList(new String[]{name,raw}));
        else if((lower.contains("полет")||lower.contains("полёт")||
                 lower.contains("автоном")||lower.contains("продолжительност"))&&
                 (lower.contains("мин")||lower.contains(" ч")||lower.contains("час")))
            data=PreviewFormatter.endurance(raw,java.util.Collections.singletonList(new String[]{name,raw}));
        else if((lower.contains("радиус")||lower.contains("дальност"))&&
                 (lower.contains("км")||lower.contains(" м")))
            data=PreviewFormatter.range(raw,java.util.Collections.singletonList(new String[]{name,raw}));
        if("—".equals(data))data="";
        if(!normalized.isEmpty()&&!data.isEmpty()&&!lines.containsKey(normalized))
            lines.put(normalized,data);
    }
    public static List<String[]> rows(String[] values,List<String[]> extra) {
        Map<String,String> all=new LinkedHashMap<>();
        if(extra!=null)for(String[] x:extra)if(x!=null&&x.length>=2)append(all,x[0],x[1]);
        if(values!=null&&values.length>=14)
            for(int n:BASE)append(all,LABELS[n],values[n]);
        List<String[]> result=new ArrayList<>();
        for(String name:ORDER){String value=all.remove(name);if(value!=null)result.add(new String[]{name,value});}
        for(Map.Entry<String,String> item:all.entrySet())
            result.add(new String[]{item.getKey(),item.getValue()});
        return result;
    }
}
