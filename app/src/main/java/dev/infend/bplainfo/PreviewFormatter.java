package dev.infend.bplainfo;

import java.util.List;
import java.util.Locale;

/**
 * Unit-aware card previews. For bare numbers, infer only from the unit
 * explicitly present in the matching source field label. Do not guess.
 * All formatting is presentation-only: source facts remain unchanged.
 */
public final class PreviewFormatter {
    private PreviewFormatter(){}

    public static String endurance(String input,List<String[]> specs){
        return render(input,specs,0);
    }
    public static String speed(String input,List<String[]> specs){
        return render(input,specs,1);
    }
    public static String range(String input,List<String[]> specs){
        return render(input,specs,2);
    }

    private static String render(String input,List<String[]> specs,int metric){
        if(input==null)return "—";
        String text=input.trim();
        // Drop explanatory equivalents like "(120 минут)" after the main fact.
        int note=text.indexOf('(');
        if(note>0)text=text.substring(0,note).trim();
        if(text.isEmpty()||text.equals("—")||text.equals("-")||
           text.equalsIgnoreCase("неизвестно")||
           text.equalsIgnoreCase("нет данных"))return "—";

        // Numeric ranges from PDF: 24…720, 24...720, 60-70.
        text=text.replace("...","–").replace('…','–').replace('−','–')
                 .replace(" - ","–");
        text=decimalCommas(text);
        String lower=text.toLowerCase(Locale.forLanguageTag("ru"));
        String explicit=unit(lower,metric);
        String stripped=stripUnits(text);
        String unit=explicit;
        if(unit.isEmpty())unit=unitFromOriginalLabel(input,specs,metric);
        if(unit.isEmpty())return "—";

        String number=stripped.trim();
        if(number.isEmpty())return "—";
        if(number.startsWith("до "))number="≤"+number.substring(3).trim();
        if(number.startsWith("от "))number="≥"+number.substring(3).trim();
        if(input.trim().startsWith("~")||input.trim().startsWith("≈"))number="~"+number;
        // Upper/lower bounds are kept as bounds, not falsely advertised as estimates.
        if(!isSafeNumeric(number))return "—";
        return number.replace("– ","–").replace(" –","–")+" "+unit;
    }

    private static String decimalCommas(String source){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i);
            if(c=='.'&&i>0&&i+1<source.length()&&
                Character.isDigit(source.charAt(i-1))&&
                Character.isDigit(source.charAt(i+1)))out.append(',');
            else out.append(c);
        }
        return out.toString();
    }

    private static String unit(String lower,int metric){
        if(metric==0){
            if(lower.contains("мин")||lower.contains("minute"))return "мин";
            if(lower.contains("час")||lower.endsWith("ч")||
               lower.contains("hour"))return "ч";
        }else if(metric==1){
            if(lower.contains("км/ч")||lower.contains("км / ч")||lower.contains("km/h")||
               lower.contains("км в час"))return "км/ч";
            if(lower.contains("м/с")||lower.contains("m/s"))return "м/с";
        }else if(metric==2){
            if(lower.contains("км")||lower.contains("километр"))return "км";
            if(lower.contains("метр")||lower.endsWith("м"))return "м";
        }
        return "";
    }

    private static String unitFromOriginalLabel(String raw,List<String[]> rows,int metric){
        if(rows==null)return "";
        for(String[] pair:rows){
            if(pair==null||pair.length<2)continue;
            if(!raw.trim().equals(pair[1].trim()))continue;
            String label=pair[0].toLowerCase(Locale.forLanguageTag("ru"));
            if(metric==0&&!(label.contains("время пол")||
                 label.contains("продолжительност")||label.contains("автоном")))continue;
            if(metric==1&&!label.contains("скорост"))continue;
            if(metric==2&&!(label.contains("радиус")||label.contains("дальност")))continue;
            String u=unit(label,metric);
            if(!u.isEmpty())return u;
        }
        return "";
    }

    private static String stripUnits(String s){
        String t=s.trim().toLowerCase(Locale.forLanguageTag("ru"));
        // Longest tokens first, so "км/ч" never becomes "км".
        String[] units={"километров в час","км в час","км/ч","км / ч",
            "km/h","m/s","м/с","километров","километра","километр",
            "часов","часа","минут","минуты","минута","hours","hour",
            "minutes","minute","мин","км","час","метров","метра","метр","ч","м"};
        for(String v:units)t=t.replace(v," ");
        t=t.replace("~","").replace("≈","").trim();
        if(t.endsWith("."))t=t.substring(0,t.length()-1).trim();
        while(t.contains("  "))t=t.replace("  "," ");
        return t;
    }

    private static boolean isSafeNumeric(String s){
        String rest=s;
        if(rest.startsWith("до "))rest=rest.substring(3);
        if(rest.startsWith("от "))rest=rest.substring(3);
        boolean anyDigit=false;
        for(int i=0;i<rest.length();i++){
            char c=rest.charAt(i);
            if(Character.isDigit(c))anyDigit=true;
            else if(c!=','&&c!='.'&&c!='–'&&c!='-'&&c!=' '&&c!='+'&&c!='/'&&c!='≤'&&c!='≥'&&c!='~')
                return false;
        }
        return anyDigit;
    }
}
