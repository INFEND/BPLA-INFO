package dev.infend.bplainfo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Physical airframe, mission and country are independent catalogue facets. */
public final class CatalogFacets {
    private CatalogFacets(){}

    public static String airframe(String type,String group,String name){
        String t=up(type),g=up(group),n=up(name);
        // Winged VTOL aircraft stay fixed-wing regardless of lift rotors.
        if(t.contains("САМОЛ")||t.contains("КРЫЛ")||g.contains("СХЕМА КРЫЛО")||
           g.contains("ХВОСТОВАЯ БАЛКА")||g.contains("ВИНТ СПЕРЕДИ")||
           g.contains("ВИНТ СЗАДИ"))return "Самолётный";
        if(t.contains("МУЛЬТИ")||t.contains("КОПТЕР")||t.contains("КВАДР")||
           t.contains("ГЕКСА")||t.contains("ОКТО")||g.contains("КОПТЕР")||
           g.contains("КВАДР")||g.contains("FPV"))return "Коптерный";
        // Named, publicly documented winged loitering aircraft.
        if(t.contains("БАРРАЖ")||g.contains("БАРРАЖ")){
            if(n.equals("LOKI")||n.equals("RAM")||n.equals("RAM-2")||
               n.equals("RAM-2S")||n.equals("WARMATE (2.0)")||
               n.equals("WARMATE (3.0)"))return "Самолётный";
        }
        return "";
    }
    public static String role(String type,String group,List<String[]> specs){
        String s=up(type)+" "+up(group);
        if(s.contains("БАРРАЖ"))return "Барражирующий";
        if(specs!=null)for(String[] row:specs){
            if(row==null||row.length<2)continue;
            String label=up(row[0]),value=up(row[1]);
            if(label.equals("НАЗНАЧЕНИЕ")||label.equals("РОЛЬ")||label.equals("ТИП ПРИМЕНЕНИЯ")){
                if(value.contains("БАРРАЖ"))return "Барражирующий";
                if(value.contains("РАЗВЕД"))return "Разведывательный";
                if(value.contains("УДАР"))return "Ударный";
            }
        }
        return "";
    }
    public static String country(List<String[]> specs){
        if(specs==null)return "";
        for(String[] row:specs){
            if(row==null||row.length<2)continue;
            String key=up(row[0]);
            if(key.equals("СТРАНА")||key.startsWith("СТРАНА (ПО АЛЬБОМУ)")||
               key.equals("СТРАНА ПРОИЗВОДСТВА")||
               key.equals("СТРАНА РАЗРАБОТЧИКА")||
               key.equals("СТРАНА ПРОИСХОЖДЕНИЯ")){
                String c=normalizeCountry(row[1]);
                if(!c.isEmpty())return c;
            }
        }
        return "";
    }
    public static String normalizeCountry(String value){
        String s=up(value).replace('Ё','Е').trim();
        if(s.contains("РОССИ")||s.equals("RUSSIA"))return "Россия";
        if(s.contains("УКРАИН")||s.equals("UKRAINE"))return "Украина";
        if(s.contains("ТУРЦ")||s.equals("TURKEY")||s.equals("TÜRKIYE"))return "Турция";
        if(s.equals("США")||s.contains("СОЕДИНЕННЫЕ ШТАТЫ")||
           s.equals("USA")||s.equals("US"))return "США";
        if(s.contains("ГЕРМАН")||s.equals("GERMANY"))return "Германия";
        if(s.contains("ПОЛЬШ")||s.equals("POLAND"))return "Польша";
        if(s.contains("БРИТАН")||s.equals("UNITED KINGDOM")||s.equals("UK"))return "Великобритания";
        if(s.contains("ИЗРАИЛ")||s.equals("ISRAEL"))return "Израиль";
        if(s.contains("КИТА")||s.equals("CHINA"))return "Китай";
        if(s.contains("ФРАНЦ")||s.equals("FRANCE"))return "Франция";
        if(s.contains("НИДЕРЛАНД")||s.equals("NETHERLANDS"))return "Нидерланды";
        if(s.contains("ЧЕХ")||s.equals("CZECHIA"))return "Чехия";
        if(s.contains("ЛАТВ")||s.equals("LATVIA"))return "Латвия";
        if(s.contains("ЭСТОН")||s.equals("ESTONIA"))return "Эстония";
        if(s.contains("ПОРТУГАЛ")||s.equals("PORTUGAL"))return "Португалия";
        return "";
    }
    public static String flag(String country){
        switch(country){
            case "Россия":return "🇷🇺";
            case "Украина":return "🇺🇦";
            case "Турция":return "🇹🇷";
            case "США":return "🇺🇸";
            case "Германия":return "🇩🇪";
            case "Польша":return "🇵🇱";
            case "Великобритания":return "🇬🇧";
            case "Израиль":return "🇮🇱";
            case "Китай":return "🇨🇳";
            case "Франция":return "🇫🇷";
            case "Нидерланды":return "🇳🇱";
            case "Чехия":return "🇨🇿";
            case "Латвия":return "🇱🇻";
            case "Эстония":return "🇪🇪";
            case "Португалия":return "🇵🇹";
            default:return "🌐";
        }
    }
    public static String canonicalName(String name){
        if(name==null)return "";
        return Normalizer.normalize(name,Normalizer.Form.NFKC).trim()
            .toUpperCase(Locale.ROOT).replace('Ё','Е')
            .replaceAll("[\\s\\u00a0]+"," ")
            .replace('«','"').replace('»','"');
    }
    public static boolean identicalModel(String first,String second){
        // No fuzzy matching: suffixes such as BASIC, EFT, LR and model numbers remain distinct.
        return !canonicalName(first).isEmpty()&&canonicalName(first).equals(canonicalName(second));
    }
    private static String up(String s){return s==null?"":s.toUpperCase(Locale.ROOT).trim();}
}
