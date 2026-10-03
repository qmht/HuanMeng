package com.huanmeng.shiyan;

public class VideoItem {
    private String name;
    private String path;
    private long size;
    private long duration;
    private long dateAdded;

    public VideoItem(String name, String path, long size, long duration, long dateAdded) {
        this.name = name; this.path = path; this.size = size; this.duration = duration; this.dateAdded = dateAdded;
    }
    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }
    public long getDuration() { return duration; }
    public long getDateAdded() { return dateAdded; }
    public String getFormattedSize() {
        if(size < 1024) return size + "B";
        else if(size < 1024*1024) return String.format("%.2fKB", size/1024.0);
        else return String.format("%.2fMB", size/(1024.0*1024.0));
    }
    public String getFormattedDuration() {
        long s = duration / 1000;
        long m = s / 60;
        s = s % 60;
        return String.format("%02d:%02d", m, s);
    }
}