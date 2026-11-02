package dev.infend.bplainfo;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical display formatting, leaving archived source facts unchanged. */
public final class SpecFormatter {
    private SpecFormatter(){}
    private static final Pattern APPROX=Pattern.compile(
        "(?iu)\\b(?:примерно|приблизительно|около|ориентировочно|оценочно|предположительно)\\b|"+
        "\\([^)]*(?:по публикациям|публикаци[яи]|зависит от конфигурации|ориентировочно|примерно)[^)]*\\)");
    private static final Pattern APPROX_NOTE=Pattern.compile(
        "(?iu)\\s*\\([^)]*(?:по публикациям|публикаци[яи]|зависит от конфигурации|ориентировочно|примерно)[^)]*\\)");
    private static String rep(String s,String re,String replacement){
        return s.replaceAll("(?iu)"+re,replacement);
    }
    public static String display(String input){
        if(input==null)return "";
        String text=input.trim().replace('•','|').replace('−','–');
        if(text.isEmpty()||text.matches("(?iu)^(?:н/?д|нет данных|неизвестно|—|–|-|нет)$"))return "";
        boolean numeric=text.matches("(?s).*\\d.*");
        if(!numeric)return text;
        boolean approximate=APPROX.matcher(text).find()||text.startsWith("~")||text.startsWith("≈");
        text=APPROX_NOTE.matcher(text).replaceAll("");
        text=text.replaceAll("(?iu)\\s*\\(32\\s*м\\s*/\\s*с\\)","");
        text=text.replace('≈','~');
        text=rep(text,"\\b(?:примерно|приблизительно|около|ориентировочно|оценочно|предположительно)\\b","");
        text=text.replaceAll("(?<=\\d)\\.(?=\\d)",",");
        text=rep(text,"\\b(?:км\\s*/\\s*(?:ч|час|часа|часов)|км\\s*в\\s*час|km/h|kmph)\\b","КМ/Ч");
        text=rep(text,"\\b(?:м\\s*/\\s*с|m/s)\\b","М/С");
        text=rep(text,"\\b(?:километров|километра|километр|км)\\b","КМ");
        text=rep(text,"\\b(?:килограммов|килограмма|килограмм|кг)\\b","КГ");
        text=rep(text,"\\b(?:граммов|грамма|грамм|г)\\b","Г");
        text=rep(text,"\\b(?:метров|метра|метр|м)\\b","М");
        text=rep(text,"\\b(?:часов|часа|час|ч)\\b","Ч");
        text=rep(text,"\\b(?:минуты|минута|минут|мин|мин\\.)\\b","МИН");
        text=rep(text,"\\b(?:секунд|секунды|секунда|сек|с)\\b","С");
        text=text.replaceAll("\\s*[-–]\\s*(?=\\d)","–");
        text=text.replaceAll("\\s+"," ").trim();
        if(approximate){
            text=text.replaceFirst("^[~≈]\\s*","");
            text="~"+text;
        }
        return text;
    }
}
