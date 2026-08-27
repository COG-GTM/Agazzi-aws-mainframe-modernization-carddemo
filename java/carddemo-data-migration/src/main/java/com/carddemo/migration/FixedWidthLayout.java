package com.carddemo.migration;
import java.util.*;
/** Declarative fixed-width field layout; missing trailing source columns are blank. */
public record FixedWidthLayout(List<FieldSpec> fields) {
  public record FieldSpec(String name,int offset,int length,Kind kind) {}
  public enum Kind { TEXT, INTEGER, LONG, MONEY }
  public String read(String line,FieldSpec f){int start=Math.min(f.offset(),line.length()),end=Math.min(start+f.length(),line.length());return line.substring(start,end);}
  public String read(String line,String name){return read(line,field(name));}
  public FieldSpec field(String name){return fields.stream().filter(f->f.name().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown field: "+name));}
  public static FixedWidthLayout of(Object... values){
    List<FieldSpec> out=new ArrayList<>(); for(int i=0;i<values.length;i+=4)out.add(new FieldSpec((String)values[i],(Integer)values[i+1],(Integer)values[i+2],(Kind)values[i+3])); return new FixedWidthLayout(out);
  }
}
