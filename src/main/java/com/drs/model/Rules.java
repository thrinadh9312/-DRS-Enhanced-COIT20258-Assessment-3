package com.drs.model;
import com.google.gson.*;
import java.time.LocalDateTime;
import java.util.*;
public final class Rules {
    public static final List<String> TYPES=List.of("Hurricane","Fire","Earthquake","Flood","Storm","Other");
    public static final List<String> SEVERITIES=List.of("Low","Medium","High","Critical");
    public static final List<String> DEPARTMENTS=List.of("Fire and Emergency","Hospital","Electricity","Transportation","Waste Management","Water Supply","Schools","Law Enforcement");
    private Rules(){}
    public static String text(JsonObject j,String key,int max){String s=j.has(key)&&!j.get(key).isJsonNull()?j.get(key).getAsString().trim():"";if(s.isEmpty()||s.length()>max)throw new IllegalArgumentException(key+" is required (maximum "+max+" characters)");return s;}
    public static int number(JsonObject j,String key,int min,int max){try{int n=new java.math.BigDecimal(j.get(key).getAsString()).intValueExact();if(n<min||n>max)throw new Exception();return n;}catch(Exception e){throw new IllegalArgumentException(key+" must be a whole number from "+min+" to "+max);}}
    public static String choice(JsonObject j,String key,List<String> choices){String s=text(j,key,100);if(!choices.contains(s))throw new IllegalArgumentException("Invalid "+key);return s;}
    public static String priority(String severity){return severity.toUpperCase(Locale.ROOT);}
    public static String date(JsonObject j){String s=text(j,"occurred_at",30);LocalDateTime d;try{d=LocalDateTime.parse(s);}catch(Exception e){throw new IllegalArgumentException("Use date/time YYYY-MM-DDTHH:MM");}if(d.isAfter(LocalDateTime.now().plusMinutes(5)))throw new IllegalArgumentException("Incident date cannot be in the future");return s;}
}
