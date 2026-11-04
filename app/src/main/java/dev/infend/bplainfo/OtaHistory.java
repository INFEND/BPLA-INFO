package dev.infend.bplainfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cumulative OTA notes from QR LINK. Does not hide skipped releases. */
public final class OtaHistory {
    private OtaHistory() {}

    public static final class Entry {
        public final long code;
        public final String name, notes;
        public Entry(long code, String name, String notes) {
            this.code=code; this.name=name; this.notes=notes;
        }
    }

    /** Fall back to latest notes if the history is missing or incomplete. */
    public static String merge(List<Entry> history,long installed,long latest,String fallback) {
        String latestNotes=ReleaseNotes.visible(fallback);
        if(history==null||history.isEmpty()||installed>=latest)return latestNotes;
        Set<String> names=new HashSet<>();
        StringBuilder result=new StringBuilder();
        long lastSelected=installed;
        for(Entry entry:history) {
            if(entry==null)return latestNotes;
            if(entry.code<=installed)continue;
            if(entry.code<=lastSelected||entry.code>latest||
                entry.name==null||entry.name.isEmpty()||!names.add(entry.name)||
                entry.notes==null||entry.notes.length()>100_000)return latestNotes;
            if(entry.code>lastSelected+1)return latestNotes;
            String clean=ReleaseNotes.visible(entry.notes);
            if(clean.isEmpty())return latestNotes;
            lastSelected=entry.code;
            if(result.length()>0)result.append('\n');
            result.append("[=] Версия ").append(entry.name).append('\n').append(clean);
        }
        if(lastSelected!=latest)return latestNotes;
        return result.toString();
    }
}
